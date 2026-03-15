package org.parent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"org.parent.handler", "org.parent.common"})
@EnableJpaRepositories(basePackages = "org.parent.common.repository")
@EnableRedisRepositories(basePackages = "org.parent.handler")
@EntityScan(basePackages = "org.parent.common.entity")
public class RequestHandlerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequestHandlerApplication.class, args);
    }
}