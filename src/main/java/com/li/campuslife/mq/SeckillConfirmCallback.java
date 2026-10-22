package com.li.campuslife.mq;


import com.li.campuslife.service.OrderRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产端确认：broker 明确 nack 时，消息不会进入队列，必须回滚预扣。
 */
@Component
public class SeckillConfirmCallback {

    private static final Logger log = LoggerFactory.getLogger(SeckillConfirmCallback.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderRecoveryService recoveryService;

    public SeckillConfirmCallback(RabbitTemplate rabbitTemplate, OrderRecoveryService recoveryService) {
        this.rabbitTemplate = rabbitTemplate;
        this.recoveryService = recoveryService;
    }

    @PostConstruct
    public void register() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                return;
            }
            if (correlationData instanceof SeckillCorrelationData data) {
                log.error("[confirm] broker 拒绝消息 orderId={} cause={}", data.getId(), cause);
                recoveryService.rollbackAndRecord(Long.parseLong(data.getId()),
                        data.getUserId(), data.getVoucherId(), "confirm nack: " + cause, null);
            } else {
                log.warn("[confirm] nack 但没有订单信息 correlationData={} cause={}", correlationData, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.warn(
                "[returns] 消息不可路由 exchange={} key={} code={} text={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText()));
    }
}