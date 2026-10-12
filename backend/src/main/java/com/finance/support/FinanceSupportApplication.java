package com.finance.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinanceSupportApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceSupportApplication.class, args);
    }
}
