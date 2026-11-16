package com.li.campuslife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商城商品（数据来自公开商品数据集，已清洗）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_item")
public class Item implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    /** 单位：分 */
    private Integer price;

    private Integer stock;

    private String image;

    private String category;

    private String brand;

    /** JSON 字符串，例如 {"颜色":"红色","尺码":"39"} */
    private String spec;

    private Integer sold;

    private Integer commentCount;

    /** 1 上架 2 下架 3 删除 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
