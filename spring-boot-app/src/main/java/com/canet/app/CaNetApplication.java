package com.canet.app;

import com.canet.app.config.CaNetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CaNetProperties.class)
public class CaNetApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaNetApplication.class, args);
    }
}
