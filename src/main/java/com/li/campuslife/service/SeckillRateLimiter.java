package com.li.campuslife.service;

import com.li.campuslife.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀入口限流：活动维度（保护 Redis/DB/MQ）+ 用户维度（防脚本刷）。
 *
 * <p>阈值放在配置里而不是注解常量，压测时可以直接调大；Redis 故障时选择放行（fail-open），
 * 因为限流是保护手段，不应该成为下单链路的单点故障。</p>
 */
@Service
public class SeckillRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SeckillRateLimiter.class);

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rate-limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Value("${campus.seckill.rate-limit.user-permits:5}")
    private long userPermits;

    @Value("${campus.seckill.rate-limit.user-window-seconds:2}")
    private long userWindowSeconds;

    @Value("${campus.seckill.rate-limit.activity-permits:5000}")
    private long activityPermits;

    @Value("${campus.seckill.rate-limit.activity-window-seconds:1}")
    private long activityWindowSeconds;

    private final StringRedisTemplate stringRedisTemplate;

    public SeckillRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 整个活动的入口 QPS 上限 */
    public boolean allowActivity(long voucherId) {
        return tryAcquire(RedisConstants.SECKILL_RATE_LIMIT_KEY + "activity:" + voucherId,
                activityPermits, activityWindowSeconds);
    }

    /** 单个用户对单张券的请求频率上限（正常用户不会连续点） */
    public boolean allowUser(long voucherId, long userId) {
        return tryAcquire(RedisConstants.SECKILL_RATE_LIMIT_KEY + "user:" + voucherId + ":" + userId,
                userPermits, userWindowSeconds);
    }

    private boolean tryAcquire(String key, long permits, long windowSeconds) {
        if (permits <= 0 || windowSeconds <= 0) {
            return true;
        }
        try {
            Long result = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key),
                    String.valueOf(permits), String.valueOf(windowSeconds));
            return result == null || result == 1L;
        } catch (DataAccessException e) {
            log.warn("[rate-limit] Redis 异常，本次放行 key={}", key, e);
            return true;
        }
    }
}
