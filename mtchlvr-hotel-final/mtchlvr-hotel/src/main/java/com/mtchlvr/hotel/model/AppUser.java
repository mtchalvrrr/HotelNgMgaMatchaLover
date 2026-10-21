package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "customer_code")
    private String customerCode;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String role = "CUSTOMER";
    @Column(nullable = false)
    private boolean verified = false;
    private String otpHash;
    private Instant otpExpiresAt;

    protected AppUser() {}
    public AppUser(String email, String passwordHash, String fullName, String role, boolean verified) {
        this.email = email; this.passwordHash = passwordHash; this.fullName = fullName;
        this.role = role; this.verified = verified;
    }
    public Long getId() { return id; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String v) { customerCode = v; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public boolean isVerified() { return verified; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public void setOtpHash(String v) { otpHash = v; }
    public void setOtpExpiresAt(Instant v) { otpExpiresAt = v; }
    public void setVerified(boolean v) { verified = v; }
}
