package com.li.campuslife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.Result;
import com.li.campuslife.dto.ScrollResult;
import com.li.campuslife.dto.UserDTO;
import com.li.campuslife.entity.Blog;
import com.li.campuslife.entity.Follow;
import com.li.campuslife.entity.User;
import com.li.campuslife.mapper.BlogMapper;
import com.li.campuslife.service.IBlogCommentsService;
import com.li.campuslife.service.IBlogService;
import com.li.campuslife.service.IFollowService;
import com.li.campuslife.service.IUserService;
import com.li.campuslife.utils.SystemConstants;
import com.li.campuslife.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.li.campuslife.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.li.campuslife.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;
    @Autowired
    private IBlogCommentsService blogCommentsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteMine(Long blogId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Blog blog = getById(blogId);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        if (!blog.getUserId().equals(user.getId())) {
            return Result.fail("只能删除自己的笔记");
        }
        removeById(blogId);
        // 顺带清掉评论，避免孤儿数据
        blogCommentsService.remove(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.li.campuslife.entity.BlogComments>()
                .eq("blog_id", blogId));
        return Result.ok();
    }

    @Override
    public Result queryLatestBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("博客不存在");
        }
        //2.查询blog关联的用户
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 未登录也可以看热门笔记，此时没有"我是否点赞"的概念
        if (UserHolder.getUser() == null) {
            blog.setIsLike(false);
            return;
        }
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        //根据用户查询
        Page<Blog> page=query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当前页数据
        List<Blog> records = page.getRecords();
        //查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否点赞过
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.如果未点赞，进行点赞
        if (score == null) {
            // 未点赞 -> 点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞 -> 取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1.查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据用户id查询用户
        List<UserDTO> userDTOs = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回结果
        return Result.ok(userDTOs);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        // 参数校验：标题和正文必填，长度与表结构对齐
        String title = StrUtil.trimToEmpty(blog.getTitle());
        String content = StrUtil.trimToEmpty(blog.getContent());
        if (title.isEmpty()) {
            return Result.fail("标题不能为空");
        }
        if (title.length() > 100) {
            return Result.fail("标题最多 100 个字");
        }
        if (content.isEmpty()) {
            return Result.fail("正文不能为空");
        }
        if (content.length() > 2000) {
            return Result.fail("正文最多 2000 个字");
        }
        blog.setTitle(title);
        blog.setContent(content);
        blog.setImages(StrUtil.trimToEmpty(blog.getImages()));
        // 未关联商户时用 0 占位（tb_blog.shop_id 非空）
        blog.setShopId(blog.getShopId() == null ? 0L : blog.getShopId());
        // 计数由服务端初始化，避免前端伪造
        blog.setLiked(0);
        blog.setComments(0);
        blog.setUserId(user.getId());
        //2.保存探店笔记
        boolean isSuccess=save(blog);
        if(!isSuccess){
            return Result.fail("保存失败");
        }
        //3.查询作者的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //4.把笔记推送给粉丝
        for(Follow follow : follows){
            Long userId=follow.getUserId();
            String key=FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(typedTuples==null||typedTuples.isEmpty()){
            //非空判断
            return Result.ok(Collections.emptyList());
        }
        //3.解析数据blogId，minTime最小时间戳，offset
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;//获取offset
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            //获取blogId
            ids.add(Long.valueOf(tuple.getValue()));
            //获取最小时间戳
            long time = tuple.getScore().longValue();
            if(time==minTime){
                os++;
            }else{
                minTime = time;
                os=1;
            }
        }
        //4.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //5.返回结果
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
