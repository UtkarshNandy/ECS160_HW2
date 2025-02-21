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

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import redis.clients.jedis.Jedis;



// Assumption - only support int/long/and string values
public class Session {

    private List<Object> objectList = new ArrayList<>();
    private Jedis jedisSession;

    public Session() {
        jedisSession = new Jedis("localhost", 6379);;
    }


    public void add(Object obj) {
        Class<?> clazz = obj.getClass();
        objectList.add(clazz);
    }


    public void persistAll() {
        for (Object obj : objectList) {
            Class<?> clazz = obj.getClass();
            // skip if not persistable
            if (!clazz.isAnnotationPresent(Persistable.class)) {
                continue;
            }
            String redisKey = "";
            Map<String, String> redisData = new HashMap<>();

            // loop thru declared fields
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    // persist ID
                    if (field.isAnnotationPresent(PersistableId.class)) {
                        Object id = field.get(obj);
                        if (id != null) {
                            redisKey = id.toString();
                            redisData.put(field.getName(), id.toString());
                        }
                    }
                    // persist other fields
                    if (field.isAnnotationPresent(PersistableField.class)) {
                        Object value = field.get(obj);
                        if (value != null) {
                            redisData.put(field.getName(), value.toString());
                        }
                    }
                    // persist list fields
                    if (field.isAnnotationPresent(PersistableListField.class)) {
                        Object listObj = field.get(obj);
                        if (listObj instanceof List<?>) {
                            List<?>list = (List<?>) listObj;
                            List<String>idList = new ArrayList<>();
                            for (Object element : list) {
                                Class<?> elemClass = element.getClass();
                                if(!elemClass.isAnnotationPresent(Persistable.class)){
                                    continue;
                                }
                                String childRedisKey = "";
                                Map<String, String> childData = new HashMap<>();

                                for(Field elementField: elemClass.getDeclaredFields()){
                                    elementField.setAccessible(true);

                                    if(elementField.isAnnotationPresent(PersistableId.class)){
                                        Object childId = elementField.get(element);
                                        if(childId != null){
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
            }
        }
        objectList.clear();
    }



    public Object load(Object object)  {

    }

}
