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
    }


    public void add(Object obj) {
        objectList.add(obj);
    }

    public void persistAll() {
        if (objectList.isEmpty()) {
            return;
        }

        for (Object obj : objectList) {
            Class<?> clazz = obj.getClass();

            // skip if not persistable
            if (!clazz.isAnnotationPresent(Persistable.class)) {
                continue;
            }

            String redisKey = "";
            Map<String, String> redisData = new HashMap<>();

            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    if (field.isAnnotationPresent(PersistableId.class)) {
                        Object id = field.get(obj); // get ID
                        if (id != null) {
                            redisKey = id.toString();
                            redisData.put(field.getName(), redisKey);
                        }
                    }
                    // Persist other fields
                    if (field.isAnnotationPresent(PersistableField.class)) {
                        Object value = field.get(obj);
                        if (value != null) {
                            redisData.put(field.getName(), value.toString());
                        }
                    }
                    // Persist list fields
                    if (field.isAnnotationPresent(PersistableListField.class)) {
                        Object listObj = field.get(obj);
                        if (listObj instanceof List<?>) {
                            List<?> list = (List<?>) listObj;
                            List<String> idList = new ArrayList<>();
                            for (Object element : list) {
                                Class<?> elemClass = element.getClass();
                                if (!elemClass.isAnnotationPresent(Persistable.class)) {
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
                                        }
                                    }
                                    if (elementField.isAnnotationPresent(PersistableField.class)) {
                                        Object childValue = elementField.get(element);
                                        if (childValue != null) {
                                            childData.put(elementField.getName(), childValue.toString());
                                        }
                                    }
                                }
                                if (!childRedisKey.isEmpty()) {
                                    jedisSession.hmset(childRedisKey, childData);
                                    idList.add(childRedisKey);
                                }
                            }
                            redisData.put(field.getName(), String.join(",", idList));
                        }
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            if (!redisKey.isEmpty()) {
                jedisSession.hmset(redisKey, redisData);
            } else {
            }
        }
        objectList.clear();
    }





    public Object load(Object object)  {
        Class<?> clazz = object.getClass();
        Field id = null;

        for(Field field: clazz.getDeclaredFields()){
            if(field.isAnnotationPresent(PersistableId.class)){
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
