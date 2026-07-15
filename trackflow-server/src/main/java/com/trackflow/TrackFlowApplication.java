package com.trackflow;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrackFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackFlowApplication.class, args);
    }
}
