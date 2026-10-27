package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rooms")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false, unique = true)
    private String roomNumber;
    @NotBlank @Column(nullable = false)
    private String type;
    @NotNull @DecimalMin("0.00") @Column(nullable = false)
    private BigDecimal nightlyRate;
    @Min(1) @Max(10) @Column(nullable = false)
    private int capacity = 2;
    @Column(nullable = false)
    private boolean available = true;
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    protected Room() {}
    public Room(String roomNumber, String type, BigDecimal nightlyRate, int capacity) {
        this.roomNumber = roomNumber; this.type = type; this.nightlyRate = nightlyRate; this.capacity = capacity;
    }
    public Long getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public String getType() { return type; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public int getCapacity() { return capacity; }
    public boolean isAvailable() { return available; }
    public boolean isActive() { return active; }
    public void setRoomNumber(String v) { roomNumber = v; }
    public void setType(String v) { type = v; }
    public void setNightlyRate(BigDecimal v) { nightlyRate = v; }
    public void setCapacity(int v) { capacity = v; }
    public void setAvailable(boolean v) { available = v; }
    public void setActive(boolean v) { active = v; }
}
