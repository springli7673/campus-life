-- ============================================================
-- campus-life 数据库结构
-- 目标实例：127.0.0.1:3306（Windows 服务 MySQL80，版本 8.0.45）
--   ⚠ 不要连到 WSL 里的 hmall-mysql 容器，那是另一个实例
--   ⚠ 脚本只在 campus_life 库里执行，不碰 hmdp / sky_take_out / tlias 等老库
-- 执行方式：在 IDEA Database 或命令行里整段运行
-- ============================================================

CREATE DATABASE IF NOT EXISTS `campus_life`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `campus_life`;

-- ============================================================
-- 一、新增：校区
-- ============================================================
DROP TABLE IF EXISTS `tb_campus`;
CREATE TABLE `tb_campus` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '校区名称，如 豫东大学·主校区',
  `city` varchar(64) NOT NULL COMMENT '所在城市',
  `address` varchar(255) NOT NULL COMMENT '详细地址',
  `longitude` double NOT NULL COMMENT '经度（仅用于距离计算）',
  `latitude` double NOT NULL COMMENT '纬度',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='校区';

-- ============================================================
-- 二、原 11 张表（结构小改）
-- ============================================================

-- 2.1 用户：增加 campus_id、student_no
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) NOT NULL COMMENT '手机号码',
  `password` varchar(128) NULL DEFAULT '' COMMENT '密码，MD5 存储',
  `nick_name` varchar(32) NULL DEFAULT '' COMMENT '昵称',
  `icon` varchar(255) NULL DEFAULT '' COMMENT '头像',
  `campus_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '所属校区',
  `student_no` varchar(32) NULL DEFAULT NULL COMMENT '学号',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniqe_key_phone`(`phone`) USING BTREE,
  INDEX `idx_campus`(`campus_id`) USING BTREE,
  UNIQUE INDEX `uk_student_no`(`student_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';

-- 2.2 用户资料
DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info` (
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，用户id',
  `city` varchar(64) NULL DEFAULT '' COMMENT '城市',
  `introduce` varchar(128) NULL DEFAULT NULL COMMENT '个人介绍',
  `fans` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '粉丝数',
  `followee` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '关注数',
  `gender` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '0 男，1 女',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `credits` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '积分',
  `level` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '会员级别 0~9',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户资料';

-- 2.3 商户类型
DROP TABLE IF EXISTS `tb_shop_type`;
CREATE TABLE `tb_shop_type` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NULL DEFAULT NULL COMMENT '类型名称',
  `icon` varchar(255) NULL DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED NULL DEFAULT NULL COMMENT '排序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商户类型';

-- 2.4 商户：增加 campus_id
DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '商户名称',
  `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '类型id',
  `images` varchar(1024) NOT NULL COMMENT '图片，多张逗号分隔',
  `area` varchar(128) NULL DEFAULT NULL COMMENT '校内区域，如 一食堂/图书馆/南门商业街',
  `address` varchar(255) NOT NULL COMMENT '地址',
  `x` double NOT NULL COMMENT '经度',
  `y` double NOT NULL COMMENT '纬度',
  `avg_price` bigint(10) UNSIGNED NULL DEFAULT NULL COMMENT '人均，单位分',
  `sold` int(10) UNSIGNED ZEROFILL NOT NULL COMMENT '销量',
  `comments` int(10) UNSIGNED ZEROFILL NOT NULL COMMENT '评论数',
  `score` int(2) UNSIGNED ZEROFILL NOT NULL COMMENT '评分，乘 10 保存',
  `open_hours` varchar(32) NULL DEFAULT NULL COMMENT '营业时间',
  `campus_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '所属校区',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `foreign_key_type`(`type_id`) USING BTREE,
  INDEX `idx_campus`(`campus_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商户';

-- 2.5 探店笔记
DROP TABLE IF EXISTS `tb_blog`;
CREATE TABLE `tb_blog` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) NOT NULL COMMENT '商户id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `images` varchar(2048) NOT NULL COMMENT '图片，最多9张，逗号分隔',
  `content` varchar(2048) NOT NULL COMMENT '正文',
  `liked` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '点赞数',
  `comments` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '评论数',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_shop`(`shop_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='探店笔记';

-- 2.6 笔记评论
DROP TABLE IF EXISTS `tb_blog_comments`;
CREATE TABLE `tb_blog_comments` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '笔记id',
  `parent_id` bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '一级评论id，0 表示本身是一级',
  `answer_id` bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '回复的评论id',
  `content` varchar(255) NOT NULL COMMENT '内容',
  `liked` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '点赞数',
  `status` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '0 正常，1 被举报，2 禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog`(`blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='笔记评论';

-- 2.7 关注
DROP TABLE IF EXISTS `tb_follow`;
CREATE TABLE `tb_follow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) UNSIGNED NOT NULL COMMENT '被关注的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_follow`(`user_id`,`follow_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='关注';

-- 2.8 签到
DROP TABLE IF EXISTS `tb_sign`;
CREATE TABLE `tb_sign` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `year` year NOT NULL COMMENT '年',
  `month` tinyint(2) NOT NULL COMMENT '月',
  `date` date NOT NULL COMMENT '日期',
  `is_backup` tinyint(1) UNSIGNED NULL DEFAULT NULL COMMENT '是否补签',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_date`(`user_id`,`date`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签到';

-- 2.9 优惠券
DROP TABLE IF EXISTS `tb_voucher`;
CREATE TABLE `tb_voucher` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '商户id',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `sub_title` varchar(255) NULL DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) NULL DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint(10) UNSIGNED NOT NULL COMMENT '支付金额，单位分',
  `actual_value` bigint(10) NOT NULL COMMENT '抵扣金额，单位分',
  `type` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 普通券，1 秒杀券',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 上架，2 下架，3 过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop`(`shop_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='优惠券';

-- 2.10 秒杀券（修正了原表 0000-00-00 的默认值，MySQL 8 严格模式会报错）
DROP TABLE IF EXISTS `tb_seckill_voucher`;
CREATE TABLE `tb_seckill_voucher` (
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '优惠券id',
  `stock` int(8) NOT NULL COMMENT '库存',
  `begin_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `end_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '失效时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`voucher_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='秒杀券';

-- 2.11 订单：唯一索引（一人一单的最终约束）+ cancel_time + 关单扫描索引
DROP TABLE IF EXISTS `tb_voucher_order`;
CREATE TABLE `tb_voucher_order` (
  `id` bigint(20) NOT NULL COMMENT '主键（RedisIdWorker 生成）',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '优惠券id',
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 余额，2 支付宝，3 微信',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 未支付，2 已支付，3 已核销，4 已取消，5 退款中，6 已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `cancel_time` timestamp NULL DEFAULT NULL COMMENT '取消时间（超时关单写入）',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_voucher`(`user_id`,`voucher_id`) USING BTREE,
  INDEX `idx_status_create`(`status`,`create_time`) USING BTREE,
  INDEX `idx_voucher`(`voucher_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='优惠券订单';

-- ============================================================
-- 三、新增：可靠性支撑表
-- ============================================================

-- 3.1 异常订单（DLQ 落地，供人工/定时补偿）
DROP TABLE IF EXISTS `tb_order_exception`;
CREATE TABLE `tb_order_exception` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint(20) NOT NULL COMMENT '订单id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '优惠券id',
  `reason` varchar(512) NOT NULL COMMENT '失败原因',
  `payload` text NULL COMMENT '消息体原文',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0 OPEN，1 REPAIRED，2 IGNORED',
  `retry_count` int(8) UNSIGNED NOT NULL DEFAULT 0 COMMENT '已补偿次数',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order`(`order_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='异常订单';

-- 3.2 对账日志
DROP TABLE IF EXISTS `tb_reconcile_log`;
CREATE TABLE `tb_reconcile_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '优惠券id',
  `db_sold` int(10) NOT NULL COMMENT 'DB 已售（未取消订单数）',
  `redis_stock` int(10) NOT NULL COMMENT 'Redis 剩余库存',
  `diff` int(10) NOT NULL COMMENT '差异值',
  `detail` varchar(512) NULL DEFAULT NULL COMMENT '差异明细',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_voucher_time`(`voucher_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对账日志';

-- ============================================================
-- 四、自检
-- ============================================================
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'campus_life'
ORDER BY TABLE_NAME;
