package com.li.campuslife.search;

import com.li.campuslife.entity.Shop;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 商户检索文档。location 必须是 "纬度,经度"，即 {@code y,x}，写反了距离会算到几千公里外。
 */
@Data
public class ShopDoc {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private Long typeId;
    private Long campusId;
    private String name;
    private String area;
    private String address;
    /**
     * geo_point，格式 "lat,lon"
     */
    private String location;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    /**
     * DB 里是乘 10 的整数，索引里还原成 4.5 这种小数
     */
    private Double score;
    private String openHours;
    private String updateTime;
    /**
     * 非索引字段，按请求坐标算出来的直线距离（米）
     */
    private Double distance;

    public static ShopDoc from(Shop shop) {
        if (shop == null) {
            return null;
        }
        ShopDoc doc = new ShopDoc();
        doc.setId(shop.getId());
        doc.setTypeId(shop.getTypeId());
        doc.setCampusId(shop.getCampusId());
        doc.setName(shop.getName());
        doc.setArea(shop.getArea());
        doc.setAddress(shop.getAddress());
        if (shop.getY() != null && shop.getX() != null) {
            doc.setLocation(shop.getY() + "," + shop.getX());
        }
        doc.setAvgPrice(shop.getAvgPrice());
        doc.setSold(shop.getSold());
        doc.setComments(shop.getComments());
        doc.setScore(shop.getScore() == null ? null : shop.getScore() / 10.0);
        doc.setOpenHours(shop.getOpenHours());
        doc.setUpdateTime(shop.getUpdateTime() == null ? null : shop.getUpdateTime().format(TIME_FORMATTER));
        return doc;
    }
}
