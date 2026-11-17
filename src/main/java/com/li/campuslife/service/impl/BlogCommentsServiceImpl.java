package com.li.campuslife.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.CommentAddDTO;
import com.li.campuslife.dto.CommentVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.dto.UserDTO;
import com.li.campuslife.entity.Blog;
import com.li.campuslife.entity.BlogComments;
import com.li.campuslife.entity.User;
import com.li.campuslife.exception.BizException;
import com.li.campuslife.mapper.BlogCommentsMapper;
import com.li.campuslife.mapper.BlogMapper;
import com.li.campuslife.mapper.UserMapper;
import com.li.campuslife.service.IBlogCommentsService;
import com.li.campuslife.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记评论：两级树（一级评论 + 回复）。
 *
 * <p>parent_id 指向一级评论，answer_id 指向被回复的那条评论，
 * 用于展示「回复 @某人」。查询时一次取出全部评论再在内存组装，
 * 单篇笔记评论量有限（上限 200 条），避免递归查询。</p>
 */
@Service
@RequiredArgsConstructor
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    private static final int MAX_CONTENT_LENGTH = 255;
    private static final int MAX_COMMENTS = 200;

    private final UserMapper userMapper;
    private final BlogMapper blogMapper;

    @Override
    public Result listTree(Long blogId) {
        List<BlogComments> rows = list(new QueryWrapper<BlogComments>()
                .eq("blog_id", blogId)
                .eq("status", 0)
                .orderByAsc("create_time")
                .last("LIMIT " + MAX_COMMENTS));
        if (rows.isEmpty()) {
            return Result.ok(List.of());
        }

        Set<Long> userIds = new HashSet<>();
        for (BlogComments row : rows) {
            userIds.add(row.getUserId());
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        // commentId -> 作者 userId，用于把 answer_id 翻译成「回复 @某人」
        Map<Long, Long> authors = new HashMap<>();
        for (BlogComments row : rows) {
            authors.put(row.getId(), row.getUserId());
        }

        List<CommentVO> roots = new ArrayList<>();
        Map<Long, List<CommentVO>> replies = new HashMap<>();
        for (BlogComments row : rows) {
            CommentVO vo = toVO(row, users, authors);
            long parentId = row.getParentId() == null ? 0L : row.getParentId();
            if (parentId == 0L) {
                roots.add(vo);
            } else {
                replies.computeIfAbsent(parentId, key -> new ArrayList<>()).add(vo);
            }
        }
        for (CommentVO root : roots) {
            root.setReplies(replies.getOrDefault(root.getId(), List.of()));
        }
        return Result.ok(roots);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result add(CommentAddDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new BizException("请先登录");
        }
        if (dto == null || dto.getBlogId() == null) {
            throw new BizException("笔记不能为空");
        }
        String content = StrUtil.trimToEmpty(dto.getContent());
        if (content.isEmpty()) {
            throw new BizException("评论内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BizException("评论最多 " + MAX_CONTENT_LENGTH + " 个字");
        }

        Blog blog = blogMapper.selectById(dto.getBlogId());
        if (blog == null) {
            throw new BizException("笔记不存在或已删除");
        }

        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            BlogComments parent = getById(parentId);
            if (parent == null || !parent.getBlogId().equals(dto.getBlogId())) {
                throw new BizException("回复的评论不存在");
            }
            // 回复二级评论时，统一挂到它的一级评论下，保持只有两层
            long realParent = parent.getParentId() == null ? 0L : parent.getParentId();
            if (realParent != 0L) {
                parentId = realParent;
            }
        }

        BlogComments comment = new BlogComments()
                .setUserId(user.getId())
                .setBlogId(dto.getBlogId())
                .setParentId(parentId)
                .setAnswerId(dto.getAnswerId() == null ? 0L : dto.getAnswerId())
                .setContent(content)
                .setLiked(0)
                .setStatus(false)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        save(comment);

        blogMapper.update(null, new UpdateWrapper<Blog>()
                .eq("id", dto.getBlogId())
                .setSql("comments = comments + 1"));

        return Result.ok(comment.getId());
    }

    private CommentVO toVO(BlogComments row, Map<Long, User> users, Map<Long, Long> authors) {
        User author = users.get(row.getUserId());
        String answerName = null;
        if (row.getAnswerId() != null && row.getAnswerId() != 0L) {
            Long answerUserId = authors.get(row.getAnswerId());
            User answerUser = answerUserId == null ? null : users.get(answerUserId);
            if (answerUser != null) {
                answerName = answerUser.getNickName();
            }
        }
        return CommentVO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .name(author == null ? "匿名同学" : author.getNickName())
                .icon(author == null ? "" : author.getIcon())
                .content(row.getContent())
                .answerName(answerName)
                .liked(row.getLiked())
                .createTime(row.getCreateTime())
                .replies(new ArrayList<>())
                .build();
    }
}
