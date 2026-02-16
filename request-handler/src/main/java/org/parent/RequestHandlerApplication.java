package org.parent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.parent.handler", "org.parent.common"})
public class RequestHandlerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequestHandlerApplication.class, args);
    }
}