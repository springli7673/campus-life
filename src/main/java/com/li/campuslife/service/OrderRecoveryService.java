package com.li.campuslife.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.OrderException;
import com.li.campuslife.mq.SeckillOrderMessage;
import com.li.campuslife.mq.SeckillOrderProducer;
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
    private final SeckillOrderProducer seckillOrderProducer;

    public OrderRecoveryService(StringRedisTemplate stringRedisTemplate,
                                IOrderExceptionService orderExceptionService,
                                SeckillOrderProducer seckillOrderProducer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderExceptionService = orderExceptionService;
        this.seckillOrderProducer = seckillOrderProducer;
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

    /**
     * 自动重投未处理的异常订单。
     *
     * <p>发出去不直接标 REPAIRED：消费端成功与否这里看不到，先累加 retryCount，
     * 到达上限后标 IGNORED，避免无限重投。订单落库本身有唯一索引兜底，重复投递是幂等的。</p>
     *
     * @param shardIndex 当前分片下标
     * @param shardTotal 分片总数
     * @param maxRetry   最大重投次数
     * @param limit      单次最多处理条数
     * @return 本轮重投条数
     */
    public int retryOpen(int shardIndex, int shardTotal, int maxRetry, int limit) {
        int total = Math.max(1, shardTotal);
        int index = Math.max(0, Math.min(shardIndex, total - 1));
        int max = Math.max(1, maxRetry);
        List<OrderException> list = orderExceptionService.list(new QueryWrapper<OrderException>()
                .eq("status", 0)
                .lt("retry_count", max)
                .orderByAsc("create_time")
                .last("limit " + Math.max(1, Math.min(limit, 200))));

        int count = 0;
        for (OrderException exception : list) {
            Long orderId = exception.getOrderId();
            if (orderId == null) {
                continue;
            }
            if (total > 1 && Math.floorMod(orderId, (long) total) != index) {
                continue;
            }
            try {
                seckillOrderProducer.send(new SeckillOrderMessage(
                        orderId, exception.getUserId(), exception.getVoucherId()));
                int retried = (exception.getRetryCount() == null ? 0 : exception.getRetryCount()) + 1;
                exception.setRetryCount(retried);
                if (retried >= max) {
                    exception.setStatus(2);
                }
                orderExceptionService.updateById(exception);
                count++;
            } catch (Exception e) {
                log.error("[recovery] 自动重投失败 orderId={}", orderId, e);
            }
        }
        return count;
    }
}
