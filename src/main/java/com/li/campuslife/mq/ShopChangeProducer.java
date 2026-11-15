package com.li.campuslife.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 商户变更消息。mode=db 时不投递，避免回滚后队列里堆无用消息。
 */
@Component
public class ShopChangeProducer {

    private static final Logger log = LoggerFactory.getLogger(ShopChangeProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    public ShopChangeProducer(RabbitTemplate rabbitTemplate,
                              @Value("${campus.search.mode:es}") String mode) {
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = "es".equalsIgnoreCase(mode);
    }

    public void send(Long shopId) {
        if (!enabled || shopId == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    MqNames.SHOP_CHANGE_EXCHANGE,
                    MqNames.SHOP_ES_SYNC_ROUTING_KEY,
                    new ShopChangeMessage(shopId));
            log.info("[shop-sync] 商户变更消息已投递 shopId={}", shopId);
        } catch (Exception e) {
            // 消息丢了还有全量同步 Job 兜底
            log.error("[shop-sync] 商户变更消息投递失败 shopId={}", shopId, e);
        }
    }
}
