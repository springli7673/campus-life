package com.li.campuslife.mq;

/**
 * 消息队列资源名，集中管理，便于对照 RabbitMQ 管理台。
 */
public final class MqNames {

    private MqNames() {
    }

    /**
     * 秒杀下单交换机与队列
     */
    public static final String SECKILL_EXCHANGE = "campus.seckill.exchange";
    public static final String SECKILL_ORDER_QUEUE = "campus.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    /**
     * 死信交换机与队列
     */
    public static final String ORDER_DLX_EXCHANGE = "campus.order.dlx.exchange";
    public static final String ORDER_DLQ = "campus.order.dlq";
    public static final String ORDER_DLQ_ROUTING_KEY = "order.dead";

    /**
     * 延迟关单交换机与队列
     */
    public static final String ORDER_DELAY_EXCHANGE = "campus.order.delay.exchange";
    public static final String ORDER_TIMEOUT_QUEUE = "campus.order.timeout.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";

    /**
     * 商户变更 -> ES 增量同步
     */
    public static final String SHOP_CHANGE_EXCHANGE = "campus.shop.exchange";
    public static final String SHOP_ES_SYNC_QUEUE = "campus.shop.es.queue";
    public static final String SHOP_ES_SYNC_ROUTING_KEY = "shop.es.sync";

    /**
     * 商户 ES 同步死信
     */
    public static final String SHOP_ES_DLX_EXCHANGE = "campus.shop.dlx.exchange";
    public static final String SHOP_ES_DLQ = "campus.shop.es.dlq";
    public static final String SHOP_ES_DLQ_ROUTING_KEY = "shop.es.dead";

    /**
     * 商品库存/销量变更 -> ES 局部更新
     */
    public static final String ITEM_SYNC_EXCHANGE = "campus.item.exchange";
    public static final String ITEM_ES_SYNC_QUEUE = "campus.item.es.queue";
    public static final String ITEM_ES_SYNC_ROUTING_KEY = "item.es.sync";
}