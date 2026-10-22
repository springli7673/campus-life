-- 商户类型
SET NAMES utf8mb4;
USE campus_life;

INSERT IGNORE INTO `tb_shop_type` (`id`,`name`,`icon`,`sort`) VALUES
(1,'食堂档口','/imgs/campus/type/canteen.png',1),
(2,'校园超市','/imgs/campus/type/market.png',2),
(3,'文印中心','/imgs/campus/type/print.png',3),
(4,'健身房','/imgs/campus/type/gym.png',4),
(5,'理发店','/imgs/campus/type/hair.png',5),
(6,'咖啡店','/imgs/campus/type/coffee.png',6),
(7,'奶茶店','/imgs/campus/type/tea.png',7),
(8,'快递驿站','/imgs/campus/type/express.png',8),
(9,'水果店','/imgs/campus/type/fruit.png',9),
(10,'自习室','/imgs/campus/type/study.png',10),
(11,'洗衣房','/imgs/campus/type/laundry.png',11),
(12,'药店','/imgs/campus/type/drug.png',12);

