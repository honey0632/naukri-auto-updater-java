package com.honey.naukri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NaukriAutoUpdaterApplication {
    public static void main(String[] args) {
        SpringApplication.run(NaukriAutoUpdaterApplication.class, args);
    }
}
