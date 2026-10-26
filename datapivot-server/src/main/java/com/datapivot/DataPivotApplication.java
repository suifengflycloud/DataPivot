package com.datapivot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DataPivot 启动类
 * <p>多源数据库迁移与结构对比工具后端服务，默认端口 8081。</p>
 */
@EnableScheduling
@MapperScan("com.datapivot.mapper")
@SpringBootApplication
public class DataPivotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPivotApplication.class, args);
    }
}
