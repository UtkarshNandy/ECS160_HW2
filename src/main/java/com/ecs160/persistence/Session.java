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

    private List<Object> objectList = new ArrayList<>();
    private Jedis jedisSession;

    public Session() {
        jedisSession = new Jedis("localhost", 6379);
        System.out.println("Redis PING: " + jedisSession.ping());
    }


    public void add(Object obj) {
        Class<?> clazz = obj.getClass();
        objectList.add(clazz);
    }


    public void persistAll() {
        if (objectList.isEmpty()) {
            System.out.println("No objects to persist. Ensure that you call session.add(obj) for each object.");
            return;
        }

        System.out.println("Starting persistAll with " + objectList.size() + " object(s).");
        for (Object obj : objectList) {
            Class<?> clazz = obj.getClass();

            // Log the class name for debugging.
            System.out.println("Processing object of type: " + clazz.getName());

            // Skip if not persistable
            if (!clazz.isAnnotationPresent(Persistable.class)) {
                System.out.println("Skipping object of type " + clazz.getName() + " because it is not annotated with @Persistable.");
                continue;
            }

            String redisKey = "";
            Map<String, String> redisData = new HashMap<>();

            // Loop through declared fields.
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    // Persist ID
                    if (field.isAnnotationPresent(PersistableId.class)) {
                        Object id = field.get(obj);
                        if (id != null) {
                            redisKey = id.toString();
                            redisData.put(field.getName(), redisKey);
                            System.out.println("Found @PersistableId field '" + field.getName() + "' with value: " + redisKey);
                        } else {
                            System.out.println("Field '" + field.getName() + "' annotated with @PersistableId is null.");
                        }
                    }
                    // Persist other fields
                    if (field.isAnnotationPresent(PersistableField.class)) {
                        Object value = field.get(obj);
                        if (value != null) {
                            redisData.put(field.getName(), value.toString());
                            System.out.println("Found @PersistableField '" + field.getName() + "' with value: " + value.toString());
                        } else {
                            System.out.println("Field '" + field.getName() + "' annotated with @PersistableField is null.");
                        }
                    }
                    // Persist list fields
                    if (field.isAnnotationPresent(PersistableListField.class)) {
                        Object listObj = field.get(obj);
                        if (listObj instanceof List<?>) {
                            List<?> list = (List<?>) listObj;
                            List<String> idList = new ArrayList<>();
                            System.out.println("Processing @PersistableListField '" + field.getName() + "' with " + list.size() + " element(s).");
                            for (Object element : list) {
                                Class<?> elemClass = element.getClass();
                                if (!elemClass.isAnnotationPresent(Persistable.class)) {
                                    System.out.println("Skipping element of type " + elemClass.getName() + " (not @Persistable).");
                                    continue;
                                }
                                String childRedisKey = "";
                                Map<String, String> childData = new HashMap<>();
                                for (Field elementField : elemClass.getDeclaredFields()) {
                                    elementField.setAccessible(true);
                                    if (elementField.isAnnotationPresent(PersistableId.class)) {
                                        Object childId = elementField.get(element);
                                        if (childId != null) {
                                            childRedisKey = childId.toString();
                                            childData.put(elementField.getName(), childRedisKey);
                                            System.out.println("  Found child @PersistableId '" + elementField.getName() + "' with value: " + childRedisKey);
                                        }
                                    }
                                    if (elementField.isAnnotationPresent(PersistableField.class)) {
                                        Object childValue = elementField.get(element);
                                        if (childValue != null) {
                                            childData.put(elementField.getName(), childValue.toString());
                                            System.out.println("  Found child @PersistableField '" + elementField.getName() + "' with value: " + childValue.toString());
                                        }
                                    }
                                }
                                if (!childRedisKey.isEmpty()) {
                                    jedisSession.hmset(childRedisKey, childData);
                                    System.out.println("Persisted child object under key: " + childRedisKey + " with data: " + childData);
                                    idList.add(childRedisKey);
                                } else {
                                    System.out.println("Child element of type " + elemClass.getName() + " does not have a valid ID; skipping persistence for this element.");
                                }
                            }
                            redisData.put(field.getName(), String.join(",", idList));
                            System.out.println("Persisted list field '" + field.getName() + "' with child keys: " + String.join(",", idList));
                        } else {
                            System.out.println("Field '" + field.getName() + "' annotated with @PersistableListField is not a List or is null.");
                        }
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            if (!redisKey.isEmpty()) {
                jedisSession.hmset(redisKey, redisData);
                System.out.println("Persisted object under key: " + redisKey + " with data: " + redisData);
            } else {
                System.out.println("No valid ID found for object of type: " + clazz.getName() + ". Skipping persistence.");
            }
        }
        objectList.clear();
        System.out.println("persistAll complete. Object list cleared.");
    }





    public Object load(Object object)  {
        Class<?> clazz = object.getClass();
        Field id = null;

        for(Field field: clazz.getDeclaredFields()){
            if(field.isAnnotationPresent(Persistable.class)){
                id = field;
                break;
            }
        }
        if(id == null){
            return null;
        }
        try{
            id.setAccessible(true);
            Object idVal = id.get(object);
            if(idVal == null){
                return null;
            }
            String redisKey = idVal.toString();

            Map<String, String> redisData = jedisSession.hgetAll(redisKey);
            if(redisData == null || redisData.isEmpty()){
                return null;
            }

            Object post = clazz.getDeclaredConstructor().newInstance();

            for(Field field: clazz.getDeclaredFields()){
                field.setAccessible(true);

                if(field.isAnnotationPresent(PersistableId.class) || field.isAnnotationPresent(PersistableField.class) && redisData.containsKey(field.getName())){
                    String value = redisData.get(field.getName());
                    if(field.getType().equals(String.class)){
                        field.set(post, value);
                    } else if (field.getType().equals(int.class) || field.getType().equals(Integer.class)){
                        field.set(post, Integer.parseInt(value));
                    }
                }

                if(field.isAnnotationPresent(PersistableListField.class)){
                    String listValue = redisData.get(field.getName());
                    if(listValue != null && !listValue.isEmpty()){
                        String[] childIds = listValue.split(",");
                        List<Object> childList = new ArrayList<>();
                        PersistableListField listAnnotation = field.getAnnotation(PersistableListField.class);
                        String childClassName = listAnnotation.className();
                        Class<?> childClass = Class.forName(childClassName);
                        for(String childId : childIds){
                            Object partChild = childClass.getDeclaredConstructor().newInstance();
                            for(Field childField : childClass.getDeclaredFields()){
                                if(childField.isAnnotationPresent(PersistableId.class)){
                                    childField.setAccessible(true);
                                    if(childField.getType().equals(String.class)){
                                        childField.set(partChild, childId);
                                    } else if(childField.getType().equals(int.class) || childField.getType().equals(Integer.class)) {
                                        childField.set(partChild, Integer.parseInt(childId));
                                    }
                                    break;
                                }
                            }
                            Object fullChild = load(partChild);
                            if(fullChild != null){
                                childList.add(fullChild);
                            }
                        }
                        field.set(post, childList);
                    }
                }
            }
            return post;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
