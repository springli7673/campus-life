-- 投递失败时回滚预扣
-- ARGV[1] voucherId, ARGV[2] userId
redis.call('incrby', 'seckill:stock:' .. ARGV[1], 1)
redis.call('srem', 'seckill:order:' .. ARGV[1], ARGV[2])
return 0