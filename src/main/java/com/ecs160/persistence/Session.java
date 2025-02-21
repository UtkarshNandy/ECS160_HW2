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

    }



    public Object load(Object object)  {
        return null;
    }

}
