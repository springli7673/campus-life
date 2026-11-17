package com.li.campuslife.mq;

import com.li.campuslife.entity.Item;
import com.li.campuslife.mapper.ItemMapper;
import com.li.campuslife.search.EsItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费商品变更消息，回查 DB 后重建该商品的索引文档。
 *
 * <p>回查而不是用消息里的字段：下单/取消可能连续发生，消息顺序无法保证，
 * 只有以数据库为准才不会把索引写成中间状态。</p>
 */
@Component
public class ItemSyncListener {

    private static final Logger log = LoggerFactory.getLogger(ItemSyncListener.class);

    private final ItemMapper itemMapper;
    private final EsItemService esItemService;

    public ItemSyncListener(ItemMapper itemMapper, EsItemService esItemService) {
        this.itemMapper = itemMapper;
        this.esItemService = esItemService;
    }

    @RabbitListener(queues = MqNames.ITEM_ES_SYNC_QUEUE)
    public void onChange(ItemSyncMessage message) {
        Item item = itemMapper.selectById(message.itemId());
        try {
            if (item == null || item.getStatus() == null || item.getStatus() != 1) {
                esItemService.deleteItem(message.itemId());
                log.info("[item-sync] 商品不存在或已下架，移除索引 itemId={}", message.itemId());
                return;
            }
            esItemService.indexItem(item);
            log.debug("[item-sync] 索引已更新 itemId={} stock={}", item.getId(), item.getStock());
        } catch (Exception e) {
            log.error("[item-sync] 更新索引失败 itemId={}", message.itemId(), e);
            throw new AmqpRejectAndDontRequeueException("item es sync failed", e);
        }
    }
}
