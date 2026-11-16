-- 商品订单：主表 + 明细
USE campus_life;

CREATE TABLE IF NOT EXISTS `tb_item_order` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`     VARCHAR(64)  NOT NULL COMMENT '业务订单号',
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `total_amount` INT NOT NULL DEFAULT 0 COMMENT '单位：分',
  `status`       TINYINT NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time`     DATETIME NULL DEFAULT NULL,
  `cancel_time`  DATETIME NULL DEFAULT NULL,
  `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品订单';

CREATE TABLE IF NOT EXISTS `tb_item_order_detail` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`   VARCHAR(64) NOT NULL,
  `item_id`    BIGINT UNSIGNED NOT NULL,
  `item_name`  VARCHAR(200) NOT NULL,
  `item_image` VARCHAR(255) DEFAULT NULL,
  `price`      INT NOT NULL COMMENT '下单时单价，单位分',
  `quantity`   INT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品订单明细';
