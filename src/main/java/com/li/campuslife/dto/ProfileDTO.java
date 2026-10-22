package com.li.campuslife.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 资料编辑请求：昵称/头像落在 tb_user，其余落在 tb_user_info。
 */
@Data
public class ProfileDTO {

    private String nickName;
    private String icon;
    private String introduce;
    private String city;
    /**
     * true 女，false 男，null 表示不修改
     */
    private Boolean gender;
    private LocalDate birthday;
}
