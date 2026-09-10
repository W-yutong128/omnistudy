package com.omnistudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OmnistudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmnistudyApplication.class, args);
    }
}
