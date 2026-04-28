package com.talon.index12306.cache;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.talon.index12306.cache.config.RedisDistributedProperties;
import com.talon.index12306.cache.core.CacheGetFilter;
import com.talon.index12306.cache.core.CacheGetIfAbsent;
import com.talon.index12306.cache.core.CacheLoader;
import com.talon.index12306.cache.util.CacheUtil;
import com.talon.index12306.cache.util.FastJson2Util;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class StringRedisTemplateProxy implements DistributedCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisDistributedProperties redisProperties;
    private final RedissonClient redissonClient;

    private static final String SAFE_GET_DISTRIBUTED_LOCK_KEY_PREFIX = "safe_get_distributed_lock_get:";


    @Override
    public <T> T get(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout) {
        return get(key, clazz, cacheLoader, timeout, redisProperties.getValueTimeUnit());
    }

    @Override
    public <T> T get(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit) {
        T res = get(key, clazz);
        if (!CacheUtil.isNullOrBlank(res)) {
            return res;
        }
        return loadAndSet(key, cacheLoader, timeout, timeUnit, false, null);
    }

    private <T> T loadAndSet(String key, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, boolean safeFlag, RBloomFilter<String> bloomFilter) {
        T res = cacheLoader.load();
        //数据库中不存在
        if (CacheUtil.isNullOrBlank(res)) {
            return null;
        }
        if (safeFlag) {
            safePut(key, res, timeout, timeUnit, null);
        } else {
            put(key, res, timeout, timeUnit);
        }
        return res;
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout) {
        return safeGet(key, clazz, cacheLoader, timeout, redisProperties.getValueTimeUnit());
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit) {
        return safeGet(key, clazz, cacheLoader, timeout, timeUnit, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, RBloomFilter<String> bloomFilter) {
        return safeGet(key, clazz, cacheLoader, timeout, bloomFilter, null, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter) {
        return safeGet(key, clazz, cacheLoader, timeout, timeUnit, bloomFilter, null, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheCheckFilter) {
        return safeGet(key, clazz, cacheLoader, timeout, redisProperties.getValueTimeUnit(), bloomFilter, cacheCheckFilter, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheCheckFilter) {
        return safeGet(key, clazz, cacheLoader, timeout, timeUnit, bloomFilter, cacheCheckFilter, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheGetFilter, CacheGetIfAbsent<String> cacheGetIfAbsent) {
        return safeGet(key, clazz, cacheLoader, timeout, redisProperties.getValueTimeUnit(), bloomFilter, cacheGetFilter, cacheGetIfAbsent);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheGetFilter, CacheGetIfAbsent<String> cacheGetIfAbsent) {
        //缓存是否存在
        //过滤器是否存在
        T res = get(key, clazz);
        if (!CacheUtil.isNullOrBlank(res)
                || (cacheGetFilter != null && cacheGetFilter.filter(key))
                || (bloomFilter != null && bloomFilter.contains(key))) {
            return res;
        }
        RLock lock = redissonClient.getLock(SAFE_GET_DISTRIBUTED_LOCK_KEY_PREFIX + key);
        lock.lock();
        try {
            //dcl
            //不存在-> cacheloader
            if (CacheUtil.isNullOrBlank(res = get(key, clazz))) {
                //数据库不存在->cacheGetIfAbsent
                if (CacheUtil.isNullOrBlank(res = loadAndSet(key, cacheLoader, timeout, timeUnit, false, bloomFilter))) {
                    if (cacheGetIfAbsent != null) {
                        cacheGetIfAbsent.execute(key);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return res;
    }

    @Override
    public void put(String key, Object value, long timeout) {
        put(key, value, timeout, redisProperties.getValueTimeUnit());
    }

    @Override
    public void put(String key, Object value, long timeout, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, value instanceof String ? (String) value : JSON.toJSONString(value), timeout, timeUnit);
    }

    @Override
    public void safePut(String key, Object value, long timeout, RBloomFilter<String> bloomFilter) {
        safePut(key, value, timeout, redisProperties.getValueTimeUnit(), bloomFilter);
    }

    @Override
    public void safePut(String key, Object value, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter) {
        put(key, value, timeout, timeUnit);
        if (bloomFilter != null) {
            bloomFilter.add(key);
        }
    }

    @Override
    public Long countExistingKeys(@NotNull String... keys) {
        return stringRedisTemplate.countExistingKeys(Lists.newArrayList(keys));
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (String.class.isAssignableFrom(clazz)) {
            return (T) json;
        } else {
            return JSON.parseObject(json, FastJson2Util.buildType(clazz));
        }
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, redisProperties.getValueTimeout());
    }

    @Override
    public Boolean putIfAllAbsent(Collection<String> keys) {
        return null;
    }

    @Override
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    @Override
    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    @Override
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public Object getInstance() {
        return stringRedisTemplate;
    }
}