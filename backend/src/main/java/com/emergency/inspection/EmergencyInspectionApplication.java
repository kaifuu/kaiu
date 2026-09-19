package com.emergency.inspection;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.emergency.inspection.mapper")
public class EmergencyInspectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmergencyInspectionApplication.class, args);
    }
}
