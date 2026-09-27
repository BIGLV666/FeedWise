package com.feedwise;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** FeedWise 启动入口。 */
@SpringBootApplication
@MapperScan("com.feedwise.mapper")
@ConfigurationPropertiesScan
public class FeedWiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedWiseApplication.class, args);
    }
}
