package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
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
    // Nullable so existing SQLite reservations remain valid after the schema update.
    @Column(name = "check_in_time") private LocalTime checkInTime;
    @Column(name = "check_out_time") private LocalTime checkOutTime;
    @Column(nullable = false) private String status = "PENDING";
    @Column(name = "status_before_cancellation")
    private String statusBeforeCancellation;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    // Soft-hide a processed record from manager history without deleting the reservation itself.
    @Column private Boolean historyVisible = true;
    @Column(name = "actual_check_in_at") private LocalDateTime actualCheckInAt;
    @Column(name = "actual_check_out_at") private LocalDateTime actualCheckOutAt;
    @Column(name = "stay_rating") private Integer stayRating;
    @Column(name = "stay_review", length = 1000) private String stayReview;

    protected Reservation() {}
    public Reservation(AppUser customer, Room room, LocalDate checkIn, LocalDate checkOut) {
        this(customer, room, checkIn, checkOut, LocalTime.of(14, 0), LocalTime.NOON);
    }
    public Reservation(AppUser customer, Room room, LocalDate checkIn, LocalDate checkOut,
                       LocalTime checkInTime, LocalTime checkOutTime) {
        this.customer = customer; this.room = room;
        this.checkIn = checkIn; this.checkOut = checkOut;
        this.checkInTime = checkInTime; this.checkOutTime = checkOutTime;
    }
    public Long getId() { return id; }
    public AppUser getCustomer() { return customer; }
    public Room getRoom() { return room; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate value) { checkOut = value; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public void setCheckInTime(LocalTime value) { checkInTime = value; }
    public void setCheckOutTime(LocalTime value) { checkOutTime = value; }
    public long getNights() {
        long dateNights =
                java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);

        if (dateNights > 0) return dateNights;
        if (dateNights < 0) return 0;

        // Same-day stays are charged at least one standard nightly rate,
        // but only when the checkout time is later than check-in.
        LocalTime start = checkInTime == null ? LocalTime.of(14, 0) : checkInTime;
        LocalTime end = checkOutTime == null ? LocalTime.NOON : checkOutTime;
        return end.isAfter(start) ? 1 : 0;
    }
    public java.math.BigDecimal getTotalCost() {
        return room.getNightlyRate().multiply(java.math.BigDecimal.valueOf(getNights()));
    }
    public String getStatus() { return status; }
    public String getStatusBeforeCancellation() { return statusBeforeCancellation; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDateTime getActualCheckInAt() { return actualCheckInAt; }
    public LocalDateTime getActualCheckOutAt() { return actualCheckOutAt; }
    public Integer getStayRating() { return stayRating; }
    public String getStayReview() { return stayReview; }
    public void setActualCheckInAt(LocalDateTime value) { actualCheckInAt = value; }
    public void setActualCheckOutAt(LocalDateTime value) { actualCheckOutAt = value; }
    public void setStayRating(Integer value) { stayRating = value; }
    public void setStayReview(String value) { stayReview = value; }
    public boolean isHistoryVisible() { return historyVisible == null || historyVisible; }
    public void setHistoryVisible(boolean visible) { historyVisible = visible; }
    public void setStatus(String v) { status = v; }
    public void setStatusBeforeCancellation(String v) { statusBeforeCancellation = v; }
}
