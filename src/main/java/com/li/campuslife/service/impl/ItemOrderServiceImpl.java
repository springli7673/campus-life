package com.li.campuslife.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.campuslife.dto.ItemOrderCreateDTO;
import com.li.campuslife.dto.ItemOrderVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.dto.UserDTO;
import com.li.campuslife.entity.Item;
import com.li.campuslife.entity.ItemOrder;
import com.li.campuslife.entity.ItemOrderDetail;
import com.li.campuslife.exception.BizException;
import com.li.campuslife.mapper.ItemMapper;
import com.li.campuslife.mapper.ItemOrderDetailMapper;
import com.li.campuslife.mapper.ItemOrderMapper;
import com.li.campuslife.mq.ItemSyncProducer;
import com.li.campuslife.service.IItemOrderService;
import com.li.campuslife.utils.RedisIdWorker;
import com.li.campuslife.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品订单。
 *
 * <p>库存扣减用 {@code UPDATE ... WHERE stock >= qty} 一条 SQL 完成判断与扣减，
 * 由 InnoDB 行锁保证不超卖；取消订单时按明细回补。</p>
 *
 * <p>注意：这里没有走 MQ 异步下单。秒杀场景才需要削峰，
 * 普通商品下单同步落库更简单，出了问题也好排查。</p>
 */
@Service
@RequiredArgsConstructor
public class ItemOrderServiceImpl implements IItemOrderService {

    private static final Logger log = LoggerFactory.getLogger(ItemOrderServiceImpl.class);

    private static final int MAX_LINES = 20;
    private static final int MAX_QUANTITY = 99;
    private static final int LIST_LIMIT = 50;

    /** 请求幂等：order:req:{userId}:{requestId} -> PENDING / orderNo */
    private static final String IDEM_KEY_PREFIX = "order:req:";
    private static final String IDEM_PENDING = "PENDING";
    private static final Duration IDEM_TTL = Duration.ofMinutes(10);

    private final ItemMapper itemMapper;
    private final ItemOrderMapper itemOrderMapper;
    private final ItemOrderDetailMapper itemOrderDetailMapper;
    private final RedisIdWorker redisIdWorker;
    private final ItemSyncProducer itemSyncProducer;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result create(ItemOrderCreateDTO dto) {
        Long userId = currentUserId();
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException("订单不能为空");
        }
        if (dto.getItems().size() > MAX_LINES) {
            throw new BizException("一次最多下单 " + MAX_LINES + " 种商品");
        }

        // 请求幂等：同一 requestId 只生效一次
        String requestId = StrUtil.trimToEmpty(dto.getRequestId());
        String idemKey = requestId.isEmpty() ? null : IDEM_KEY_PREFIX + userId + ":" + requestId;

