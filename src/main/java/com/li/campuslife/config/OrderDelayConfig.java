package com.li.campuslife.config;

import com.li.campuslife.mq.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 订单超时关单的延迟交换机（依赖 rabbitmq_delayed_message_exchange 插件）。
 */
@Configuration
public class OrderDelayConfig {

    @Bean
    public CustomExchange orderDelayExchange() {
        return new CustomExchange(MqNames.ORDER_DELAY_EXCHANGE, "x-delayed-message", true, false,
                Map.of("x-delayed-type", "direct"));
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(MqNames.ORDER_TIMEOUT_QUEUE)
                .deadLetterExchange(MqNames.ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqNames.ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return new Binding(MqNames.ORDER_TIMEOUT_QUEUE, Binding.DestinationType.QUEUE,
                MqNames.ORDER_DELAY_EXCHANGE, MqNames.ORDER_TIMEOUT_ROUTING_KEY, null);
    }
}