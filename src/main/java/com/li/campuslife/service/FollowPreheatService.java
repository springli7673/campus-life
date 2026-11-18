package com.li.campuslife.service;

import com.li.campuslife.entity.Follow;
import com.li.campuslife.mapper.FollowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 tb_follow 里的关注关系灌进 Redis 集合（follows:{userId}）。
 *
 * <p>共同关注、关注状态都依赖这两个 Set；种子数据只写进了 MySQL，
 * 不预热的话 SINTER 永远是空集。</p>
 */
@Component
public class FollowPreheatService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FollowPreheatService.class);
    private static final String KEY_PREFIX = "follows:";
    private static final String FLAG_KEY = "follows:initialized";

    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public FollowPreheatService(FollowMapper followMapper, StringRedisTemplate stringRedisTemplate) {
        this.followMapper = followMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(FLAG_KEY))) {
                return;
            }
            List<Follow> rows = followMapper.selectList(null);
            if (rows.isEmpty()) {
                return;
            }
            Map<Long, List<String>> grouped = new HashMap<>();
            for (Follow row : rows) {
                grouped.computeIfAbsent(row.getUserId(), key -> new ArrayList<>())
                        .add(row.getFollowUserId().toString());
            }
            for (Map.Entry<Long, List<String>> entry : grouped.entrySet()) {
                stringRedisTemplate.opsForSet()
                        .add(KEY_PREFIX + entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            stringRedisTemplate.opsForValue().set(FLAG_KEY, "1");
            log.info("[follow] 关注关系预热完成，{} 个用户 / {} 条关系", grouped.size(), rows.size());
        } catch (Exception e) {
            log.warn("[follow] 关注关系预热失败，共同关注可能为空", e);
        }
    }
}
