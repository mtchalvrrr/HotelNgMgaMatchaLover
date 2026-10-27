package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "guest_reports")
public class GuestReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private AppUser customer;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected GuestReport() {}
    public GuestReport(AppUser customer, String category, String message) {
        this.customer = customer;
        this.category = category;
        this.message = message;
    }
    public Long getId() { return id; }
    public AppUser getCustomer() { return customer; }
    public String getCategory() { return category; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
}