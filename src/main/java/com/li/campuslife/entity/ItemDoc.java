package com.li.campuslife.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 商品索引文档。字段与前端 Item 对齐，命中的记录可直接返回，不用回查 DB。
 */
@Data
public class ItemDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long storeId;

    private String storeName;

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

    private String updateTime;
}
