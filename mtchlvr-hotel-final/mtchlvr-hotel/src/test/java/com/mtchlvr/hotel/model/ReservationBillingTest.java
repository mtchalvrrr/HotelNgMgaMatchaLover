package com.mtchlvr.hotel.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationBillingTest {

    @Test
    void validSameDayStayChargesOneNight() {
        Room room = mock(Room.class);
        when(room.getNightlyRate()).thenReturn(new BigDecimal("1800.00"));
        LocalDate date = LocalDate.of(2026, 10, 22);

        Reservation reservation = new Reservation(
            mock(AppUser.class), room, date, date,
            LocalTime.of(10, 0), LocalTime.of(18, 0)
        );

        assertEquals(1L, reservation.getNights());
        assertEquals(new BigDecimal("1800.00"), reservation.getTotalCost());
    }

    @Test
    void multiNightStayKeepsCalendarNightCalculation() {
        Room room = mock(Room.class);
        when(room.getNightlyRate()).thenReturn(new BigDecimal("1800.00"));
        LocalDate checkIn = LocalDate.of(2026, 10, 22);

        Reservation reservation = new Reservation(
            mock(AppUser.class), room, checkIn, checkIn.plusDays(2),
            LocalTime.of(14, 0), LocalTime.NOON
        );

        assertEquals(2L, reservation.getNights());
        assertEquals(new BigDecimal("3600.00"), reservation.getTotalCost());
    }
}
