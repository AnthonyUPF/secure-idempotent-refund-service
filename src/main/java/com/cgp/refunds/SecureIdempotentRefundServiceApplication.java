package com.cgp.refunds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SecureIdempotentRefundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                SecureIdempotentRefundServiceApplication.class,
                args
        );
    }
}
