-- 校区
SET NAMES utf8mb4;
USE campus_life;

INSERT IGNORE INTO `tb_campus` (`id`,`name`,`city`,`address`,`longitude`,`latitude`,`status`) VALUES
(1,'豫东大学·主校区','豫东市','豫东市学府路 1 号',115.055000,30.235000,1),
(2,'豫东大学·东校区','豫东市','豫东市文华路 8 号',115.067000,30.236200,1),
(3,'豫东大学·南校区','豫东市','豫东市青禾路 6 号',115.053000,30.226500,1);

