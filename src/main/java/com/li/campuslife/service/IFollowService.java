package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Follow;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
