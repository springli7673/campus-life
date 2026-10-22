package com.li.campuslife.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderProducer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public SeckillOrderProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 投递下单消息。同步异常（连不上 broker、channel 异常）由调用方捕获并回滚 Redis 预扣。
     */
    public void send(SeckillOrderMessage message) {
        send(message, new CorrelationData(String.valueOf(message.orderId())));
    }

    public void send(SeckillOrderMessage message, CorrelationData correlationData) {
        rabbitTemplate.convertAndSend(
                MqNames.SECKILL_EXCHANGE,
                MqNames.SECKILL_ORDER_ROUTING_KEY,
                message,
                correlationData);
        log.info("[seckill] 下单消息已投递 orderId={} userId={} voucherId={}",
                message.orderId(), message.userId(), message.voucherId());
    }
}