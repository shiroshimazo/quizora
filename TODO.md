# Password recovery

## Completed

- [x] Centered three-step JavaFX form with independent password visibility buttons.
- [x] Registered, active account lookup and account-not-found dialog.
- [x] Gmail SMTP at smtp.gmail.com:587 with mandatory STARTTLS and hostname verification.
- [x] Random six-digit codes, a clear email subject, and a five-minute expiry notice.
- [x] Hashed challenges in SQLite; new requests invalidate previous codes atomically.
- [x] Five-minute validity measured after successful SMTP delivery; reject at the exact expiry boundary.
- [x] Sent time, expiry time, and a live countdown updated every second.
- [x] Disable verification at expiry and offer Resend Code.
- [x] Thirty-second resend cooldown and five verification attempts per challenge.
- [x] Friendly incorrect-code, expired-code, delivery-failure, and storage-failure messages.
- [x] Verified password reset using the existing password hasher; consume each challenge once.
- [x] Background lookup, SMTP, verification, and password updates.
- [x] Regression checks with fake delivery/time and an isolated SQLite database.

## Remaining setup and follow-up

- [ ] Configure QUIZORA_SMTP_USERNAME and QUIZORA_SMTP_APP_PASSWORD locally; see SMTP_SETUP.md.
- [ ] Verify live Gmail delivery to an authorized test account after credentials are configured.
- [ ] Connect the account-not-found dialog's Sign up button when the registration screen exists.
