
# Database Rules

## Purpose
This document defines the rules for interacting with the SQLite database of the Quiz Application System. The database is the single file database/quizora.db; its tables are defined in src/quizora/database/schema.sql, which databaseConnection applies on startup. Any future development must follow the existing database design and relationships to prevent data inconsistency and system errors.


The database supports:
- User authentication and role management
- Quiz management
- Question management
- Student assessment records
- Result tracking
- Reporting functions

Then focus on:
## Existing Database Structure

Database file:
database/quizora.db (override with -Dquizora.db=path)

The documented database consists of:
1. users
2. subjects
3. quizzes
4. questions
5. quiz_attempts
6. student_answers
7. teacher_subjects
8. quiz_results

---

## Database Access Rules

- Do not modify existing table structures without approval.
- Do not rename existing tables or columns.
- Do not remove existing relationships.
- New features must adapt to the current database design.

---

## Relationship Rules

Keep:
- A quiz must belong to an existing subject.
- A quiz must have an assigned teacher.
- Questions must belong to an existing quiz.
- Student answers must belong to an existing quiz attempt.
- A quiz result must belong to an existing quiz attempt.
- Each quiz attempt may have only one finalized quiz result.
- Teacher subject assignments must reference existing teachers and subjects.

---

## Data Handling Rules

- Always validate data before inserting.
- Always check if records exist before updating.
- Avoid duplicate records.
- Do not delete records that are connected to historical results unless properly handled.

---

## Java SQLite Connection Rules

Since the system will use JavaFX:
- Database operations must be separated from JavaFX views and presentation controllers.
- Use DAO classes for database transactions.
- Use `PreparedStatement` for SQL queries.
- Do not place SQL queries directly inside JavaFX controls, FXML controllers, or presentation controllers.
- Run database requests in a `javafx.concurrent.Task` call() method on a background thread; update controls in setOnSucceeded/setOnFailed handlers or Platform.runLater() on the JavaFX Application Thread.
- Open connections only through `databaseConnection`: `getConnection()` for writes and `getReadOnlyConnection()` for reads. Both enable foreign keys, which SQLite leaves off by default.
- Write transactions start with BEGIN IMMEDIATE, which locks the whole database. Do not use MySQL row locks (`FOR UPDATE`, `FOR SHARE`); SQLite rejects them.
- Store timestamps as local-time text with `datetime('now','localtime')`, not `CURRENT_TIMESTAMP` (UTC). Use `date('now','localtime')` for today's date and read date-only values with `LocalDate.parse(resultSet.getString(...))`.
- Detect duplicate keys with `databaseConnection.isDuplicateKey(error)`, not MySQL error code 1062.
- Change table structure only in `src/quizora/database/schema.sql`, using `CREATE ... IF NOT EXISTS`. Existing databases need a reviewed migration for any column change.
