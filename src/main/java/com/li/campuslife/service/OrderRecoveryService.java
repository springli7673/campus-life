package com.li.campuslife.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.OrderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 秒杀预扣的补偿与异常登记。
 */
@Service
public class OrderRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryService.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill-rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final IOrderExceptionService orderExceptionService;

    public OrderRecoveryService(StringRedisTemplate stringRedisTemplate,
                                IOrderExceptionService orderExceptionService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderExceptionService = orderExceptionService;
    }

    /**
     * 回滚 Redis 预扣并登记异常（订单未落库时使用）
     */
    public void rollbackAndRecord(long orderId, long userId, long voucherId, String reason, String payload) {
        try {
            stringRedisTemplate.execute(ROLLBACK_SCRIPT, Collections.emptyList(),
                    String.valueOf(voucherId), String.valueOf(userId));
            log.warn("[recovery] 已回滚预扣 orderId={} voucherId={} userId={}", orderId, voucherId, userId);
        } catch (Exception e) {
            log.error("[recovery] 回滚预扣失败 orderId={}", orderId, e);
        }
        orderExceptionService.record(orderId, userId, voucherId, reason, payload);
    }

    /**
     * 只回补 Redis 库存并释放占位（关单时使用）
     */
    public void releaseRedisStock(long orderId, long userId, long voucherId, String reason) {
        try {
            stringRedisTemplate.execute(ROLLBACK_SCRIPT, Collections.emptyList(),
                    String.valueOf(voucherId), String.valueOf(userId));
        } catch (Exception e) {
            log.error("[recovery] 关单回补 Redis 失败 orderId={}", orderId, e);
            orderExceptionService.record(orderId, userId, voucherId, reason, null);
        }
    }

    public List<OrderException> listOpen(int limit) {
        return orderExceptionService.list(new QueryWrapper<OrderException>()
                .eq("status", 0)
                .orderByAsc("create_time")
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }
}