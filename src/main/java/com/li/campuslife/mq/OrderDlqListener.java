package com.li.campuslife.mq;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.campuslife.service.IOrderExceptionService;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;

/**
 * 死信队列消费者：把失败消息落进异常表，避免"死信只是换个地方堆积"。
 */
@Component
public class OrderDlqListener {

    private static final Logger log = LoggerFactory.getLogger(OrderDlqListener.class);

    private final IOrderExceptionService orderExceptionService;
    private final ObjectMapper objectMapper;

    public OrderDlqListener(IOrderExceptionService orderExceptionService, ObjectMapper objectMapper) {
        this.orderExceptionService = orderExceptionService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = MqNames.ORDER_DLQ)
    public void onDeadLetter(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        String reason = xDeath == null ? "dead-letter" : xDeath.toString();
        try {
            SeckillOrderMessage order = objectMapper.readValue(body, SeckillOrderMessage.class);
            orderExceptionService.record(order.orderId(), order.userId(), order.voucherId(),
                    "dead-letter: " + reason, body);
        } catch (Exception e) {
            log.error("[dlq] 无法解析死信消息 body={}", body, e);
            orderExceptionService.record(System.currentTimeMillis(), 0L, 0L, "unparseable: " + reason, body);
        }
    }
}