package com.li.campuslife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 异常订单：死信、投递失败、回补失败都记在这里，供人工/定时补偿。
 */
@Data
@Accessors(chain = true)
@TableName("tb_order_exception")
public class OrderException {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long userId;
    private Long voucherId;
    private String reason;
    private String payload;
    /**
     * 0 OPEN / 1 REPAIRED / 2 IGNORED
     */
    private Integer status;
    private Integer retryCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}