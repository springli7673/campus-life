package com.li.campuslife.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.dto.ReconcileResult;
import com.li.campuslife.entity.ReconcileLog;
import com.li.campuslife.entity.SeckillVoucher;
import com.li.campuslife.entity.VoucherOrder;
import com.li.campuslife.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 库存对账：以 DB 为准，比对 Redis 库存与占位集合，发现差异时修复并落 {@code tb_reconcile_log}。
 *
 * <p>为什么要"两次观测才修"：消息在投递/消费途中时，Redis 已经预扣、DB 还没落库，
 * 此刻的 diff 是暂时的。首次观测只记录，下一轮（默认 5 分钟后）仍是同样的差异才动手，
 * 避免把在途请求的库存"改回去"造成超卖。</p>
 */
@Service
public class StockReconcileService {

    private static final Logger log = LoggerFactory.getLogger(StockReconcileService.class);

    /** 只有当前值仍等于观测值时才覆盖，防止并发预扣被踩掉 */
    private static final DefaultRedisScript<Long> CAS_SET_SCRIPT;

    static {
        CAS_SET_SCRIPT = new DefaultRedisScript<>();
        CAS_SET_SCRIPT.setLocation(new ClassPathResource("reconcile-set.lua"));
        CAS_SET_SCRIPT.setResultType(Long.class);
    }

    /** 上一轮观测到的差异（单实例内存；多实例部署需要换成 Redis 记录） */
    private final Map<Long, Observation> pending = new ConcurrentHashMap<>();

    private record Observation(int redisStock, Set<String> stray, Set<String> missing) {
    }

    private final ISeckillVoucherService seckillVoucherService;
    private final IVoucherOrderService voucherOrderService;
    private final IReconcileLogService reconcileLogService;
    private final StringRedisTemplate stringRedisTemplate;

    public StockReconcileService(ISeckillVoucherService seckillVoucherService,
                                 IVoucherOrderService voucherOrderService,
                                 IReconcileLogService reconcileLogService,
                                 StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.voucherOrderService = voucherOrderService;
        this.reconcileLogService = reconcileLogService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 全量对账。
     *
     * @param immediateRepair true 时跳过"两次观测"确认，立即修复（运维手工触发用）
     */
    public List<ReconcileResult> reconcileAll(boolean immediateRepair) {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        List<ReconcileResult> results = new ArrayList<>(vouchers.size());
        for (SeckillVoucher sv : vouchers) {
            try {
                results.add(reconcileOne(sv, immediateRepair));
            } catch (Exception e) {
                log.error("[reconcile] 对账失败 voucherId={}", sv.getVoucherId(), e);
            }
        }
        results.sort(Comparator.comparingLong(ReconcileResult::voucherId));
        return results;
    }

    private ReconcileResult reconcileOne(SeckillVoucher sv, boolean immediateRepair) {
        long voucherId = sv.getVoucherId();
        int dbStock = sv.getStock() == null ? 0 : sv.getStock();
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;

        String rawStock = stringRedisTemplate.opsForValue().get(stockKey);
        if (rawStock == null) {
            return new ReconcileResult(voucherId, dbStock, -1, 0, List.of(), List.of(), false, "未预热");
        }
        int redisStock;
        try {
            redisStock = Integer.parseInt(rawStock.trim());
        } catch (NumberFormatException e) {
            log.error("[reconcile] Redis 库存不是数字 voucherId={} value={}", voucherId, rawStock);
            return new ReconcileResult(voucherId, dbStock, -1, 0, List.of(), List.of(), false, "库存值异常");
        }

        Set<String> dbUsers = validOrderUsers(voucherId);
        Set<String> redisUsers = Optional.ofNullable(stringRedisTemplate.opsForSet().members(orderKey))
                .orElseGet(Set::of);
        Set<String> stray = new TreeSet<>(redisUsers);
        stray.removeAll(dbUsers);
        Set<String> missing = new TreeSet<>(dbUsers);
        missing.removeAll(redisUsers);
        int diff = redisStock - dbStock;

        if (diff == 0 && stray.isEmpty() && missing.isEmpty()) {
            pending.remove(voucherId);
            return new ReconcileResult(voucherId, dbStock, redisStock, dbUsers.size(),
                    List.of(), List.of(), false, "一致");
        }

        Observation now = new Observation(redisStock, Set.copyOf(stray), Set.copyOf(missing));
        if (!immediateRepair && !now.equals(pending.get(voucherId))) {
            pending.put(voucherId, now);
            log.warn("[reconcile] 首次观测到差异，等待下一轮复核 voucherId={} diff={} stray={} missing={}",
                    voucherId, diff, stray.size(), missing.size());
            return new ReconcileResult(voucherId, dbStock, redisStock, dbUsers.size(),
                    List.copyOf(stray), List.copyOf(missing), false, "首次观测，待复核");
        }

        List<String> actions = new ArrayList<>(3);
        if (diff != 0) {
            Long applied = stringRedisTemplate.execute(CAS_SET_SCRIPT, List.of(stockKey),
                    String.valueOf(redisStock), String.valueOf(dbStock));
            actions.add(applied != null && applied == 1L
                    ? "stock:" + redisStock + "->" + dbStock
                    : "stock:CAS冲突，下轮重试");
        }
        if (!missing.isEmpty()) {
            stringRedisTemplate.opsForSet().add(orderKey, missing.toArray(new String[0]));
            actions.add("sadd:" + missing.size());
        }
        if (!stray.isEmpty()) {
            stringRedisTemplate.opsForSet().remove(orderKey, stray.toArray());
            actions.add("srem:" + stray.size());
        }
        pending.remove(voucherId);

        String action = String.join(",", actions);
        String detail = truncate("diff=" + diff + ";stray=" + abbreviate(stray)
                + ";missing=" + abbreviate(missing) + ";action=" + action);
        reconcileLogService.save(new ReconcileLog()
                .setVoucherId(voucherId)
                .setDbSold(dbUsers.size())
                .setRedisStock(redisStock)
                .setDiff(diff)
                .setDetail(detail)
                .setCreateTime(LocalDateTime.now()));
        log.warn("[reconcile] 已修复 voucherId={} diff={} stray={} missing={} action={}",
                voucherId, diff, stray.size(), missing.size(), action);

        return new ReconcileResult(voucherId, dbStock, redisStock, dbUsers.size(),
                List.copyOf(stray), List.copyOf(missing), true, "已修复");
    }

    /** DB 中该券所有未取消订单的用户 */
    private Set<String> validOrderUsers(long voucherId) {
        List<Object> ids = voucherOrderService.listObjs(new QueryWrapper<VoucherOrder>()
                .select("user_id")
                .eq("voucher_id", voucherId)
                .ne("status", 4));
        Set<String> users = new HashSet<>(Math.max(16, ids.size() * 2));
        for (Object id : ids) {
            users.add(String.valueOf(id));
        }
        return users;
    }

    private static String abbreviate(Set<String> users) {
        if (users.isEmpty()) {
            return "-";
        }
        String joined = users.stream().limit(10).collect(Collectors.joining(","));
        return users.size() > 10 ? joined + "...(+" + (users.size() - 10) + ")" : joined;
    }

    private static String truncate(String text) {
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}
