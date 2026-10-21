package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private AppUser customer;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Room room;
    @Column(nullable = false) private LocalDate checkIn;
    @Column(nullable = false) private LocalDate checkOut;
    @Column(nullable = false) private String status = "PENDING";
    @Column(nullable = false) private Instant createdAt = Instant.now();
    // Soft-hide a processed record from manager history without deleting the reservation itself.
    @Column private Boolean historyVisible = true;

    protected Reservation() {}
    public Reservation(AppUser customer, Room room, LocalDate checkIn, LocalDate checkOut) {
        this.customer = customer; this.room = room; this.checkIn = checkIn; this.checkOut = checkOut;
    }
    public Long getId() { return id; }
    public AppUser getCustomer() { return customer; }
    public Room getRoom() { return room; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public long getNights() {
        return java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
    }
    public java.math.BigDecimal getTotalCost() {
        return room.getNightlyRate().multiply(java.math.BigDecimal.valueOf(getNights()));
    }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isHistoryVisible() { return historyVisible == null || historyVisible; }
    public void setHistoryVisible(boolean visible) { historyVisible = visible; }
    public void setStatus(String v) { status = v; }
}
