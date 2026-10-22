-- 对账修正库存：只有当前值仍是观测值时才覆盖，避免踩掉并发预扣
-- KEYS[1] 库存 key；ARGV[1] 观测到的旧值；ARGV[2] 修正后的新值
-- 返回 1 修正成功，0 期间被并发修改（放弃本次修正，下一轮再对）
local current = redis.call('get', KEYS[1])
if current == ARGV[1] then
    redis.call('set', KEYS[1], ARGV[2])
    return 1
end
return 0
