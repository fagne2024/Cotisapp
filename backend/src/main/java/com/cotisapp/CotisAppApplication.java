package com.cotisapp;

import com.cotisapp.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StorageProperties.class)
public class CotisAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(CotisAppApplication.class, args);
    }
}
