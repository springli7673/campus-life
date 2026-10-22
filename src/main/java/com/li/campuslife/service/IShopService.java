package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    /**
     * 按类型 + 校区查询商户，距离以校区中心为基准。
     *
     * @param sortBy distance（默认）/ comments / score
     */
    Result queryShopByType(Integer typeId, Integer current, String sortBy, Long campusId);

    /** 关键词搜索商户名、地址、商圈 */
    Result search(String keyword, Long campusId, Integer current);
}
