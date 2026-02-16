package org.parent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.parent.build", "org.parent.common"})
public class BuildServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BuildServiceApplication.class, args);
    }
}