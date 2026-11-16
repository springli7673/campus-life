package com.li.campuslife.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 商品视图对象：spec 从 JSON 字符串转成 Map，避免前端二次解析。
 *
 * <p>商品为平台自营，没有店铺维度；多商家会引入拆单与结算，本项目刻意不做。</p>
 */
@Data
@Builder
public class ItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 单位：分 */
    private Integer price;

    private Integer stock;

    private String image;

    private String category;

    private String brand;

    private Map<String, String> spec;

    private Integer sold;

    private Integer commentCount;
}
