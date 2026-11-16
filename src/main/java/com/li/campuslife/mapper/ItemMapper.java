package com.li.campuslife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.campuslife.entity.Item;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 条件扣减库存：stock >= qty 才扣，返回 0 表示库存不足或已下架。
     * 判断与扣减在同一条 SQL 里完成，靠 InnoDB 行锁避免超卖。
     */
    @Update("UPDATE tb_item SET stock = stock - #{qty} WHERE id = #{id} AND status = 1 AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") Integer qty);

    /** 取消订单时回补库存 */
    @Update("UPDATE tb_item SET stock = stock + #{qty} WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") Integer qty);
}
