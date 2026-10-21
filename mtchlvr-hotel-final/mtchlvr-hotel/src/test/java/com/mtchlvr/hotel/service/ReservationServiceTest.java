package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ReservationRepository reservations;
    private RoomRepository rooms;
    private UserRepository users;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rooms = mock(RoomRepository.class);
        users = mock(UserRepository.class);
        service = new ReservationService(reservations, rooms, users);
    }

    @Test
    void rejectsCheckoutOnOrBeforeCheckin() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.reserve("guest@example.com", 1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(1)));
        assertTrue(error.getMessage().contains("valid check-in"));
        verifyNoInteractions(users, rooms, reservations);
    }

    @Test
    void acceptsSameDayReservationAndLeavesItPendingForManagerApproval() {
        LocalDate today = LocalDate.now();
        AppUser customer = mock(AppUser.class);
        Room room = mock(Room.class);

        when(users.findByEmailIgnoreCase("guest@example.com"))
            .thenReturn(Optional.of(customer));
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(1L);
        when(room.isAvailable()).thenReturn(true);
        when(room.isActive()).thenReturn(true);
        when(reservations.findAll()).thenReturn(List.of());
        when(reservations.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.reserve(
            "guest@example.com", 1L, today, today.plusDays(1));

        assertEquals(today, result.getCheckIn());
        assertEquals(today.plusDays(1), result.getCheckOut());
        assertEquals("PENDING", result.getStatus());
        verify(reservations).save(any(Reservation.class));
    }

    @Test
    void rejectsApprovalWhenAnotherActiveReservationOverlaps() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7L);
        Reservation target = mock(Reservation.class);
        when(target.getId()).thenReturn(1L);
        when(target.getRoom()).thenReturn(room);
        when(target.getCheckIn()).thenReturn(LocalDate.now().plusDays(4));
        when(target.getCheckOut()).thenReturn(LocalDate.now().plusDays(8));
        Reservation overlap = mock(Reservation.class);
        when(overlap.getId()).thenReturn(2L);
        when(overlap.getRoom()).thenReturn(room);
        when(overlap.getStatus()).thenReturn("PENDING");
        when(overlap.getCheckIn()).thenReturn(LocalDate.now().plusDays(6));
        when(overlap.getCheckOut()).thenReturn(LocalDate.now().plusDays(10));
        when(reservations.findById(1L)).thenReturn(Optional.of(target));
        when(reservations.findAll()).thenReturn(List.of(target, overlap));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(1L, "APPROVED"));
        assertTrue(error.getMessage().contains("overlaps"));
        verify(reservations, never()).save(any());
    }

    @Test
    void acceptsValidStatusAndPersistsIt() {
        Reservation reservation = mock(Reservation.class);
        when(reservations.findById(9L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);
        assertSame(reservation, service.updateStatus(9L, "REJECTED"));
        verify(reservation).setStatus("REJECTED");
        verify(reservations).save(reservation);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(9L, "PENDING"));
        verifyNoInteractions(reservations);
    }
}
