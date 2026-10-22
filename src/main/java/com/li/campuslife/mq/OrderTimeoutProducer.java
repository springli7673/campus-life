package com.li.campuslife.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutProducer.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${campus.seckill.order-timeout-seconds:1800}")
    private long timeoutSeconds;

    public OrderTimeoutProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(long orderId) {
        long delayMs = timeoutSeconds * 1000L;
        try {
            rabbitTemplate.convertAndSend(
                    MqNames.ORDER_DELAY_EXCHANGE,
                    MqNames.ORDER_TIMEOUT_ROUTING_KEY,
                    new OrderTimeoutMessage(orderId),
                    msg -> {
                        msg.getMessageProperties().setHeader("x-delay", delayMs);
                        return msg;
                    });
            log.info("[timeout] 已投递延迟关单消息 orderId={} delay={}s", orderId, timeoutSeconds);
        } catch (Exception e) {
            // 延迟消息失败不影响下单，扫表任务会兜底
            log.error("[timeout] 投递延迟消息失败，等待扫表兜底 orderId={}", orderId, e);
        }
    }
}