        if (idemKey != null) {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idemKey, IDEM_PENDING, IDEM_TTL);
            if (!Boolean.TRUE.equals(acquired)) {
                String existing = stringRedisTemplate.opsForValue().get(idemKey);
                // 已经处理完：直接把上一次的订单还回去
                if (StrUtil.isNotBlank(existing) && !IDEM_PENDING.equals(existing)) {
                    ItemOrder done = itemOrderMapper.selectOne(new QueryWrapper<ItemOrder>()
                            .eq("order_no", existing)
                            .eq("user_id", userId));
                    if (done != null) {
                        log.info("[item-order] 重复请求命中幂等记录 requestId={} orderNo={}", requestId, existing);
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("orderNo", done.getOrderNo());
                        data.put("totalAmount", done.getTotalAmount());
                        data.put("duplicate", true);
                        return Result.ok(data);
                    }
                }
                // 还在处理中：直接拒绝，避免并发双写
                throw new BizException("订单正在处理中，请勿重复提交");
            }
        }

        try {
            return doCreate(dto, userId, idemKey);
        } catch (RuntimeException e) {
            // 业务失败要释放占位，否则用户 10 分钟内无法重试
            if (idemKey != null) {
                stringRedisTemplate.delete(idemKey);
            }
            throw e;
        }
    }

    private Result doCreate(ItemOrderCreateDTO dto, Long userId, String idemKey) {
        String orderNo = String.valueOf(redisIdWorker.nextId("item-order"));
        int totalAmount = 0;
        List<ItemOrderDetail> details = new ArrayList<>(dto.getItems().size());

        for (ItemOrderCreateDTO.Line line : dto.getItems()) {
            if (line.getItemId() == null) {
                throw new BizException("商品不能为空");
            }
            int quantity = line.getQuantity() == null ? 1 : line.getQuantity();
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                throw new BizException("购买数量需在 1-" + MAX_QUANTITY + " 之间");
            }

            Item item = itemMapper.selectById(line.getItemId());
            if (item == null || item.getStatus() == null || item.getStatus() != 1) {
                throw new BizException("商品已下架");
            }
            if (itemMapper.deductStock(item.getId(), quantity) == 0) {
                throw new BizException("库存不足：" + item.getName());
            }

            totalAmount += item.getPrice() * quantity;
            details.add(new ItemOrderDetail()
                    .setOrderNo(orderNo)
                    .setItemId(item.getId())
                    .setItemName(item.getName())
                    .setItemImage(item.getImage())
                    .setPrice(item.getPrice())
                    .setQuantity(quantity));
        }

        ItemOrder order = new ItemOrder()
                .setOrderNo(orderNo)
                .setUserId(userId)
                .setTotalAmount(totalAmount)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now());
        itemOrderMapper.insert(order);
        for (ItemOrderDetail detail : details) {
            itemOrderDetailMapper.insert(detail);
        }

        notifyEsAfterCommit(details);

        // 把占位值换成订单号，后续重复请求直接返回它；失败不影响下单结果
        if (idemKey != null) {
            try {
                stringRedisTemplate.opsForValue().set(idemKey, orderNo, IDEM_TTL);
            } catch (Exception e) {
                log.warn("[item-order] 幂等记录写入失败 orderNo={}: {}", orderNo, e.toString());
            }
        }

        log.info("[item-order] 下单成功 orderNo={} userId={} 金额={}分 明细={}",
                orderNo, userId, totalAmount, details.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("totalAmount", totalAmount);
        return Result.ok(data);
    }

    @Override
    public Result pay(String orderNo) {
        Long userId = currentUserId();
        int rows = itemOrderMapper.update(null, new UpdateWrapper<ItemOrder>()
                .eq("order_no", orderNo)
                .eq("user_id", userId)
                .eq("status", 1)
                .set("status", 2)
                .set("pay_time", LocalDateTime.now()));
        if (rows == 0) {
            return Result.fail("订单不存在，或已支付/已取消");
        }
        log.info("[item-order] 支付成功 orderNo={} userId={}", orderNo, userId);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancel(String orderNo) {
        Long userId = currentUserId();
        ItemOrder order = itemOrderMapper.selectOne(new QueryWrapper<ItemOrder>()
                .eq("order_no", orderNo)
                .eq("user_id", userId));
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!closeIfUnpaid(orderNo)) {
            return Result.fail("只有待支付订单可以取消");
        }
        restoreStockAndNotify(orderNo);
        return Result.ok();
    }

    @Override
    public Result listMine() {
        Long userId = currentUserId();
        List<ItemOrder> orders = itemOrderMapper.selectList(new QueryWrapper<ItemOrder>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT " + LIST_LIMIT));
        return Result.ok(toVOList(orders));
    }

    @Override
    public Result detail(String orderNo) {
        Long userId = currentUserId();
        ItemOrder order = itemOrderMapper.selectOne(new QueryWrapper<ItemOrder>()
                .eq("order_no", orderNo)
                .eq("user_id", userId));
        if (order == null) {
            return Result.fail("订单不存在");
        }
        List<ItemOrderVO> list = toVOList(List.of(order));
        return Result.ok(list.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders(int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<ItemOrder> expired = itemOrderMapper.selectList(new QueryWrapper<ItemOrder>()
                .eq("status", 1)
                .lt("create_time", deadline)
                .last("LIMIT 200"));
        int closed = 0;
        for (ItemOrder order : expired) {
            if (closeIfUnpaid(order.getOrderNo())) {
                restoreStockAndNotify(order.getOrderNo());
                closed++;
            }
        }
        if (closed > 0) {
            log.info("[item-order] 关闭超时订单 {} 笔（阈值 {} 分钟）", closed, timeoutMinutes);
        }
        return closed;
    }

    /** 条件更新：只有状态仍为待支付才关单，保证幂等 */
    private boolean closeIfUnpaid(String orderNo) {
        int rows = itemOrderMapper.update(null, new UpdateWrapper<ItemOrder>()
                .eq("order_no", orderNo)
                .eq("status", 1)
                .set("status", 3)
                .set("cancel_time", LocalDateTime.now()));
        return rows > 0;
    }

    private void restoreStockAndNotify(String orderNo) {
        List<ItemOrderDetail> details = itemOrderDetailMapper.selectList(
                new QueryWrapper<ItemOrderDetail>().eq("order_no", orderNo));
        for (ItemOrderDetail detail : details) {
            itemMapper.restoreStock(detail.getItemId(), detail.getQuantity());
        }
        notifyEsAfterCommit(details);
    }

    /**
     * 事务提交后再投递 ES 同步消息：提前发的话消费者可能查到未提交的库存，
     * 反而把索引写成旧值。
     */
    private void notifyEsAfterCommit(List<ItemOrderDetail> details) {
        if (details.isEmpty()) {
            return;
        }
        List<Long> itemIds = details.stream().map(ItemOrderDetail::getItemId).distinct().toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    itemIds.forEach(itemSyncProducer::send);
                }
            });
        } else {
            itemIds.forEach(itemSyncProducer::send);
        }
    }

    private List<ItemOrderVO> toVOList(List<ItemOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<String> orderNos = orders.stream().map(ItemOrder::getOrderNo).toList();
        List<ItemOrderDetail> allDetails = itemOrderDetailMapper.selectList(
                new QueryWrapper<ItemOrderDetail>().in("order_no", orderNos));

        Map<String, List<ItemOrderDetail>> grouped = new HashMap<>();
        for (ItemOrderDetail detail : allDetails) {
            grouped.computeIfAbsent(detail.getOrderNo(), key -> new ArrayList<>()).add(detail);
        }

        List<ItemOrderVO> result = new ArrayList<>(orders.size());
        for (ItemOrder order : orders) {
            result.add(ItemOrderVO.builder()
                    .orderNo(order.getOrderNo())
                    .totalAmount(order.getTotalAmount())
                    .status(order.getStatus())
                    .statusText(statusText(order.getStatus()))
                    .createTime(order.getCreateTime())
                    .details(grouped.getOrDefault(order.getOrderNo(), List.of()))
                    .build());
        }
        return result;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待支付";
            case 2 -> "已支付";
            case 3 -> "已取消";
            default -> "未知";
        };
    }

    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new BizException("请先登录");
        }
        return user.getId();
    }
}
