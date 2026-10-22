-- 固定窗口限流（Redis 侧原子）
-- KEYS[1] 计数 key；ARGV[1] 窗口内允许次数；ARGV[2] 窗口秒数
-- 返回 1 放行，0 拒绝
local current = redis.call('incr', KEYS[1])
if current == 1 then
    redis.call('expire', KEYS[1], ARGV[2])
elseif redis.call('ttl', KEYS[1]) < 0 then
    -- key 被意外清掉 TTL（或持久化恢复）时补上，避免永久限流
    redis.call('expire', KEYS[1], ARGV[2])
end
if current > tonumber(ARGV[1]) then
    return 0
end
return 1
