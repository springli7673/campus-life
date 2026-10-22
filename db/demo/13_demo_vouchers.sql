-- ============================================================
-- 演示数据补充：让每家商户都有券可测
--   1) 没有任何券的商户，补一张普通代金券
--   2) 没有秒杀券的商户，补一张秒杀券（库存 100）
--   3) 所有秒杀券的活动窗口统一打开（现在 ~ 30 天后）
-- 可重复执行：全部带 NOT EXISTS 判断，不会重复插入
-- ============================================================
USE campus_life;

-- 1. 给没有任何券的商户补一张普通代金券
DROP TEMPORARY TABLE IF EXISTS tmp_shop_no_voucher;
CREATE TEMPORARY TABLE tmp_shop_no_voucher (id BIGINT PRIMARY KEY);
INSERT INTO tmp_shop_no_voucher (id)
SELECT s.id
FROM tb_shop s
WHERE NOT EXISTS (SELECT 1 FROM tb_voucher v WHERE v.shop_id = s.id);

INSERT INTO tb_voucher (shop_id, title, sub_title, rules, pay_value, actual_value, type, status)
SELECT s.id,
       CONCAT(GREATEST(ROUND(s.avg_price), 10), ' 元代金券'),
       '到店出示即可',
       '不可与其他优惠叠加',
       GREATEST(ROUND(s.avg_price), 10) * 80,
       GREATEST(ROUND(s.avg_price), 10) * 100,
       0, 1
FROM tb_shop s
JOIN tmp_shop_no_voucher t ON t.id = s.id;

-- 2. 给没有秒杀券的商户补一张秒杀券
DROP TEMPORARY TABLE IF EXISTS tmp_shop_no_seckill;
CREATE TEMPORARY TABLE tmp_shop_no_seckill (id BIGINT PRIMARY KEY);
INSERT INTO tmp_shop_no_seckill (id)
SELECT s.id
FROM tb_shop s
WHERE NOT EXISTS (SELECT 1 FROM tb_voucher v WHERE v.shop_id = s.id AND v.type = 1);

SET @base := (SELECT COALESCE(MAX(id), 0) FROM tb_voucher);

INSERT INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status)
SELECT @base + ROW_NUMBER() OVER (ORDER BY s.id),
       s.id,
       CONCAT(GREATEST(ROUND(s.avg_price), 10), ' 元秒杀券'),
       '限量 100 张，先到先得',
       '不可与其他优惠叠加',
       GREATEST(ROUND(s.avg_price), 10) * 50,
       GREATEST(ROUND(s.avg_price), 10) * 100,
       1, 1
FROM tb_shop s
JOIN tmp_shop_no_seckill t ON t.id = s.id;

INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time)
SELECT v.id, 100,
       DATE_SUB(NOW(), INTERVAL 1 DAY),
       '2029-01-01 00:00:00'
FROM tb_voucher v
WHERE v.id > @base
  AND v.type = 1
  AND NOT EXISTS (SELECT 1 FROM tb_seckill_voucher sv WHERE sv.voucher_id = v.id);

-- 3. 打开所有秒杀券的活动窗口
UPDATE tb_seckill_voucher
SET begin_time = DATE_SUB(NOW(), INTERVAL 1 DAY),
    end_time   = '2029-01-01 00:00:00';
