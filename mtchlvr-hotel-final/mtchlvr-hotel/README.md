# mtchlvr hotel

A local hotel reservation and management starter built with Java 21, Spring Boot, SQLite, Thymeleaf, HTML, CSS, and vanilla JavaScript.

## Room inventory
The seeded inventory contains **12 rooms total: 4 room types with 3 rooms per type**:
- Standard — 3 rooms
- Deluxe — 3 rooms
- Suite — 3 rooms
- Family — 3 rooms

These are sensible starter names and can be changed in `DataSeeder.java`.

## Requirements
- JDK 21
- Maven 3.9+
- Internet connection on first build so Maven can download dependencies

## Run
From this directory:
```bash
mvn spring-boot:run
```
Open http://localhost:8080

The SQLite database is created locally at `./data/mtchlvr.db`.

## Shared sign-in and local demo manager
Customers and the manager use the **same `/login` page**. After sign-in, manager accounts go to `/manager`; customer accounts go to the customer-facing home page (or the page they originally requested).

- Email: `cedihmatchalover@gmail.com`
- Password: `5201314`

These are mock capstone demo credentials only. Do not reuse them for any real account.

**Change the demo password before using real customer information.** This is a local starter, not a production deployment.

## Google reCAPTCHA and account creation
Login and registration use Google's **reCAPTCHA v2 checkbox** (the “I'm not a robot” widget), verified on the server with Google's siteverify API. The old arithmetic CAPTCHA has been removed.

Before using login or registration, create reCAPTCHA v2 Checkbox keys at https://www.google.com/recaptcha/admin/create and configure the environment variables:

- `RECAPTCHA_SITE_KEY` — public site key used by the browser widget
- `RECAPTCHA_SECRET_KEY` — private server-side verification key; never expose it in frontend code or commit it to source control

For local development, register `localhost` as an allowed domain in the reCAPTCHA admin console. Without valid keys, verification intentionally fails instead of silently bypassing the security check. Google reCAPTCHA requires an internet connection in the browser and from the server.

## Security notes
- Passwords are BCrypt-hashed.
- Spring Security enables CSRF protection and session-based authentication.
- Manager routes require the `MANAGER` role.
- Server-side validation is used for submitted fields.
- Do not expose this local app to the internet as-is.
- Before real use, add rate limiting, secure secrets management, audit logging, stronger email/OTP abuse controls, and database backup/restore procedures.

## Included starter functionality
- Room browsing and availability search
- Registration with server-validated CAPTCHA
- Login/logout
- Customer reservation list and cancellation request
- Manager dashboard and room inventory list
- Prevents a manager from approving overlapping active reservations
- Repairs partially seeded room inventories without creating duplicate room numbers
- Role-aware shared login and stronger text/focus readability
- Seeded inventory of 12 rooms
- Minimalist green/cream visual styling

This starter intentionally keeps the code small and readable. Upgrade workflows, advanced reports, room/customer editing, and the full AI chatbot can be implemented as follow-on modules.


## Tests
Run the automated service tests and application test lifecycle with:
```bash
mvn test
```
The test suite covers Google reCAPTCHA configuration and missing-token handling, invalid booking dates, conflicting reservation approvals, valid status updates, and invalid statuses. Run `mvn test` in an environment with Maven and dependency access; the current review environment does not have Maven installed.
