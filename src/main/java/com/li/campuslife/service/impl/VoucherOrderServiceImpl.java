package com.li.campuslife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.CouponOrderVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.dto.UserDTO;
import com.li.campuslife.entity.Shop;
import com.li.campuslife.entity.Voucher;
import com.li.campuslife.entity.VoucherOrder;
import com.li.campuslife.exception.RateLimitException;
import com.li.campuslife.mapper.ShopMapper;
import com.li.campuslife.mapper.VoucherMapper;
import com.li.campuslife.mapper.VoucherOrderMapper;
import com.li.campuslife.mq.SeckillCorrelationData;
import com.li.campuslife.mq.SeckillOrderMessage;
import com.li.campuslife.mq.SeckillOrderProducer;
import com.li.campuslife.service.ISeckillVoucherService;
import com.li.campuslife.service.IVoucherOrderService;
import com.li.campuslife.service.OrderRecoveryService;
import com.li.campuslife.service.SeckillRateLimiter;
import com.li.campuslife.utils.RedisIdWorker;
import com.li.campuslife.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill-rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOrderProducer seckillOrderProducer;
    private final OrderRecoveryService recoveryService;
    private final SeckillRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;
    private final VoucherMapper voucherMapper;
    private final ShopMapper shopMapper;

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService,
                                   RedisIdWorker redisIdWorker,
                                   StringRedisTemplate stringRedisTemplate,
                                   SeckillOrderProducer seckillOrderProducer,
                                   OrderRecoveryService recoveryService,
                                   SeckillRateLimiter rateLimiter,
                                   TransactionTemplate transactionTemplate,
                                   VoucherMapper voucherMapper,
                                   ShopMapper shopMapper) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.seckillOrderProducer = seckillOrderProducer;
        this.recoveryService = recoveryService;
        this.rateLimiter = rateLimiter;
        this.transactionTemplate = transactionTemplate;
        this.voucherMapper = voucherMapper;
        this.shopMapper = shopMapper;
    }

    @Override
    public Result payOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        boolean updated = update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<VoucherOrder>()
                .eq("id", orderId)
                .eq("user_id", user.getId())
                .eq("status", 1)
                .set("status", 2)
                .set("pay_time", LocalDateTime.now()));
        if (!updated) {
            return Result.fail("订单不存在或已支付");
        }
        return Result.ok();
    }

    @Override
    public Result listMine() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        List<VoucherOrder> orders = list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<VoucherOrder>()
                .eq("user_id", user.getId())
                .orderByDesc("create_time")
                .last("LIMIT 100"));
        if (orders.isEmpty()) {
            return Result.ok(List.of());
        }

        List<Long> voucherIds = orders.stream().map(VoucherOrder::getVoucherId).distinct().toList();
        Map<Long, Voucher> vouchers = new HashMap<>();
        for (Voucher voucher : voucherMapper.selectBatchIds(voucherIds)) {
            vouchers.put(voucher.getId(), voucher);
        }

        List<Long> shopIds = vouchers.values().stream().map(Voucher::getShopId).distinct().toList();
        Map<Long, String> shopNames = new HashMap<>();
        if (!shopIds.isEmpty()) {
            for (Shop shop : shopMapper.selectBatchIds(shopIds)) {
                shopNames.put(shop.getId(), shop.getName());
            }
        }

        List<CouponOrderVO> result = new ArrayList<>(orders.size());
        for (VoucherOrder order : orders) {
            Voucher voucher = vouchers.get(order.getVoucherId());
            result.add(CouponOrderVO.builder()
                    .orderId(order.getId())
                    .voucherId(order.getVoucherId())
                    .title(voucher == null ? "券已下架" : voucher.getTitle())
                    .subTitle(voucher == null ? null : voucher.getSubTitle())
                    .shopName(voucher == null ? null : shopNames.get(voucher.getShopId()))
                    .payValue(voucher == null ? null : voucher.getPayValue())
                    .actualValue(voucher == null ? null : voucher.getActualValue())
                    .status(order.getStatus())
                    .statusText(statusText(order.getStatus()))
                    .createTime(order.getCreateTime())
                    .useTime(order.getUseTime())
                    .build());
        }
        return Result.ok(result);
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待支付";
            case 2 -> "待使用";
            case 3 -> "已核销";
            case 4 -> "已取消";
            case 5 -> "退款中";
            case 6 -> "已退款";
            default -> "未知";
        };
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        // 限流放在最前面：既挡住活动级洪峰，也挡住单用户的脚本刷单
        if (!rateLimiter.allowActivity(voucherId)) {
            throw new RateLimitException("活动太火爆了，请稍后再试");
        }
        if (!rateLimiter.allowUser(voucherId, user.getId())) {
            throw new RateLimitException("操作过于频繁，请稍后再试");
        }
        long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                user.getId().toString());

        int code = result == null ? -1 : result.intValue();
        switch (code) {
            case 0 -> log.debug("[seckill] 预扣成功 orderId={} voucherId={}", orderId, voucherId);
            case 1 -> { return Result.fail("库存不足"); }
            case 2 -> { return Result.fail("请勿重复下单"); }
            case 3 -> { return Result.fail("活动未开始"); }
            case 4 -> { return Result.fail("活动已结束"); }
            default -> { return Result.fail("活动不存在"); }
        }

        SeckillOrderMessage message = new SeckillOrderMessage(orderId, user.getId(), voucherId);
        try {
            seckillOrderProducer.send(message,
                    new SeckillCorrelationData(String.valueOf(orderId), user.getId(), voucherId));
        } catch (RuntimeException e) {
            log.error("[seckill] 消息投递失败，回滚预扣 orderId={} voucherId={}", orderId, voucherId, e);
            stringRedisTemplate.execute(ROLLBACK_SCRIPT, Collections.emptyList(),
                    voucherId.toString(), user.getId().toString());
            return Result.fail("下单失败，请稍后重试");
        }
        return Result.ok(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(SeckillOrderMessage message) {
        VoucherOrder order = new VoucherOrder()
                .setId(message.orderId())
                .setUserId(message.userId())
                .setVoucherId(message.voucherId())
                .setPayType(1)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now());
        // 唯一索引 uk_user_voucher 在并发/重复消费时兜底，冲突会抛 DuplicateKeyException
        save(order);

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", message.voucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            // 抛异常回滚已插入的订单，交给重试/死信
            throw new IllegalStateException("DB 库存不足，voucherId=" + message.voucherId());
        }
        log.info("[seckill] 订单落库成功 orderId={} userId={} voucherId={}",
                message.orderId(), message.userId(), message.voucherId());
    }

    /**
     * 超时关单：条件更新保证支付/关单只有一方成功；DB 提交后再回补 Redis。
     */
    @Override
    public boolean cancelIfUnpaid(Long orderId) {
        Boolean cancelled = transactionTemplate.execute(status -> {
            VoucherOrder order = getById(orderId);
            if (order == null || order.getStatus() == null || order.getStatus() != 1) {
                return false;
            }
            boolean updated = update()
                    .set("status", 4)
                    .set("cancel_time", LocalDateTime.now())
                    .eq("id", orderId)
                    .eq("status", 1)
                    .update();
            if (!updated) {
                return false;
            }
            seckillVoucherService.update()
                    .setSql("stock = stock + 1")
                    .eq("voucher_id", order.getVoucherId())
                    .update();
            log.info("[cancel] 订单已取消 orderId={} voucherId={}", orderId, order.getVoucherId());
            return true;
        });

        if (Boolean.TRUE.equals(cancelled)) {
            VoucherOrder order = getById(orderId);
            if (order != null) {
                recoveryService.releaseRedisStock(orderId, order.getUserId(), order.getVoucherId(),
                        "cancel redis replenish failed");
            }
        }
        return Boolean.TRUE.equals(cancelled);
    }

    @Override
    public Result queryOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }
}
