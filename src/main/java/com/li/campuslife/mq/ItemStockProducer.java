package com.li.campuslife.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 商品库存/销量变更消息。
 *
 * <p>下单扣库存、取消回补后各投一条，消费端回查 DB 重建该商品的索引文档。
 * 投递失败只记日志：索引里的库存会旧一点，但每日全量重建能纠正。</p>
 */
@Component
public class ItemStockProducer {

    private static final Logger log = LoggerFactory.getLogger(ItemStockProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    public ItemStockProducer(RabbitTemplate rabbitTemplate,
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
                    new ItemStockMessage(itemId));
        } catch (Exception e) {
            log.error("[item-sync] 库存变更消息投递失败 itemId={}", itemId, e);
        }
    }
}
