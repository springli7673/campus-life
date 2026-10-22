package com.li.campuslife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 校区。商户、用户都挂在校区下，距离以校区中心为基准计算。
 */
@Data
@Accessors(chain = true)
@TableName("tb_campus")
public class Campus {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 如 豫东大学·主校区
     */
    private String name;
    private String city;
    private String address;
    private Double longitude;
    private Double latitude;
    /**
     * 1 启用，0 停用
     */
    private Integer status;
}
