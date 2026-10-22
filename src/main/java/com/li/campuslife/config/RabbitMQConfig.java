package com.li.campuslife.config;

import com.li.campuslife.mq.MqNames;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀下单的 RabbitMQ 拓扑：
 * campus.seckill.exchange -> campus.order.queue ->（失败）campus.order.dlx.exchange -> campus.order.dlq
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // 默认只信任 java.util / java.lang，死信消费时反序列化自己的消息会被拒绝
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.li.campuslife.mq");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MqNames.SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(MqNames.ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(MqNames.SECKILL_ORDER_QUEUE)
                .deadLetterExchange(MqNames.ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqNames.ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderDlq() {
        return QueueBuilder.durable(MqNames.ORDER_DLQ).build();
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue()).to(seckillExchange())
                .with(MqNames.SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding orderDlqBinding() {
        return BindingBuilder.bind(orderDlq()).to(orderDlxExchange())
                .with(MqNames.ORDER_DLQ_ROUTING_KEY);
    }
}