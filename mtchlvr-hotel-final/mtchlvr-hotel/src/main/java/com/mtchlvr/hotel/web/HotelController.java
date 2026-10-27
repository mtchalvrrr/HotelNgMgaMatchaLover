package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import com.mtchlvr.hotel.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import jakarta.servlet.http.HttpSession;

@Controller
public class HotelController {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final CaptchaService captcha;
    private final PasswordEncoder encoder;
    private final ReportRepository reports;
    private final StayRatingRepository stayRatings;

    public HotelController(RoomRepository rooms, UserRepository users, ReservationRepository reservations,
                           ReservationService reservationService, CaptchaService captcha, PasswordEncoder encoder, ReportRepository reports,
                           StayRatingRepository stayRatings) {
        this.rooms = rooms; this.users = users; this.reservations = reservations;
        this.reservationService = reservationService; this.captcha = captcha; this.encoder = encoder; this.reports = reports;
        this.stayRatings = stayRatings;
    }

    @GetMapping("/") public String home(Model model) {
        model.addAttribute("rooms", rooms.findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc());
        return "index";
    }
    @GetMapping("/rooms") public String browse(Model model) {
        model.addAttribute("rooms", rooms.findByActiveTrueOrderByTypeAscRoomNumberAsc()); return "rooms";
    }
    @GetMapping("/login") public String login(Model model) { return "login"; }

