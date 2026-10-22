package com.li.campuslife.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.SeckillVoucher;
import com.li.campuslife.entity.VoucherOrder;
import com.li.campuslife.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把进行中/未开始的秒杀券预热到 Redis：
 * 活动时间写入 hash、库存写入 string、已下单用户重建到 set。
 */
@Component
public class SeckillPreheatService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillPreheatService.class);

    private final ISeckillVoucherService seckillVoucherService;
    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    public SeckillPreheatService(ISeckillVoucherService seckillVoucherService,
                                 IVoucherOrderService voucherOrderService,
                                 StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.voucherOrderService = voucherOrderService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = preheat(false);
            log.info("[preheat] 启动预热完成，处理 {} 张进行中/未开始的秒杀券", count);
        } catch (Exception e) {
            log.warn("[preheat] 启动预热失败，可稍后调用 /admin/seckill/preheat 重试", e);
        }
    }

    /**
     * @param force true 时覆盖库存（演示重置用），false 时只在 key 不存在时写入
     * @return 处理的券数量
     */
    public int preheat(boolean force) {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        int count = 0;
        for (SeckillVoucher sv : vouchers) {
            if (sv.getBeginTime() == null || sv.getEndTime() == null) {
                continue;
            }
            String voucherId = sv.getVoucherId().toString();
            Map<String, String> activity = new HashMap<>(2);
            activity.put("begin", String.valueOf(sv.getBeginTime().atZone(ZoneId.systemDefault()).toEpochSecond()));
            activity.put("end", String.valueOf(sv.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond()));
            stringRedisTemplate.opsForHash().putAll(RedisConstants.SECKILL_ACTIVITY_KEY + voucherId, activity);

            String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
            if (force) {
                stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(sv.getStock()));
            } else {
                stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(sv.getStock()));
            }

            String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;
            if (force) {
                // 强制重置时先清空占位集合，否则上一次失败/回滚残留的用户会一直"重复下单"
                stringRedisTemplate.delete(orderKey);
            }
            List<VoucherOrder> orders = voucherOrderService.list(
                    new QueryWrapper<VoucherOrder>()
                            .eq("voucher_id", sv.getVoucherId())
                            .ne("status", 4));
            for (VoucherOrder order : orders) {
                stringRedisTemplate.opsForSet().add(orderKey, order.getUserId().toString());
            }
            count++;
        }
        return count;
    }
}