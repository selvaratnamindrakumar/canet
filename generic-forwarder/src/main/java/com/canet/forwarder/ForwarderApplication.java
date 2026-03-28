package com.canet.forwarder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.canet.forwarder.config.ForwarderProperties;

@SpringBootApplication
@EnableConfigurationProperties(ForwarderProperties.class)
public class ForwarderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForwarderApplication.class, args);
    }
}
