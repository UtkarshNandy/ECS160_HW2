package com.ecs160.persistence;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ecs160.persistence.Persistable;
import com.ecs160.persistence.PersistableField;
import com.ecs160.persistence.PersistableId;
import com.ecs160.persistence.PersistableListField;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import redis.clients.jedis.Jedis;



// Assumption - only support int/long/and string values
public class Session {
    private List<Object> pendingObjects = new ArrayList<>();
    private Jedis redisConnection;

    public Session() {
        redisConnection = new Jedis("localhost", 6379);
    }

    public void add(Object obj) {
        pendingObjects.add(obj);
    }

    private String extractIdentifier(Object obj, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object idValue = field.get(obj);
        return idValue != null ? idValue.toString() : "";
    }

    private Map<String, String> buildEntityData(Object obj, Class<?> objClass) throws IllegalAccessException {
        Map<String, String> data = new HashMap<>();

        for (Field field : objClass.getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(PersistableField.class)) {
                Object value = field.get(obj);
                if (value != null) {
                    data.put(field.getName(), value.toString());
                }
            }
        }

        return data;
    }

    private void persistListField(Object obj, Field listField, Map<String, String> parentData) throws IllegalAccessException {
        listField.setAccessible(true);
        Object listValue = listField.get(obj);

        if (!(listValue instanceof List<?>)) {
            return;
        }

        List<?> elements = (List<?>) listValue;
        List<String> elementIds = new ArrayList<>();

        for (Object element : elements) {
            if (!element.getClass().isAnnotationPresent(Persistable.class)) {
                continue;
            }

            String elementId = "";
            Map<String, String> elementData = new HashMap<>();

            for (Field elementField : element.getClass().getDeclaredFields()) {
                elementField.setAccessible(true);

                if (elementField.isAnnotationPresent(PersistableId.class)) {
                    elementId = extractIdentifier(element, elementField);
                    elementData.put(elementField.getName(), elementId);
                } else if (elementField.isAnnotationPresent(PersistableField.class)) {
                    Object fieldValue = elementField.get(element);
                    if (fieldValue != null) {
                        elementData.put(elementField.getName(), fieldValue.toString());
                    }
                }
            }

            if (!elementId.isEmpty()) {
                redisConnection.hmset(elementId, elementData);
                elementIds.add(elementId);
            }
        }

        if (!elementIds.isEmpty()) {
            parentData.put(listField.getName(), String.join(",", elementIds));
        }
    }

    public void persistAll() {
        if (pendingObjects.isEmpty()) {
            return;
        }

        for (Object obj : pendingObjects) {
            Class<?> objClass = obj.getClass();

            if (!objClass.isAnnotationPresent(Persistable.class)) {
                continue;
            }

            try {
                String objectId = "";
                Map<String, String> objectData = new HashMap<>();

                // First, process the ID field
                for (Field field : objClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(PersistableId.class)) {
                        objectId = extractIdentifier(obj, field);
                        objectData.put(field.getName(), objectId);
                        break;
                    }
                }

                if (objectId.isEmpty()) {
                    continue;
                }

                // Add regular persistable fields
                objectData.putAll(buildEntityData(obj, objClass));

                // Process list fields
                for (Field field : objClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(PersistableListField.class)) {
                        persistListField(obj, field, objectData);
                    }
                }

                redisConnection.hmset(objectId, objectData);

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        pendingObjects.clear();
    }

    private Object createPartialObject(Class<?> objClass, String id) throws Exception {
        Object partialObj = objClass.getDeclaredConstructor().newInstance();

        for (Field field : objClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(PersistableId.class)) {
                field.setAccessible(true);
                if (field.getType().equals(String.class)) {
                    field.set(partialObj, id);
                } else if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                    field.set(partialObj, Integer.parseInt(id));
                }
                break;
            }
        }

        return partialObj;
    }

    private void loadField(Object obj, Field field, String value) throws IllegalAccessException {
        field.setAccessible(true);
        if (field.getType().equals(String.class)) {
            field.set(obj, value);
        } else if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
            field.set(obj, Integer.parseInt(value));
        }
    }

    private List<Object> loadListField(String listValue, PersistableListField annotation) throws Exception {
        if (listValue == null || listValue.isEmpty()) {
            return null;
        }

        List<Object> loadedList = new ArrayList<>();
        String[] elementIds = listValue.split(",");
        Class<?> elementClass = Class.forName(annotation.className());

        for (String elementId : elementIds) {
            Object partialElement = createPartialObject(elementClass, elementId);
            Object loadedElement = load(partialElement);
            if (loadedElement != null) {
                loadedList.add(loadedElement);
            }
        }

        return loadedList;
    }

    public Object load(Object prototype) {
        Class<?> objClass = prototype.getClass();

        try {
            // Find and validate ID field
            Field idField = null;
            for (Field field : objClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(PersistableId.class)) {
                    idField = field;
                    break;
                }
            }

            if (idField == null) {
                return null;
            }

            // Get object ID and fetch data
            idField.setAccessible(true);
            Object idValue = idField.get(prototype);
            if (idValue == null) {
                return null;
            }

            String redisKey = idValue.toString();
            Map<String, String> redisData = redisConnection.hgetAll(redisKey);

            if (redisData == null || redisData.isEmpty()) {
                return null;
            }

            // Create and populate new object
            Object loadedObject = objClass.getDeclaredConstructor().newInstance();

            for (Field field : objClass.getDeclaredFields()) {
                String storedValue = redisData.get(field.getName());

                if (field.isAnnotationPresent(PersistableId.class) ||
                        field.isAnnotationPresent(PersistableField.class)) {
                    if (storedValue != null) {
                        loadField(loadedObject, field, storedValue);
                    }
                } else if (field.isAnnotationPresent(PersistableListField.class)) {
                    PersistableListField listAnnotation = field.getAnnotation(PersistableListField.class);
                    List<Object> loadedList = loadListField(storedValue, listAnnotation);
                    if (loadedList != null) {
                        field.setAccessible(true);
                        field.set(loadedObject, loadedList);
                    }
                }
            }

            return loadedObject;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
