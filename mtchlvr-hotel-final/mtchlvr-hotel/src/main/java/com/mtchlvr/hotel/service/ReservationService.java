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
    private static final LocalTime DEFAULT_CHECK_IN = LocalTime.of(14, 0);
    private static final LocalTime DEFAULT_CHECK_OUT = LocalTime.NOON;
    private static final List<String> NON_OCCUPYING = List.of("CANCELLED", "REJECTED");

    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final UserRepository users;

    public ReservationService(ReservationRepository reservations, RoomRepository rooms, UserRepository users) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.users = users;
    }

    private LocalDateTime start(Reservation r) {
        LocalTime time = r.getCheckInTime();
        return r.getCheckIn().atTime(time == null ? DEFAULT_CHECK_IN : time);
    }

    private LocalDateTime end(Reservation r) {
        LocalTime time = r.getCheckOutTime();
        return r.getCheckOut().atTime(time == null ? DEFAULT_CHECK_OUT : time);
    }

    private boolean overlaps(LocalDateTime start, LocalDateTime end, Reservation existing) {
        return start.isBefore(this.end(existing)) && end.isAfter(this.start(existing));
    }

    public boolean isAvailable(Room room, LocalDate in, LocalDate out) {
        return isAvailable(room, in, DEFAULT_CHECK_IN, out, DEFAULT_CHECK_OUT);
    }

    public boolean isAvailable(Room room, LocalDate in, LocalTime inTime,
                               LocalDate out, LocalTime outTime) {
        if (room == null || room.getId() == null || in == null || out == null ||
                inTime == null || outTime == null || in.isBefore(LocalDate.now()) ||
                !room.isAvailable() || !room.isActive()) {
            return false;
        }

        LocalDateTime requestedStart = in.atTime(inTime);
        LocalDateTime requestedEnd = out.atTime(outTime);
        if (!requestedEnd.isAfter(requestedStart)) return false;

        return reservations.findAll().stream().noneMatch(r ->
            r.getRoom().getId().equals(room.getId()) &&
            !NON_OCCUPYING.contains(r.getStatus()) &&
            overlaps(requestedStart, requestedEnd, r)
        );
    }

    @Transactional
    public Reservation updateStatus(Long reservationId, String status) {
        if (status == null || !List.of("APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Choose a valid reservation status.");
        }

        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        String currentStatus = reservation.getStatus();
        String nextStatus = status;

        if ("CANCELLATION_REQUESTED".equals(currentStatus)) {
            if ("CANCELLED".equals(status)) {
                nextStatus = "CANCELLED";
            } else if ("APPROVED".equals(status)) {
                String previousStatus = reservation.getStatusBeforeCancellation();

                // Older records have no saved prior status; preserve their
                // previous behavior by treating them as approved bookings.
                nextStatus = ("PENDING".equals(previousStatus) ||
                              "APPROVED".equals(previousStatus))
                        ? previousStatus : "APPROVED";
            } else {
                throw new IllegalArgumentException(
                    "For a cancellation request, choose keep reservation or confirm cancellation."
                );
            }
        }

        if ("APPROVED".equals(nextStatus)) {
            Room room = reservation.getRoom();
            LocalDateTime requestedStart = start(reservation);
            LocalDateTime requestedEnd = end(reservation);

            boolean conflict = reservations.findAll().stream().anyMatch(other ->
                !other.getId().equals(reservation.getId()) &&
                other.getRoom().getId().equals(room.getId()) &&
                !NON_OCCUPYING.contains(other.getStatus()) &&
                overlaps(requestedStart, requestedEnd, other)
            );

            if (conflict) {
                throw new IllegalArgumentException(
                    "Cannot approve: this room overlaps another active reservation."
                );
            }
        }

        reservation.setStatus(nextStatus);
        if ("CANCELLATION_REQUESTED".equals(currentStatus)) {
            reservation.setStatusBeforeCancellation(null);
        }
        return reservations.save(reservation);
    }

    // Compatibility overload for existing callers and tests.
    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalDate out) {
        if (in == null || out == null || !out.isAfter(in) || in.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                "Choose a valid check-in date and a check-out date after it."
            );
        }
        return reserve(email, roomId, in, DEFAULT_CHECK_IN, out, DEFAULT_CHECK_OUT);
    }

    @Transactional
    public Reservation reserve(String email, Long roomId, LocalDate in, LocalTime inTime,
                               LocalDate out, LocalTime outTime) {
        if (in == null || out == null || inTime == null || outTime == null ||
                in.isBefore(LocalDate.now()) ||
                !out.atTime(outTime).isAfter(in.atTime(inTime))) {
            throw new IllegalArgumentException(
                "Choose a valid check-in date/time and a checkout after check-in."
            );
        }

        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow();
        Room room = rooms.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found."));

        if (!isAvailable(room, in, inTime, out, outTime)) {
            throw new IllegalArgumentException(
                "That room is not available for the selected dates and times."
            );
        }

        return reservations.save(new Reservation(user, room, in, out, inTime, outTime));
    }
}
