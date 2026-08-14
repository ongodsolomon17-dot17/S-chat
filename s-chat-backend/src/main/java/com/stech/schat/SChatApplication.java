package com.stech.schat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // powers AuthRateLimiter's idle-bucket sweep and StatusService's expired-post cleanup
public class SChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(SChatApplication.class, args);
    }
}
