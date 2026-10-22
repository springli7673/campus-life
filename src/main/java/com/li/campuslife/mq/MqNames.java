package com.li.campuslife.mq;

/**
 * 消息队列资源名，集中管理，便于对照 RabbitMQ 管理台。
 */
public final class MqNames {

    private MqNames() {
    }

    /** 秒杀下单交换机与队列 */
    public static final String SECKILL_EXCHANGE = "campus.seckill.exchange";
    public static final String SECKILL_ORDER_QUEUE = "campus.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    /** 死信交换机与队列 */
    public static final String ORDER_DLX_EXCHANGE = "campus.order.dlx.exchange";
    public static final String ORDER_DLQ = "campus.order.dlq";
    public static final String ORDER_DLQ_ROUTING_KEY = "order.dead";

    /** 延迟关单交换机与队列 */
    public static final String ORDER_DELAY_EXCHANGE = "campus.order.delay.exchange";
    public static final String ORDER_TIMEOUT_QUEUE = "campus.order.timeout.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";
}