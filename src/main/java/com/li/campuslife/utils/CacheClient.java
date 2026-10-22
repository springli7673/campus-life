package com.li.campuslife.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.li.campuslife.utils.RedisConstants.*;

@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 异步重建缓存线程池（逻辑过期专用）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 普通写入缓存，设置TTL
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 写入带逻辑过期的缓存
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 方案1：仅缓存穿透（无锁，不能解决缓存击穿）
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallBack, Long time, TimeUnit unit
    ) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 缓存命中
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中空字符串（防穿透）
        if (json != null) {
            return null;
        }
        // 查数据库
        R r = dbFallBack.apply(id);
        if (r == null) {
            // 数据库不存在，写入空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 写入缓存
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 方案2：互斥锁，解决缓存穿透 + 缓存击穿（强一致，会阻塞等待）
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallBack, Long time, TimeUnit unit
    ) {
        String key = keyPrefix + id;
        //1.查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 缓存是空字符串
        if (json != null) {
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            // 获取锁
            Boolean lockFlag = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (!BooleanUtil.isTrue(lockFlag)) {
                // 获取锁失败，休眠重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit);
            }
            // 获取锁成功，查询数据库
            r = dbFallBack.apply(id);
            if (r == null) {
                // 数据库无数据，写入空值防穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 写入缓存
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            stringRedisTemplate.delete(lockKey);
        }
        return r;
    }

    /**
     * 方案3：逻辑过期，解决缓存击穿，允许短暂数据不一致，不阻塞
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallBack, Long time, TimeUnit unit
    ) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 未过期，直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 已经过期，尝试获取锁异步更新
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean lockFlag = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        if (BooleanUtil.isTrue(lockFlag)) {
            // 开启子线程更新缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newData = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key, newData, time, unit);
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            });
        }
        // 主线程直接返回旧数据，不等待
        return r;
    }
}
