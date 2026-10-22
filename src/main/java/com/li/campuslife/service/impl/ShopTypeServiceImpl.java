package com.li.campuslife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.entity.ShopType;
import com.li.campuslife.mapper.ShopTypeMapper;
import com.li.campuslife.service.IShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.li.campuslife.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryTypeList() {
        //1. 查询redis缓存
        String jsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        //2. 判断缓存是否命中
        if (StrUtil.isNotBlank(jsonStr)) {
            // json字符串转集合
            return JSONUtil.toList(jsonStr, ShopType.class);
        }
        //3. 缓存没命中 → 查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //4. 写入redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,
                JSONUtil.toJsonStr(typeList),
                30, TimeUnit.MINUTES);
        return typeList;
    }
}
