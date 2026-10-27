package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;

@Configuration
public class DataSeeder {
    @Bean CommandLineRunner seed(RoomRepository rooms, UserRepository users, PasswordEncoder encoder) {
        return args -> {
            // Seed each missing room independently so a partially populated database is repaired.
            addType(rooms, "Standard", "1", new BigDecimal("1800"), 2);
            addType(rooms, "Deluxe", "2", new BigDecimal("2600"), 2);
            addType(rooms, "Suite", "3", new BigDecimal("4200"), 2);
            addType(rooms, "Family", "4", new BigDecimal("3500"), 4);
            if (!users.existsByEmailIgnoreCase("cedihmatchalover@gmail.com")) {
                users.save(new AppUser("cedihmatchalover@gmail.com", encoder.encode("5201314"),
                    "Hotel Manager", "MANAGER", true));
            }
        };
    }
    private void addType(RoomRepository rooms, String type, String prefix, BigDecimal rate, int capacity) {
        for (int i = 1; i <= 3; i++) {
            String number = prefix + "0" + i;
            if (!rooms.existsByRoomNumber(number)) {
                rooms.save(new Room(number, type, rate, capacity));
            }
        }
    }
}