    @GetMapping("/register") public String register(Model model, HttpSession session,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        model.addAttribute("form", new RegisterForm()); return "register";
    }
    @PostMapping("/register") public String registerPost(@Valid @ModelAttribute("form") RegisterForm form,
            BindingResult errors, HttpSession session, Model model, RedirectAttributes flash,
            @RequestParam(name = "g-recaptcha-response", required = false) String captchaToken,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!captcha.verify(captchaToken, request.getRemoteAddr())) errors.reject("captcha", "Please complete the Google reCAPTCHA check and try again.");
        if (!java.util.Objects.equals(form.password, form.confirmPassword)) {
            errors.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }
        String normalizedEmail = form.email == null ? "" : form.email.trim();
        if (!normalizedEmail.isBlank() && users.existsByEmailIgnoreCase(normalizedEmail)) {
            errors.rejectValue("email", "duplicate", "An account with this email already exists.");
        }
        if (errors.hasErrors()) return "register";
        AppUser newCustomer = new AppUser(normalizedEmail.toLowerCase(java.util.Locale.ROOT),
                encoder.encode(form.password), form.fullName.trim(), "CUSTOMER", true);
        newCustomer.setCustomerCode(generateCustomerCode());
        users.save(newCustomer);
        flash.addFlashAttribute("success", "Account created successfully!");
        request.setAttribute(
                org.springframework.web.servlet.View.RESPONSE_STATUS_ATTRIBUTE,
                org.springframework.http.HttpStatus.SEE_OTHER);
        return "redirect:/";
    }
    @GetMapping("/customer/reservations") public String myReservations(Authentication auth, Model model) {
        AppUser customer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (customer.getCustomerCode() == null || customer.getCustomerCode().isBlank()) {
            customer.setCustomerCode(generateCustomerCode());
            users.save(customer);
        }
        model.addAttribute("customer", customer);
        java.util.List<Reservation> customerReservations =
                reservations.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName());
        model.addAttribute("reservations", customerReservations);
        java.util.Map<Long, StayRating> ratingsByReservationId = new java.util.HashMap<>();
        for (Reservation reservation : customerReservations) {
            stayRatings.findByReservation_Id(reservation.getId())
                    .ifPresent(rating -> ratingsByReservationId.put(reservation.getId(), rating));
        }
        model.addAttribute("ratingsByReservationId", ratingsByReservationId);
        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(hotelZone);
        java.time.LocalDateTime now =
                java.time.LocalDateTime.now(hotelZone).withNano(0);

        model.addAttribute("checkoutTodayReservations", customerReservations.stream()
                .filter(r -> "CHECKED_IN".equals(r.getStatus()))
                .filter(r -> r.getCheckOut().equals(today))
                .toList());

        model.addAttribute("earlyCheckoutReservationIds", customerReservations.stream()
                .filter(r -> "CHECKED_IN".equals(r.getStatus()))
                .filter(r -> {
                    java.time.LocalTime inTime = r.getCheckInTime() == null
                            ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
                    java.time.LocalTime outTime = r.getCheckOutTime() == null
                            ? java.time.LocalTime.NOON : r.getCheckOutTime();
                    java.time.LocalDateTime start = r.getCheckIn().atTime(inTime);
                    java.time.LocalDateTime end = r.getCheckOut().atTime(outTime);
                    return !now.isBefore(start) && now.isBefore(end);
                })
                .map(Reservation::getId)
                .toList());
        return "my-reservations";
    }
    @PostMapping("/customer/reservations") public String makeReservation(Authentication auth,
            @RequestParam Long roomId, @RequestParam String checkIn, @RequestParam String checkOut,
            @RequestParam(defaultValue = "14:00") String checkInTime,
            @RequestParam(defaultValue = "12:00") String checkOutTime,
            RedirectAttributes flash) {
        try {
            reservationService.reserve(auth.getName(), roomId,
                    LocalDate.parse(checkIn), java.time.LocalTime.parse(checkInTime),
                    LocalDate.parse(checkOut), java.time.LocalTime.parse(checkOutTime));
            flash.addFlashAttribute("success", "Reservation request submitted.");
        } catch (Exception ex) {
            flash.addFlashAttribute("error", ex.getMessage() == null
                    ? "Could not create reservation." : ex.getMessage());
        }
        return "redirect:/customer/reservations";
    }
    @PostMapping("/customer/reservations/{id}/cancel") public String cancel(@PathVariable Long id,
            Authentication auth, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found."); return "redirect:/customer/reservations";
        }
        if (!r.getStatus().equals("PENDING") && !r.getStatus().equals("APPROVED")) {
            flash.addFlashAttribute("error", "This reservation cannot be cancelled in its current status.");
        } else {
            r.setStatusBeforeCancellation(r.getStatus());
            r.setStatus("CANCELLATION_REQUESTED");
            reservations.save(r);
            flash.addFlashAttribute("success", "Cancellation request sent to the manager.");
        }
        return "redirect:/customer/reservations";
    }

    @PostMapping("/customer/reservations/{id}/early-checkout")
    public String earlyCheckout(@PathVariable Long id, Authentication auth,
                                RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);

        if (r == null || r.getCustomer() == null
                || !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found.");
            return "redirect:/customer/reservations";
        }

        if (!"CHECKED_IN".equals(r.getStatus())) {
            flash.addFlashAttribute("error",
                    "You must be checked in before you can check out early.");
            return "redirect:/customer/reservations";
        }

        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        java.time.LocalDateTime now =
                java.time.LocalDateTime.now(hotelZone).withNano(0);

        java.time.LocalTime inTime = r.getCheckInTime() == null
                ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
        java.time.LocalTime outTime = r.getCheckOutTime() == null
                ? java.time.LocalTime.NOON : r.getCheckOutTime();

        java.time.LocalDateTime scheduledStart = r.getCheckIn().atTime(inTime);
        java.time.LocalDateTime scheduledEnd = r.getCheckOut().atTime(outTime);

        if (now.isBefore(scheduledStart)) {
            flash.addFlashAttribute("error",
                    "Early checkout is available only after your scheduled check-in.");
            return "redirect:/customer/reservations";
        }

        if (!now.isBefore(scheduledEnd)) {
            flash.addFlashAttribute("error",
                    "Your scheduled checkout time has arrived. Please settle your bill at reception.");
            return "redirect:/customer/reservations";
        }

        r.setCheckOut(now.toLocalDate());
        r.setCheckOutTime(now.toLocalTime());
        r.setStatus("COMPLETED");
        reservations.save(r);

        flash.addFlashAttribute("success",
                "Early checkout recorded. Your estimated room charge has been updated. "
                + "Please settle your final bill at reception.");
        return "redirect:/customer/reservations";
    }

    @PostMapping("/customer/reservations/{id}/rating")
    public String submitStayRating(@PathVariable Long id, Authentication auth,
            @RequestParam int rating, @RequestParam(defaultValue = "") String review,
            RedirectAttributes flash) {
        Reservation reservation = reservations.findById(id).orElse(null);
        if (reservation == null || reservation.getCustomer() == null
                || !reservation.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found.");
            return "redirect:/customer/reservations";
        }
        if (!"COMPLETED".equals(reservation.getStatus())) {
            flash.addFlashAttribute("error", "You can rate your stay after checkout is completed.");
            return "redirect:/customer/reservations";
        }
        if (rating < 1 || rating > 5) {
            flash.addFlashAttribute("error", "Choose a rating from 1 to 5 stars.");
            return "redirect:/customer/reservations";
        }
        String cleanReview = review == null ? "" : review.trim();
        if (cleanReview.length() > 1000) {
            flash.addFlashAttribute("error", "Your review must be 1,000 characters or fewer.");
            return "redirect:/customer/reservations";
        }
        if (stayRatings.findByReservation_Id(id).isPresent()) {
            flash.addFlashAttribute("error", "This stay has already been rated.");
            return "redirect:/customer/reservations";
        }
        stayRatings.save(new StayRating(reservation, rating, cleanReview.isBlank() ? null : cleanReview));
        flash.addFlashAttribute("success", "Thank you for rating your stay!");
        return "redirect:/customer/reservations";
    }

    @PostMapping("/manager/reservations/{id}/check-in")
    public String checkInGuest(@PathVariable Long id, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null) {
            flash.addFlashAttribute("error", "Reservation not found.");
            return "redirect:/manager";
        }
        if (!"APPROVED".equals(r.getStatus())) {
            flash.addFlashAttribute("error", "Only approved reservations can be checked in.");
            return "redirect:/manager";
        }
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone).withNano(0);
        java.time.LocalTime checkInTime = r.getCheckInTime() == null
                ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
        java.time.LocalTime checkOutTime = r.getCheckOutTime() == null
                ? java.time.LocalTime.NOON : r.getCheckOutTime();
        java.time.LocalDateTime scheduledStart = r.getCheckIn().atTime(checkInTime);
        java.time.LocalDateTime scheduledEnd = r.getCheckOut().atTime(checkOutTime);
        if (now.isBefore(scheduledStart) || !now.isBefore(scheduledEnd)) {
            flash.addFlashAttribute("error", "Check-in is only available from the scheduled check-in time until before checkout.");
            return "redirect:/manager";
        }
        r.setStatus("CHECKED_IN");
        reservations.save(r);
        flash.addFlashAttribute("success", "Guest checked in. The reservation is now marked as CHECKED_IN.");
        return "redirect:/manager";
    }

    @PostMapping("/manager/reservations/{id}/check-out")
    public String checkOutGuest(@PathVariable Long id, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null) {
            flash.addFlashAttribute("error", "Reservation not found.");
            return "redirect:/manager";
        }
        if (!"CHECKED_IN".equals(r.getStatus())) {
            flash.addFlashAttribute("error", "Only checked-in guests can be checked out.");
            return "redirect:/manager";
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Manila")).withNano(0);
        java.time.LocalDateTime scheduledStart = r.getCheckIn().atTime(
                r.getCheckInTime() == null ? java.time.LocalTime.of(14, 0) : r.getCheckInTime());
        if (now.isBefore(scheduledStart)) {
            flash.addFlashAttribute("error", "The guest's scheduled check-in time has not arrived.");
            return "redirect:/manager";
        }
        r.setCheckOut(now.toLocalDate());
        r.setCheckOutTime(now.toLocalTime());
        r.setStatus("COMPLETED");
        reservations.save(r);
        flash.addFlashAttribute("success", "Guest checked out. The completed stay is now available for a customer rating.");
        return "redirect:/manager";
    }

    @GetMapping("/api/rooms/{roomId}/availability")
    @ResponseBody
    public java.util.List<java.util.Map<String, String>> roomAvailability(@PathVariable Long roomId) {
        return reservations.findAll().stream()
            .filter(r -> r.getRoom().getId().equals(roomId))
            .filter(r -> !java.util.List.of("REJECTED", "CANCELLED").contains(r.getStatus()))
            .map(r -> {
                java.time.LocalTime inTime = r.getCheckInTime() == null
                        ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
                java.time.LocalTime outTime = r.getCheckOutTime() == null
                        ? java.time.LocalTime.NOON : r.getCheckOutTime();
                return java.util.Map.of(
                    "checkIn", r.getCheckIn().toString(),
                    "checkOut", r.getCheckOut().toString(),
                    "checkInTime", inTime.toString(),
                    "checkOutTime", outTime.toString(),
                    "status", r.getStatus()
                );
            })
            .toList();
    }

    @GetMapping("/manager") public String manager(Model model) {
        model.addAttribute("rooms", rooms.findAll());
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        java.util.List<Reservation> occupancyReservations = reservations.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> java.util.List.of("PENDING", "APPROVED", "CHECKED_IN", "CANCELLATION_REQUESTED")
                        .contains(r.getStatus()))
                .toList();
        for (Reservation reservation : occupancyReservations) {
            AppUser customer = reservation.getCustomer();
            if (customer.getCustomerCode() == null || customer.getCustomerCode().isBlank()) {
                customer.setCustomerCode(generateCustomerCode());
                users.save(customer);
            }
        }
        model.addAttribute("occupancyReservations", occupancyReservations);
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc());
        return "manager";
    }

    @GetMapping("/manager/reservation-requests") public String managerReservationRequests(Model model) {
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        return "reservation-requests";
    }

    @GetMapping("/manager/history")
    public String managerHistory(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String year,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "newest") String sort, Model model) {
        String query = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        Integer selectedYear = null;
        if (year != null && !year.isBlank()) {
            try { selectedYear = Integer.valueOf(year); }
            catch (NumberFormatException ignored) { selectedYear = null; }
        }
        final Integer filterYear = selectedYear;
        final String filterStatus = java.util.List.of("ALL", "PENDING", "APPROVED", "CHECKED_IN",
                "COMPLETED", "REJECTED", "CANCELLED", "CANCELLATION_REQUESTED").contains(status)
                ? status : "ALL";
        java.util.List<Reservation> history = reservations.findAllByOrderByCreatedAtDesc().stream()
                .filter(Reservation::isHistoryVisible)
                .filter(r -> filterStatus.equals("ALL") || filterStatus.equals(r.getStatus()))
                .filter(r -> filterYear == null || r.getCheckIn().getYear() == filterYear)
                .filter(r -> query.isBlank() ||
                        String.valueOf(r.getId()).contains(query) ||
                        containsIgnoreCase(r.getCustomer().getFullName(), query) ||
                        containsIgnoreCase(r.getCustomer().getEmail(), query) ||
                        containsIgnoreCase(r.getCustomer().getCustomerCode(), query) ||
                        containsIgnoreCase(r.getRoom().getRoomNumber(), query) ||
                        containsIgnoreCase(r.getRoom().getType(), query))
                .sorted("oldest".equalsIgnoreCase(sort)
                        ? java.util.Comparator.comparing(Reservation::getCheckIn).thenComparing(Reservation::getId)
                        : java.util.Comparator.comparing(Reservation::getCheckIn).thenComparing(Reservation::getId).reversed())
                .toList();
        java.util.Map<Long, StayRating> ratingsByReservationId = new java.util.HashMap<>();
        for (Reservation reservation : history) {
            stayRatings.findByReservation_Id(reservation.getId())
                    .ifPresent(rating -> ratingsByReservationId.put(reservation.getId(), rating));
        }
        model.addAttribute("reservations", history);
        model.addAttribute("ratingsByReservationId", ratingsByReservationId);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("year", year == null ? "" : year);
        model.addAttribute("statusFilter", filterStatus);
        model.addAttribute("sort", "oldest".equalsIgnoreCase(sort) ? "oldest" : "newest");
        model.addAttribute("historyYears", reservations.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> r.getCheckIn().getYear()).distinct().sorted(java.util.Comparator.reverseOrder()).toList());
        return "reservation-history";
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    @GetMapping("/manager/room-management") public String roomManagement(Model model) {
        model.addAttribute("rooms", rooms.findAll());
        return "room-management";
    }

    @PostMapping("/customer/reports") public String submitReport(Authentication auth,
            @RequestParam String category, @RequestParam String message, RedirectAttributes flash) {
        String clean = message == null ? "" : message.trim();
        if (clean.isBlank() || clean.length() > 2000 || category == null ||
                !java.util.List.of("SERVICE", "ROOM", "BOOKING", "WEBSITE", "OTHER").contains(category)) {
            flash.addFlashAttribute("error", "Choose a report category and enter a message of up to 2,000 characters.");
            return "redirect:/customer/reservations#report";
        }
        AppUser customer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        reports.save(new GuestReport(customer, category, clean));
        flash.addFlashAttribute("success", "Thank you. Your report was sent to the hotel manager.");
        return "redirect:/customer/reservations#report";
    }

    @GetMapping("/manager/reports") public String managerReports(Model model) {
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc());
        return "reports";
    }

    @PostMapping("/manager/reports/{id}/status") public String updateReportStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp="OPEN|RESOLVED") String status, RedirectAttributes flash) {
        GuestReport report = reports.findById(id).orElse(null);
        if (report == null) flash.addFlashAttribute("error", "Report not found.");
        else { report.setStatus(status); reports.save(report); flash.addFlashAttribute("success", "Report status updated."); }
        return "redirect:/manager/reports";
    }

    @PostMapping("/manager/rooms/{id}/deactivate") public String deactivateRoom(@PathVariable Long id, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) {
            flash.addFlashAttribute("error", "Room not found.");
        } else if (!room.isActive()) {
            flash.addFlashAttribute("error", "This room is already inactive.");
        } else {
            room.setActive(false);
            rooms.save(room);
            flash.addFlashAttribute("success", "Room " + room.getRoomNumber() + " was deactivated and hidden from new bookings.");
        }
        return "redirect:/manager";
    }

    @PostMapping("/manager/rooms/{id}/reactivate") public String reactivateRoom(@PathVariable Long id, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) {
            flash.addFlashAttribute("error", "Room not found.");
        } else if (room.isActive()) {
            flash.addFlashAttribute("error", "This room is already active.");
        } else {
            room.setActive(true);
            rooms.save(room);
            flash.addFlashAttribute("success", "Room " + room.getRoomNumber() + " was reactivated.");
        }
        return "redirect:/manager";
    }

    @PostMapping("/manager/reservations/{id}/status") public String updateStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp="APPROVED|REJECTED|CANCELLED") String status, RedirectAttributes flash) {
        try {
            reservationService.updateStatus(id, status);
            flash.addFlashAttribute("success", "Reservation status updated.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager";
    }


    private boolean isProcessedHistory(Reservation r) {
        return r.isHistoryVisible() && !java.util.List.of("PENDING", "CANCELLATION_REQUESTED").contains(r.getStatus());
    }

    @PostMapping("/manager/history/{id}/delete")
    public String deleteHistoryRecord(@PathVariable Long id, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || !isProcessedHistory(r)) {
            flash.addFlashAttribute("error", "Processed history record not found.");
        } else {
            r.setHistoryVisible(false);
            reservations.save(r);
            flash.addFlashAttribute("success", "History record deleted.");
        }
        return "redirect:/manager/history";
    }

    @PostMapping("/manager/history/clear")
    public String clearHistory(RedirectAttributes flash) {
        java.util.List<Reservation> processed = reservations.findAllByOrderByCreatedAtDesc()
            .stream().filter(this::isProcessedHistory).toList();
        if (processed.isEmpty()) {
            flash.addFlashAttribute("error", "There are no processed history records to delete.");
        } else {
            processed.forEach(r -> r.setHistoryVisible(false));
            reservations.saveAll(processed);
            flash.addFlashAttribute("success", "Processed reservation history cleared.");
        }
        return "redirect:/manager/history";
    }


public static class RegisterForm {
    @NotBlank @Size(min=8, max=80)
    private String fullName = "";

    @NotBlank @Email @Size(max=160)
    private String email = "";

    @NotBlank
    @Size(min=10, max=70)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
        message = "Password must have uppercase and lowercase letters, a number, and a special symbol, with no spaces."
    )
    private String password = "";

    @NotBlank
    private String confirmPassword = "";

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}


    private String generateCustomerCode() {
        String code;
        do {
            int digits = java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000);
            code = String.format(java.util.Locale.ROOT, "mtchlvr-%04d-5201314", digits);
        } while (users.existsByCustomerCode(code));
        return code;
    }

}
