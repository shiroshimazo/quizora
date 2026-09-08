
# Database Rules

## Purpose
This document defines the rules for interacting with the existing MySQL database of the Quiz Application System. The database structure has already been implemented. Any future development must follow the existing database design and relationships to prevent data inconsistency and system errors.


The database supports:
- User authentication and role management
- Quiz management
- Question management
- Student assessment records
- Result tracking
- Reporting functions

Then focus on:
## Existing Database Structure

Name of database:
quiz_application_system

The current database consists of:
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

## Java MySQL Connection Rules

Since the system uses JavaFX with FXML:
- Database operations must be separated from JavaFX controllers and FXML views.
- Use DAO classes for database transactions.
- Use `PreparedStatement` for SQL queries.
- Do not place SQL queries directly inside JavaFX controls or controllers.
