package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    /** 删除自己的笔记（连同评论） */
    Result deleteMine(Long blogId);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
