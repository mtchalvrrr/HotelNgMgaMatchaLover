document.addEventListener("DOMContentLoaded", () => {
  const today = new Date();
  const localDate = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  document.querySelectorAll('input[name="checkIn"]').forEach((input) => {
    input.min = localDate;
    input.addEventListener("change", () => {
      const out = input.closest("form")?.querySelector('input[name="checkOut"]');
      if (out) { out.min = input.value || localDate; if (out.value && out.value <= input.value) out.value = ""; }
    });
  });
  document.querySelectorAll('input[name="checkOut"]').forEach((input) => input.min = localDate);

  document.querySelectorAll("[data-password-toggle]").forEach((button) => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.dataset.passwordToggle);
      if (!input) return;
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      button.textContent = visible ? "Hide" : "Show";
      button.setAttribute("aria-label", visible ? "Hide password" : "Show password");
      button.setAttribute("aria-pressed", String(visible));
    });
  });

  const modal = document.getElementById("confirm-modal");
  if (modal) {
    const message = modal.querySelector("[data-confirm-message]");
    const cancel = modal.querySelector("[data-confirm-cancel]");
    const proceed = modal.querySelector("[data-confirm-proceed]");
    let pendingForm = null;
    document.querySelectorAll("form[data-confirm]").forEach(form => form.addEventListener("submit", event => {
      if (form.dataset.confirmed === "true") { form.dataset.confirmed = "false"; return; }
      event.preventDefault();
      pendingForm = form;
      message.textContent = form.dataset.confirm || "Are you sure you want to continue?";
      modal.hidden = false;
      cancel.focus();
    }));
    const close = () => { modal.hidden = true; pendingForm = null; };
    cancel.addEventListener("click", close);
    modal.addEventListener("click", event => { if (event.target === modal) close(); });
    proceed.addEventListener("click", () => {
      if (!pendingForm) return;
      const form = pendingForm;
      close();
      form.dataset.confirmed = "true";
      form.requestSubmit();
    });
    document.addEventListener("keydown", event => { if (event.key === "Escape" && !modal.hidden) close(); });
  }
});

// MTCHLVR_DATE_TIME_RESERVATIONS
document.addEventListener("DOMContentLoaded", () => {
  const now = new Date();
  const localDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString().slice(0, 10);

  document.querySelectorAll(".booking-form").forEach((form) => {
    const inDate = form.querySelector('[name="checkIn"]');
    const inTime = form.querySelector('[name="checkInTime"]');
    const outDate = form.querySelector('[name="checkOut"]');
    const outTime = form.querySelector('[name="checkOutTime"]');
    if (!inDate || !inTime || !outDate || !outTime) return;

    inDate.min = localDate;
    outDate.min = localDate;

    const validate = () => {
      outDate.min = inDate.value || localDate;
      const start = inDate.value && inTime.value ? `${inDate.value}T${inTime.value}` : "";
      const end = outDate.value && outTime.value ? `${outDate.value}T${outTime.value}` : "";
      const invalid = start && end && end <= start;
      outDate.setCustomValidity(invalid ? "Check-out must be later than check-in." : "");
      outTime.setCustomValidity(invalid ? "Choose a checkout time after check-in." : "");
    };

    [inDate, inTime, outDate, outTime].forEach((input) => {
      input.addEventListener("change", validate);
    });
    form.addEventListener("submit", (event) => {
      validate();
      if (!form.reportValidity()) event.preventDefault();
    });
  });
});
// END MTCHLVR_DATE_TIME_RESERVATIONS


/* MTCHLVR_OCCUPIED_TIME_LOG_V2
   Display each room's occupied date/time log without hiding room cards. */
