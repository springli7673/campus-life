package com.li.campuslife.mq;

import com.li.campuslife.entity.Shop;
import com.li.campuslife.mapper.ShopMapper;
import com.li.campuslife.search.EsShopSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费商户变更，按 shopId 回查 DB 后 upsert / delete 索引。
 * 失败直接 reject 不重回队列，由队列的 DLX 转入死信。
 */
@Component
@ConditionalOnProperty(prefix = "campus.search", name = "mode", havingValue = "es", matchIfMissing = true)
public class ShopChangeListener {

    private static final Logger log = LoggerFactory.getLogger(ShopChangeListener.class);

    private final ShopMapper shopMapper;
    private final EsShopSearchService esShopSearchService;

    public ShopChangeListener(ShopMapper shopMapper, EsShopSearchService esShopSearchService) {
        this.shopMapper = shopMapper;
        this.esShopSearchService = esShopSearchService;
    }

    @RabbitListener(queues = MqNames.SHOP_ES_SYNC_QUEUE)
    public void onChange(ShopChangeMessage message) {
        try {
            Shop shop = shopMapper.selectById(message.shopId());
            if (shop == null) {
                esShopSearchService.deleteShop(message.shopId());
                log.info("[shop-sync] 商户不存在，已删除索引 shopId={}", message.shopId());
            } else {
                esShopSearchService.indexShop(shop);
                log.info("[shop-sync] 索引已更新 shopId={}", message.shopId());
            }
        } catch (Exception e) {
            log.error("[shop-sync] 同步失败，消息进死信 shopId={}", message.shopId(), e);
            throw new AmqpRejectAndDontRequeueException("shop es sync failed", e);
        }
    }
}
