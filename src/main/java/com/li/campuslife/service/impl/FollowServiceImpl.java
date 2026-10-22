package com.li.campuslife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.Result;
import com.li.campuslife.dto.UserDTO;
import com.li.campuslife.entity.Follow;
import com.li.campuslife.mapper.FollowMapper;
import com.li.campuslife.service.IFollowService;
import com.li.campuslife.service.IUserService;
import com.li.campuslife.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    /**
     * 根据用户id关注或取消关注
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.判断是关注还是取关
        String key = "follows:" + userId;
        if(isFollow){
            //如果关注了，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //3.把关注用户的id，放入set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
            //如果取关了，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if(isSuccess){
                //把关注用户的id，从set集合中删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }

        }
        return Result.ok();
    }

    /**
     * 判断当前用户是否关注了该用户
     * @param followUserId
     * @return
     */

    @Override
    public Result isFollow(Long followUserId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //3.判断
        return Result.ok(count > 0);
    }

    /**
     * 获取共同关注
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        //2.获取两个set的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //如果交集为空，返回空列表
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.根据id查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //5.返回用户
        return Result.ok(users);
    }
}
