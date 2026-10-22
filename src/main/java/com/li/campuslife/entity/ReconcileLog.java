package com.li.campuslife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 库存对账日志：只在发现差异时写入，记录当时的 DB/Redis 快照与修复动作。
 */
@Data
@Accessors(chain = true)
@TableName("tb_reconcile_log")
public class ReconcileLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long voucherId;
    /** DB 中未取消的订单数 */
    private Integer dbSold;
    /** 对账时刻 Redis 中的库存 */
    private Integer redisStock;
    /** redisStock - dbStock */
    private Integer diff;
    /** 差异明细（占位集合多出/缺失的用户、修复动作） */
    private String detail;
    private LocalDateTime createTime;
}
