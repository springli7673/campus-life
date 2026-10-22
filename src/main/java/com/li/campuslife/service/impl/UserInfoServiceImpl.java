package com.li.campuslife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.entity.UserInfo;
import com.li.campuslife.mapper.UserInfoMapper;
import com.li.campuslife.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
