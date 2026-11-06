package com.eventra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class EvenTraApplication {

    public static void main(String[] args) {
        // Enforce UTC timezone globally so PostgreSQL driver and database accept timestamp connections across all platforms
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(EvenTraApplication.class, args);
    }
}