document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll(".room-availability[data-room-id]").forEach(function (panel) {
        const roomId = panel.getAttribute("data-room-id");
        const list = panel.querySelector("[data-availability-list]");
        if (!roomId || !list) return;

        list.replaceChildren();

        function addMessage(message) {
            const item = document.createElement("li");
            item.textContent = message;
            list.appendChild(item);
        }

        function dateLabel(value) {
            if (!value) return "Unknown date";
            const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})$/);
            return match ? `${match[2]}/${match[3]}/${match[1]}` : String(value);
        }

        function timeLabel(value, fallback) {
            const raw = value || fallback;
            if (!raw) return "time not recorded";
            const match = String(raw).match(/^(\d{1,2}):(\d{2})/);
            if (!match) return String(raw);
            let hour = Number(match[1]);
            const suffix = hour >= 12 ? "PM" : "AM";
            hour = hour % 12 || 12;
            return `${hour}:${match[2]} ${suffix}`;
        }

        fetch(`/api/rooms/${encodeURIComponent(roomId)}/availability`, {
            headers: { "Accept": "application/json" }
        })
        .then(function (response) {
            if (!response.ok) throw new Error("Could not load availability");
            return response.json();
        })
        .then(function (records) {
            list.replaceChildren();

            if (!Array.isArray(records)) {
                addMessage("Availability information is temporarily unavailable.");
                return;
            }

            const occupied = records.filter(function (record) {
                const status = String(record.status || "").toUpperCase();
                return status !== "REJECTED" && status !== "CANCELLED";
            });

            occupied.sort(function (a, b) {
                return String(a.checkIn || "").localeCompare(String(b.checkIn || "")) ||
                    String(a.checkInTime || "14:00").localeCompare(String(b.checkInTime || "14:00"));
            });

            if (occupied.length === 0) {
                addMessage("No occupied dates currently recorded.");
                return;
            }

            occupied.forEach(function (record) {
                const status = String(record.status || "RESERVED")
                    .replaceAll("_", " ")
                    .toLowerCase()
                    .replace(/\b\w/g, function (letter) { return letter.toUpperCase(); });

                addMessage(
                    `${dateLabel(record.checkIn)} ${timeLabel(record.checkInTime, "14:00")} – ` +
                    `${dateLabel(record.checkOut)} ${timeLabel(record.checkOutTime, "12:00")} · ${status}`
                );
            });
        })
        .catch(function () {
            list.replaceChildren();
            addMessage("Could not load occupied times. Please refresh the page.");
        });
    });
});



/* MTCHLVR_RESERVATION_STATUS_LABELS_V1
   Room cards stay visible. Reservation records lock only their time ranges. */
document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll(".room-availability[data-room-id]").forEach(function (panel) {
        const roomId = panel.getAttribute("data-room-id");
        const list = panel.querySelector("[data-availability-list]");
        if (!roomId || !list) return;

        function addLine(message) {
            const item = document.createElement("li");
            item.textContent = message;
            list.appendChild(item);
        }

        function dateLabel(value) {
            const match = String(value || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
            return match ? `${match[2]}/${match[3]}/${match[1]}` : (value || "Unknown date");
        }

        function timeLabel(value, fallback) {
            const match = String(value || fallback || "").match(/^(\d{1,2}):(\d{2})/);
            if (!match) return value || fallback || "time not recorded";
            let hour = Number(match[1]);
            const suffix = hour >= 12 ? "PM" : "AM";
            hour = hour % 12 || 12;
            return `${hour}:${match[2]} ${suffix}`;
        }

        function statusLabel(status) {
            switch (String(status || "").toUpperCase()) {
                case "PENDING":
                    return "Reserved — Awaiting Manager Review";
                case "APPROVED":
                    return "Reserved — Approved";
                case "CANCELLATION_REQUESTED":
                    return "Reserved — Cancellation Under Review";
                default:
                    return "Reserved — " + String(status || "Under Review").replaceAll("_", " ");
            }
        }

        fetch(`/api/rooms/${encodeURIComponent(roomId)}/availability`, {
            headers: { "Accept": "application/json" }
        })
        .then(function (response) {
            if (!response.ok) throw new Error("Availability request failed");
            return response.json();
        })
        .then(function (records) {
            list.replaceChildren();

            if (!Array.isArray(records)) {
                addLine("Availability could not be loaded. Please refresh.");
                return;
            }

            // The API excludes rejected/cancelled reservations.
            const occupied = records.filter(function (r) {
                return !["REJECTED", "CANCELLED"].includes(
                    String(r.status || "").toUpperCase()
                );
            });

            occupied.sort(function (a, b) {
                return String(a.checkIn || "").localeCompare(String(b.checkIn || "")) ||
                    String(a.checkInTime || "14:00").localeCompare(String(b.checkInTime || "14:00"));
            });

            if (occupied.length === 0) {
                addLine("Available — no reserved times recorded.");
                return;
            }

            occupied.forEach(function (r) {
                addLine(
                    `${dateLabel(r.checkIn)} ${timeLabel(r.checkInTime, "14:00")} – ` +
                    `${dateLabel(r.checkOut)} ${timeLabel(r.checkOutTime, "12:00")} | ` +
                    statusLabel(r.status)
                );
            });
        })
        .catch(function () {
            list.replaceChildren();
            addLine("Could not load reservation times. Please refresh the page.");
        });
    });
});
