# Quizora MySQL setup

The initial schema is in [quiz_application_system.sql](quiz_application_system.sql).
It was imported into the local MySQL 8.4.3 server.

| Setting | Value |
| --- | --- |
| Host | localhost |
| Port | 3306 |
| Database | quiz_application_system |
| User | root |
| Password | empty string |
| JDBC driver | lib/mysql-connector-j-9.7.0.jar |

## Import on another machine

Requires MySQL 8.0.16 or later for enforced CHECK constraints.
Start MySQL, then open its command-line client:

```text
mysql --host=localhost --port=3306 --user=root
```

At the MySQL prompt run (adjust the project path if needed):

```sql
SOURCE C:/Users/Jeremy/Documents/NetBeansProjects/quizora/database/quiz_application_system.sql;
SHOW TABLES FROM quiz_application_system;
```

Alternatively, open the SQL file in MySQL Workbench and execute it.
The script uses CREATE IF NOT EXISTS and contains no DROP or ALTER statements.
It can be rerun for the same schema but does not upgrade or validate pre-existing
tables with different definitions. Inspect those definitions before adapting them.

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
The schema itself seeds no accounts. The explicit CreateDemoAccounts utility has
created the three local demo accounts listed below; there are no sample quizzes.

Future application services must validate teacher/student roles, teacher subject
permissions, quiz availability, timing and attempt limits. They must calculate
scores and finalize answers, attempt state, and results in one transaction.
Published questions must not be changed once attempts exist without a versioning
strategy. These cross-table workflow rules are not implemented by this schema.

## Java connection

databaseConnection.getConnection() opens a fresh JDBC connection and propagates
SQLException to its caller. Use it in DAOs with try-with-resources and run database
work off the JavaFX application thread. The existing panels remain presentation
shells with a live administrator overview; login and role routing are implemented,
while CRUD behavior is pending.

## Login and local demo accounts

Run the normal main class, quizora.Quizora, in NetBeans. Login accepts a username
or email, checks the password and is_active flag, and routes using the database
role. There is no user-selected role on the login form.

| Role | Username | Email | Local demo password |
| --- | --- | --- | --- |
| Administrator | admin | admin@quizora.local | Admin@123 |
| Teacher | teacher | teacher@quizora.local | Teacher@123 |
| Student | student | student@quizora.local | Student@123 |

On a fresh database, run quizora.database.CreateDemoAccounts once (Run File),
or use the built artifact:

```text
java -cp "dist/quizora.jar;dist/lib/*" quizora.database.CreateDemoAccounts
```

This utility skips existing identities without resetting passwords or roles.
These published credentials are for local development only.
Passwords use salted PBKDF2-HMAC-SHA256 with 600,000 iterations, following the
[OWASP PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2).
The stored format is pbkdf2-sha256$iterations$base64Salt$base64Hash;
plaintext and malformed hashes are rejected.

userDAO uses a parameterized query; AuthenticationService verifies credentials.
Database and hashing work run in a JavaFX Task, with a busy state and inline
validation/error messages. An in-memory session holds only the user ID, name,
and role. Logout clears it and opens a fresh login form. The admin dashboard shows
live KPIs and charts; other feature pages remain blank. Registration and password
reset are still unimplemented.

LoginSmokeTest passed against MySQL and the actual JavaFX form for all three
roles, including username/email login, role-specific menus, blank content,
password visibility, busy state, logout, invalid credentials, inactive accounts,
SQL-injection input, and unauthenticated route rejection. Its inactive fixture
is deleted after the test. Demo accounts remain available.

The connector is configured on the Ant classpath and copied into dist/lib by
the jar build. JavaFX remains on the module path. Connection handling follows
the [MySQL DriverManager documentation](https://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-connect-drivermanager.html).

To check connectivity in NetBeans, open databaseConnection.java and choose
Run File. Expected output:

```text
Connected to quiz_application_system on MySQL 8.4.3
```

Or after building, from the project directory with Java on PATH:

```text
java -cp "dist/quizora.jar;dist/lib/*" quizora.database.databaseConnection
```

## Verification

Ant jar build and connectivity through the packaged JAR passed.
DatabaseSmokeTest exercises all eight tables in one transaction and checks
duplicate answers/results, cross-quiz answers, invalid scores, and deletion
of referenced quizzes. Fixture rows are rolled back; auto-increment sequences
can still advance.

```text
javac -cp "build/classes;lib/*" -d build/test/classes test/quizora/database/DatabaseSmokeTest.java
java -cp "build/classes;build/test/classes;lib/*" quizora.database.DatabaseSmokeTest
```
