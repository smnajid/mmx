package com.mmx.order.adapter.out.persistence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mmx.order.adapter.out.persistence")
class PersistenceTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersistenceTestApplication.class, args);
    }
}
