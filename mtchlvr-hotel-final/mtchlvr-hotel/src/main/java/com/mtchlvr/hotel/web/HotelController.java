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

    public HotelController(RoomRepository rooms, UserRepository users, ReservationRepository reservations,
                           ReservationService reservationService, CaptchaService captcha, PasswordEncoder encoder, ReportRepository reports) {
        this.rooms = rooms; this.users = users; this.reservations = reservations;
        this.reservationService = reservationService; this.captcha = captcha; this.encoder = encoder; this.reports = reports;
    }

    @GetMapping("/") public String home(Model model) {
        model.addAttribute("rooms", rooms.findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc());
        return "index";
    }
    @GetMapping("/rooms") public String browse(Model model) {
        model.addAttribute("rooms", rooms.findByActiveTrueOrderByTypeAscRoomNumberAsc()); return "rooms";
    }
    @GetMapping("/api/rooms/{roomId}/availability")
    @ResponseBody
    public java.util.List<java.util.Map<String, String>> roomAvailability(@PathVariable Long roomId) {
        return reservations.findAll().stream()
            .filter(r -> r.getRoom().getId().equals(roomId))
            .filter(r -> !java.util.List.of("REJECTED", "CANCELLED").contains(r.getStatus()))
            .map(r -> java.util.Map.of(
                "checkIn", r.getCheckIn().toString(),
                "checkOut", r.getCheckOut().toString(),
                "checkInTime", r.getCheckInTime() == null ? "14:00" : r.getCheckInTime().toString(),
                "checkOutTime", r.getCheckOutTime() == null ? "12:00" : r.getCheckOutTime().toString(),
                "status", r.getStatus()))
            .toList();
    }

    @GetMapping("/login") public String login(Model model) { return "login"; }

    @GetMapping("/register") public String register(Model model, HttpSession session) {
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
        flash.addFlashAttribute("success", "Account created. You can now sign in.");
        return "redirect:/login";
    }
    @GetMapping("/customer/reservations") public String myReservations(Authentication auth, Model model) {
        // MTCHLVR_CHECKOUT_COMPLETION
        // Finish approved stays after the checkout date has passed.
        for (com.mtchlvr.hotel.model.Reservation stay :
                reservations.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName())) {
            if ("APPROVED".equals(stay.getStatus())
                    && stay.getCheckOut() != null
                    && stay.getCheckOut().isBefore(java.time.LocalDate.now())) {
                stay.setStatus("COMPLETED");
                reservations.save(stay);
            }
        }
        // END MTCHLVR_CHECKOUT_COMPLETION

        model.addAttribute("customer", users.findByEmailIgnoreCase(auth.getName()).orElseThrow());
        model.addAttribute("reservations", reservations.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName()));
        return "my-reservations";
    }
    @PostMapping("/customer/reservations") public String makeReservation(Authentication auth,
            @RequestParam Long roomId, @RequestParam String checkIn, @RequestParam String checkInTime,
            @RequestParam String checkOut, @RequestParam String checkOutTime,
            RedirectAttributes flash) {
        try {
            reservationService.reserve(auth.getName(), roomId,
                LocalDate.parse(checkIn), java.time.LocalTime.parse(checkInTime),
                LocalDate.parse(checkOut), java.time.LocalTime.parse(checkOutTime));
            flash.addFlashAttribute("success", "Reservation request submitted.");
        } catch (Exception ex) {
            flash.addFlashAttribute("error", ex.getMessage() == null ? "Could not create reservation." : ex.getMessage());
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
            r.setStatus("CANCELLATION_REQUESTED"); reservations.save(r);
            flash.addFlashAttribute("success", "Cancellation request sent to the manager.");
        }
        return "redirect:/customer/reservations";
    }

    @GetMapping("/manager") public String manager(Model model) {
        model.addAttribute("rooms", rooms.findAll());
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        model.addAttribute("occupancyReservations", reservations.findAllByOrderByCreatedAtDesc()
            .stream()
            .filter(r -> !java.util.List.of("REJECTED", "CANCELLED").contains(r.getStatus()))
            .toList());
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc());
        return "manager";
    }

    @GetMapping("/manager/reservation-requests") public String managerReservationRequests(Model model) {
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        return "reservation-requests";
    }

    @GetMapping("/manager/history") public String managerHistory(Model model) {
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc().stream().filter(Reservation::isHistoryVisible).toList());
        return "reservation-history";
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
