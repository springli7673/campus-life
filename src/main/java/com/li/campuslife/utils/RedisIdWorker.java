package com.li.campuslife.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component//表示这是一个Spring管理的组件，可以被自动扫描和实例化
public class RedisIdWorker {
    //开始时间戳
    private static final long BEGIN_TIMESTAMP = 1672531200L;
    //序列号位数
    private static final long COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.of("+8"));
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接返回
        return timestamp << COUNT_BITS | count;
    }

}
