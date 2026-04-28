package com.talon.index12306.idempotent.core;

import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 幂等上下文
 */
public final class IdempotentContext {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    public static Map<String, Object> get() {
        return CONTEXT.get();
    }

    public static Object getKey(String key) {
        Map<String, Object> map = get();
        if (CollectionUtils.isEmpty(map)) {
            return null;
        }
        return map.get(key);
    }

    public static String getString(String key) {
        Object res = getKey(key);
        if (res == null) {
            return null;
        }
        return res.toString();
    }

    public static void put(String key, Object val) {
        Map<String, Object> map = get();
        if (CollectionUtils.isEmpty(map)) {
            map = new HashMap<>();
        }
        map.put(key, val);
    }

    public static void putContext(Map<String, Object> context) {
        Map<String, Object> map = get();
        if (CollectionUtils.isEmpty(map)) {
            CONTEXT.set(context);
            return;
        }
        map.putAll(context);
    }

    public static void clean() {
        CONTEXT.remove();
    }

}
