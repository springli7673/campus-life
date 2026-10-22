-- 秒杀资格校验 + 预扣（原子）
-- ARGV[1] voucherId, ARGV[2] userId
-- 返回：0 成功 / 1 库存不足 / 2 重复下单 / 3 未开始 / 4 已结束 / 5 活动不存在
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local activityKey = 'seckill:activity:' .. voucherId

local beginTime = redis.call('hget', activityKey, 'begin')
local endTime = redis.call('hget', activityKey, 'end')
if (not beginTime) or (not endTime) then
    return 5
end

-- 用 Redis 服务器时间，避免应用时钟漂移
local now = tonumber(redis.call('TIME')[1])
if now < tonumber(beginTime) then
    return 3
end
if now > tonumber(endTime) then
    return 4
end

local stock = redis.call('get', stockKey)
if not stock then
    return 5
end
if tonumber(stock) <= 0 then
    return 1
end

if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0