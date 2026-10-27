package com.mtchlvr.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MtchlvrHotelApplication {
    public static void main(String[] args) {
        java.nio.file.Path dbDir = java.nio.file.Path.of("data");
        try { java.nio.file.Files.createDirectories(dbDir); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot create local data directory", e); }
        SpringApplication.run(MtchlvrHotelApplication.class, args);
    }
}
