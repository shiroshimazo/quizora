# Gmail password recovery

Quizora sends verification email through `smtp.gmail.com`, port `587`, with
mandatory STARTTLS and certificate hostname verification. The subject is
`Your Verification Code`; the plain-text body includes the six-digit code and
states that it expires in five minutes.

## Local configuration

Enable Google 2-Step Verification and create a Gmail app password. Do not use
your normal Google account password. Some managed accounts do not allow app
passwords; ask your administrator if the option is unavailable.

Set these environment variables in the process that launches Quizora:

- `QUIZORA_SMTP_USERNAME`: the sending Gmail/Google Workspace email address.
- `QUIZORA_SMTP_APP_PASSWORD`: that account's app password.

For a temporary PowerShell session, enter the password without putting it into
shell history or displaying it:

```powershell
$env:QUIZORA_SMTP_USERNAME = Read-Host 'Sending Gmail address'
$gmailSecret = Read-Host 'Gmail app password' -AsSecureString
$env:QUIZORA_SMTP_APP_PASSWORD = [System.Net.NetworkCredential]::new('', $gmailSecret).Password
try {
    powershell -ExecutionPolicy Bypass -File .\run.ps1
} finally {
    Remove-Item Env:\QUIZORA_SMTP_APP_PASSWORD -ErrorAction SilentlyContinue
    $gmailSecret.Dispose()
}
```

NetBeans must inherit the same environment variables if you launch the app from
the IDE. No credentials belong in Java files, FXML, project properties, or Git.
The app does not load `.env` files.

## Recovery behavior

Use an email belonging to an active, non-archived account in Quizora. There are
no preview codes. Delivery runs off the JavaFX thread. Missing credentials,
authentication errors, or network failures produce a friendly retry message;
the UI never claims that a failed email was sent.

Each successfully sent code lasts exactly 300 seconds after SMTP accepts it.
Inbox arrival can be delayed by the mail provider; SMTP acceptance is the send
boundary, not the time the recipient opens the email. The visible times use the
computer's local time zone. Expiry is checked against the stored absolute UTC
deadline, including when saving a new password. A suspended UI cannot extend it.

Resend has a 30-second cooldown. A new accepted request immediately invalidates
the previous challenge, even if the new delivery fails. A late completion of an
older send cannot replace the latest challenge. Only hashes, not plaintext
codes, are stored in SQLite. Five incorrect attempts require another code.
A verified challenge is single-use and must still be unexpired when the password
is saved. Passwords must contain 8-128 characters and match confirmation.

The `password_recovery` table is created automatically without changing existing
user records. Closing the form stops its timer; sending a new request supersedes
any remaining challenge. All app instances sharing the same SQLite database
share this challenge state.

## Checks

Run `powershell -ExecutionPolicy Bypass -File .\test-recovery.ps1`.
Tests use temporary databases, fake SMTP delivery, and a controllable clock.
They cover the exact expiry boundary, resend replacement, failed sending,
late SMTP completion, attempt limits, password updates, visible times, countdown,
and expired/resend UI states. No test email is sent.

Live delivery still requires a locally configured Gmail account and a registered
recipient you are authorized to email.

## Bundled dependencies

Ant packages these JARs from `lib/` into `dist/lib/`:

- `org.eclipse.angus:jakarta.mail:2.0.5` (Jakarta Mail API and SMTP implementation)
- `jakarta.activation:jakarta.activation-api:2.1.3`
- `org.eclipse.angus:angus-activation:2.0.2`

Downloaded from Maven Central; downloads were checked against the repository's
published checksums. License notices are included in the JARs under `META-INF`.

References: [Google SMTP setup](https://support.google.com/a/answer/176600),
[Google app passwords](https://support.google.com/accounts/answer/185833),
[Angus SMTP properties](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html).
