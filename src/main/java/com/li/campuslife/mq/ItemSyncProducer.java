package com.li.campuslife.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 商品索引同步消息。
 *
 * <p>触发点：下单扣库存、取消订单回补、后台改价/上下架。
 * 消费端回查 DB 重建该商品的索引文档，投递失败只记日志——
 * 索引旧一点不影响交易，每日全量重建会纠正。</p>
 */
@Component
public class ItemSyncProducer {

    private static final Logger log = LoggerFactory.getLogger(ItemSyncProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    public ItemSyncProducer(RabbitTemplate rabbitTemplate,
                             @Value("${campus.search.item-es-enabled:true}") boolean enabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = enabled;
    }

    public void send(Long itemId) {
        if (!enabled || itemId == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    MqNames.ITEM_SYNC_EXCHANGE,
                    MqNames.ITEM_ES_SYNC_ROUTING_KEY,
                    new ItemSyncMessage(itemId));
        } catch (Exception e) {
            log.error("[item-sync] 库存变更消息投递失败 itemId={}", itemId, e);
        }
    }
}
