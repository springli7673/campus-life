package com.li.campuslife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.li.campuslife.mapper")
@SpringBootApplication
public class CampusLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLifeApplication.class, args);
    }

}