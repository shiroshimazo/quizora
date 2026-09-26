# Quizora SQLite database

Quizora stores its data in one SQLite file, `database/quizora.db`. The app creates
it on first run. You do not need a database server.

| Setting | Value |
| --- | --- |
| File | database/quizora.db, relative to the working directory |
| Override | -Dquizora.db=C:/path/to/file.db |
| Schema | src/quizora/database/schema.sql (packaged in the JAR) |
| JDBC driver | lib/sqlite-jdbc-3.53.4.0.jar |

The file is listed in `.gitignore`, so each machine keeps its own copy. To start
over, close the app and delete `database/quizora.db`. The next run creates an
empty database.

## First-time setup

1. Build and run the app once (`run.ps1` or NetBeans F6). This creates the tables.
2. Create the demo accounts. In NetBeans, open `CreateDemoAccounts.java` and choose
   Run File. Or, after building:

   ```text
   java --enable-native-access=ALL-UNNAMED -cp "dist/quizora.jar;dist/lib/*" quizora.database.CreateDemoAccounts
   ```

To check the connection, open `databaseConnection.java` and choose Run File. Expected
output:

```text
Connected to C:\...\quizora\database\quizora.db on SQLite 3.53.4 (3 users)
```

To browse or edit the data by hand, open the file in
[DB Browser for SQLite](https://sqlitebrowser.org/dl/). Close the app first,
or at least don't write from both at once. SQLite allows only one writer at a time.

## Tables and design choices

| Table | Purpose |
| --- | --- |
| users | Administrator, teacher, and student accounts; unique username/email |
| subjects | Subjects and optional category labels |
| teacher_subjects | Unique teacher/subject assignments |
| quizzes | Quiz metadata, subject, responsible teacher, publication status |
| questions | Four-choice questions, correct answer, points, ordering |
| quiz_attempts | Student attempts and submission state |
| student_answers | One answer per question per attempt; NULL means unanswered |
| quiz_results | One finalized score per attempt |

Four-choice, single-answer questions are the initial implementation assumption.
Categories are optional text on subjects; repeated attempts are allowed.
Composite foreign keys ensure answers belong to the same quiz as the attempt.
Foreign keys restrict deletion of referenced records to preserve history.
Store encoded, salted password hashes in password_hash, never plaintext passwords.

The schema was converted from the original MySQL schema and migrations 001-004:

- `ENUM` columns became `TEXT` with `CHECK (... IN (...))` constraints.
- Timestamps are local-time text (`2026-09-26 14:05:00`).
- `updated_at` on users and quizzes is maintained by triggers, replacing MySQL
  `ON UPDATE CURRENT_TIMESTAMP`.
- Usernames, emails, and subject names use `COLLATE NOCASE`, so uniqueness and
  login stay case-insensitive as they were under MySQL.

Future application services must validate teacher/student roles, teacher subject
permissions, quiz availability, timing and attempt limits. They must calculate
scores and finalize answers, attempt state, and results in one transaction.
Published questions must not be changed once attempts exist without a versioning
strategy. These cross-table workflow rules are not implemented by this schema.

## Java connection

`databaseConnection.getConnection()` opens a read/write connection and
`getReadOnlyConnection()` opens one that rejects writes. Both turn on foreign keys
and wait up to 10 seconds when the database is busy. Write transactions start with
`BEGIN IMMEDIATE`, which locks the whole database until commit; this replaces the
MySQL `FOR UPDATE` / `FOR SHARE` row locks. Use connections in DAOs with
try-with-resources and run database work off the JavaFX application thread.

The driver loads a native library, so the JVM needs
`--enable-native-access=ALL-UNNAMED`. It is set in `run.jvmargs` and `run.ps1`.

## Login and local demo accounts

Login accepts a username or email, checks the password and is_active flag, and
routes using the database role. There is no user-selected role on the login form.

| Role | Username | Email | Local demo password |
| --- | --- | --- | --- |
| Administrator | admin | admin@quizora.local | Admin@123 |
| Teacher | teacher | teacher@quizora.local | Teacher@123 |
| Student | student | student@quizora.local | Student@123 |

`CreateDemoAccounts` skips existing identities without resetting passwords or roles.
These published credentials are for local development only.
Passwords use salted PBKDF2-HMAC-SHA256 with 600,000 iterations, following the
[OWASP PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2).
The stored format is pbkdf2-sha256$iterations$base64Salt$base64Hash;
plaintext and malformed hashes are rejected.
