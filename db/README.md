# 数据库脚本

MySQL 8.0，库名 `campus_life`，字符集 `utf8mb4`。

## 目录

| 路径 | 作用 |
|---|---|
| `schema/campus_life_schema.sql` | 建库 + 14 张表 |
| `seed/01` ~ `seed/12` | 基础种子数据（约 9.2 万行），按编号顺序执行 |
| `seed/00_manifest.txt` | 01~12 各表行数清单 |
| `demo/13_demo_vouchers.sql` | 可选。演示用的补充券：给每家商户补券、把秒杀活动窗口开到 2029-01-01 |

## 导入顺序

```bash
# 1. 建表
mysql -uroot -p < db/schema/campus_life_schema.sql

# 2. 基础数据（按编号 01 -> 12）
mysql -uroot -p campus_life < db/seed/01_campus.sql
mysql -uroot -p campus_life < db/seed/02_shop_type.sql
mysql -uroot -p campus_life < db/seed/03_user.sql
mysql -uroot -p campus_life < db/seed/04_shop.sql
mysql -uroot -p campus_life < db/seed/05_voucher.sql
mysql -uroot -p campus_life < db/seed/06_seckill_voucher.sql
mysql -uroot -p campus_life < db/seed/07_blog.sql
mysql -uroot -p campus_life < db/seed/08_blog_comments.sql
mysql -uroot -p campus_life < db/seed/09_follow.sql
mysql -uroot -p campus_life < db/seed/10_user_info.sql
mysql -uroot -p campus_life < db/seed/11_sign.sql
mysql -uroot -p campus_life < db/seed/12_voucher_order.sql

# 3. 可选：演示数据（建议本地演示/测试时执行）
mysql -uroot -p campus_life < db/demo/13_demo_vouchers.sql
```

Windows 下用 IDEA 的 Database 控制台或 `cmd /c "mysql ... < 文件"` 逐个执行即可。

## `demo/13_demo_vouchers.sql` 做了什么

- 没有任何券的商户，补一张普通代金券
- 没有秒杀券的商户，补一张秒杀券（库存 100）
- 所有秒杀券活动窗口：开始时间设为昨天，结束时间设为 **2029-01-01**
- 全部带 `NOT EXISTS` 判断，可重复执行，不会插重

> 基础数据 `seed/06_seckill_voucher.sql` 里的活动时间是 2026 年的，导入 demo 脚本后会被统一改成 2029 年。如果只想恢复原始种子，重新导入 06 即可。

## 导入之后

启动应用，然后调一次预热接口，把秒杀券写进 Redis：

```bash
POST http://127.0.0.1:8081/admin/seckill/preheat?force=true
```

（需要登录 token；重置演示数据时加 `force=true`，会按数据库覆盖库存并清空占位集合。）