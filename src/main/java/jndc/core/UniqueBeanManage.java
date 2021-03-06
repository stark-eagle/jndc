package jndc.core;


import jndc.client.JNDCClientConfigCenter;
import jndc.core.data_store.DataStore;
import jndc.server.NDCServerConfigCenter;
import web.core.MappingRegisterCenter;
import web.core.MessageNotificationCenter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * simple object management,can use spring or other tools replace
 */
public class UniqueBeanManage {

    private static Map<Class, Object> map = new ConcurrentHashMap<>();

    public static <T> T getBean(Class<T> tClass) {
        Object o = map.get(tClass);
        if (o == null) {
            throw new RuntimeException("no matching bean "+tClass);
        }
        return (T) o;
    }


    static {
        registerBean(new NDCServerConfigCenter());
        registerBean(new JNDCClientConfigCenter());
        registerBean(new IpChecker());
        registerBean(new MappingRegisterCenter());
        registerBean(new DataStore());
        registerBean(new AsynchronousEventCenter());
        registerBean(new MessageNotificationCenter());
        registerBean(new ScheduledTaskCenter());

    }


    public static void registerBean(Object o) {
        if (null == o) {
            return;
        }
        Class<?> aClass = o.getClass();
        map.put(aClass, o);
    }
}
