package com.main.aqarpaymentbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AqarPaymentBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AqarPaymentBackendApplication.class, args);
    }

}
