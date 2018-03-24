package com.findr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * This class tells spring where to start. No need to change anything here. The @EnableScheduling annotation
 * will tell spring to run any classes with functions with @Scheduled in intervals. They're placed in the "scheduled"
 * package.
 */
@SpringBootApplication
@EnableScheduling
public class FindrApplication {

    public static void main(String[] args) {
        SpringApplication.run(FindrApplication.class, args);
    }
}
