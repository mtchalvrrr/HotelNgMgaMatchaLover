package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final UserRepository users;

    public ReservationService(ReservationRepository reservations, RoomRepository rooms, UserRepository users) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
    }

    // Existing records without saved times use conventional hotel hours.
    private LocalTime inTime(Reservation r) {
        return r.getCheckInTime() == null ? LocalTime.of(14, 0) : r.getCheckInTime();
    }

    private LocalTime outTime(Reservation r) {
        return r.getCheckOutTime() == null ? LocalTime.of(12, 0) : r.getCheckOutTime();
    }

    private LocalDateTime start(Reservation r) {
        return LocalDateTime.of(r.getCheckIn(), inTime(r));
    }

    private LocalDateTime end(Reservation r) {
        return LocalDateTime.of(r.getCheckOut(), outTime(r));
    }

    private boolean overlaps(LocalDateTime start, LocalDateTime end, Reservation r) {
        return start.isBefore(end(r)) && end.isAfter(start(r));
    }

    public boolean isAvailable(Room room, LocalDate in, LocalTime inAt,
                               LocalDate out, LocalTime outAt) {
        if (room == null || room.getId() == null || in == null || out == null ||
                inAt == null || outAt == null || !room.isAvailable() || !room.isActive()) {
            return false;
        }

        LocalDateTime start = LocalDateTime.of(in, inAt);
        LocalDateTime end = LocalDateTime.of(out, outAt);
        if (!end.isAfter(start) || start.isBefore(LocalDateTime.now())) return false;

        return reservations.findAll().stream().noneMatch(r ->
            r.getRoom().getId().equals(room.getId()) &&
            !List.of("CANCELLED", "REJECTED").contains(r.getStatus()) &&
            overlaps(start, end, r)
        );
    }

    // Retain compatibility for any existing code using date-only reservations.
    public boolean isAvailable(Room room, LocalDate in, LocalDate out) {
        return isAvailable(room, in, LocalTime.of(14, 0), out, LocalTime.of(12, 0));
    }

    @Transactional
    public Reservation updateStatus(Long reservationId, String status) {
        if (status == null || !List.of("APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Choose a valid reservation status.");
        }

        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if ("APPROVED".equals(status)) {
            LocalDateTime start = start(reservation);
            LocalDateTime end = end(reservation);
            boolean conflict = reservations.findAll().stream().anyMatch(other ->
                !other.getId().equals(reservation.getId()) &&
                other.getRoom().getId().equals(reservation.getRoom().getId()) &&
                !List.of("CANCELLED", "REJECTED").contains(other.getStatus()) &&
                overlaps(start, end, other)
            );
            if (conflict) {
                throw new IllegalArgumentException("Cannot approve: this room overlaps another active reservation.");
            }
        }

        reservation.setStatus(status);
        return reservations.save(reservation);
    }

    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalDate out) {
        return reserve(email, roomId, in, LocalTime.of(14, 0), out, LocalTime.of(12, 0));
    }

    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalTime inAt,
                               LocalDate out, LocalTime outAt) {
        if (in == null || out == null || inAt == null || outAt == null) {
            throw new IllegalArgumentException("Choose check-in and check-out dates and times.");
        }

        LocalDateTime start = LocalDateTime.of(in, inAt);
        LocalDateTime end = LocalDateTime.of(out, outAt);
        if (!end.isAfter(start) || start.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Check-out must be later than check-in, and check-in cannot be in the past.");
        }

        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow();
        Room room = rooms.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found."));

        if (!isAvailable(room, in, inAt, out, outAt)) {
            throw new IllegalArgumentException("That room is not available for the selected dates and times.");
        }

        Reservation reservation = new Reservation(user, room, in, out);
        reservation.setCheckInTime(inAt);
        reservation.setCheckOutTime(outAt);
        return reservations.save(reservation);
    }
}
