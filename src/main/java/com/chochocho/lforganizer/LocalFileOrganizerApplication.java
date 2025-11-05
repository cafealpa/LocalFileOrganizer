package com.chochocho.lforganizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LocalFileOrganizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalFileOrganizerApplication.class, args);
    }

}
