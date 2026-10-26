package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stay_ratings", uniqueConstraints = @UniqueConstraint(name = "uk_stay_rating_reservation", columnNames = "reservation_id"))
public class StayRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String review;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected StayRating() {}

    public StayRating(Reservation reservation, int rating, String review) {
        this.reservation = reservation;
        this.rating = rating;
        this.review = review;
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public int getRating() { return rating; }
    public String getReview() { return review; }
    public Instant getCreatedAt() { return createdAt; }
}
