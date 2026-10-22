package com.li.campuslife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.entity.BlogComments;
import com.li.campuslife.mapper.BlogCommentsMapper;
import com.li.campuslife.service.IBlogCommentsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
