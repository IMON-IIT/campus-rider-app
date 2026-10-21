package com.jusmartride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JUSmartRideApplication {

    public static void main(String[] args) {
        SpringApplication.run(JUSmartRideApplication.class, args);
    }
}
