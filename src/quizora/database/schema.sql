-- Quizora schema for SQLite 3.35+. Converted from the MySQL schema and migrations 001-004.
-- Every statement is IF NOT EXISTS: databaseConnection runs this file on startup, and it
-- never drops or alters existing tables or data.
--
-- MySQL to SQLite notes:
--   INT UNSIGNED AUTO_INCREMENT -> INTEGER PRIMARY KEY AUTOINCREMENT
--   ENUM(...)                   -> TEXT with a CHECK (... IN (...)) constraint
--   TIMESTAMP                   -> TEXT 'YYYY-MM-DD HH:MM:SS' in local time
--   ON UPDATE CURRENT_TIMESTAMP -> AFTER UPDATE triggers at the end of this file
--   utf8mb4_0900_ai_ci          -> COLLATE NOCASE on usernames, emails and subject names
-- Foreign keys are enforced because every connection enables PRAGMA foreign_keys.

CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name TEXT NOT NULL CHECK (length(full_name) <= 150),
    username TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(username) <= 50),
    email TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(email) <= 254),
    password_hash TEXT NOT NULL,
    contact_number TEXT NULL CHECK (length(contact_number) <= 50),
    profile_picture BLOB NULL,
    role TEXT NOT NULL DEFAULT 'student' CHECK (role IN ('admin', 'teacher', 'student')),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    archived_at TEXT NULL DEFAULT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS subjects (
    subject_id INTEGER PRIMARY KEY AUTOINCREMENT,
    subject_name TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(subject_name) <= 100),
    category TEXT CHECK (length(category) <= 100),
    description TEXT,
    archived_at TEXT NULL DEFAULT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS teacher_subjects (
    teacher_id INTEGER NOT NULL,
    subject_id INTEGER NOT NULL,
    PRIMARY KEY (teacher_id, subject_id),
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS quizzes (
    quiz_id INTEGER PRIMARY KEY AUTOINCREMENT,
    subject_id INTEGER NOT NULL,
    teacher_id INTEGER NOT NULL,
    title TEXT NOT NULL CHECK (length(title) <= 200),
    description TEXT,
    time_limit_minutes INTEGER NOT NULL DEFAULT 30,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'closed')),
    archived_at TEXT NULL DEFAULT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    CHECK (time_limit_minutes > 0),
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE RESTRICT,
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

-- Initial question format: four choices, one correct answer.
CREATE TABLE IF NOT EXISTS questions (
    question_id INTEGER PRIMARY KEY AUTOINCREMENT,
    quiz_id INTEGER NOT NULL,
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_answer TEXT NOT NULL CHECK (correct_answer IN ('A', 'B', 'C', 'D')),
    points INTEGER NOT NULL DEFAULT 1,
    question_order INTEGER NOT NULL,
    UNIQUE (quiz_id, question_order),
    UNIQUE (question_id, quiz_id),
    CHECK (points > 0),
    CHECK (question_order > 0),
    FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
    attempt_id INTEGER PRIMARY KEY AUTOINCREMENT,
    quiz_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    started_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    submitted_at TEXT NULL,
    status TEXT NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'submitted')),
    UNIQUE (attempt_id, quiz_id),
    CHECK (submitted_at IS NULL OR submitted_at >= started_at),
    CHECK ((status = 'in_progress' AND submitted_at IS NULL)
        OR (status = 'submitted' AND submitted_at IS NOT NULL)),
    FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS student_answers (
    answer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    attempt_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    quiz_id INTEGER NOT NULL,
    selected_answer TEXT NULL CHECK (selected_answer IN ('A', 'B', 'C', 'D')),
    UNIQUE (attempt_id, question_id),
    -- Prevent answers to questions belonging to a different quiz.
    FOREIGN KEY (attempt_id, quiz_id)
        REFERENCES quiz_attempts(attempt_id, quiz_id) ON DELETE RESTRICT,
    FOREIGN KEY (question_id, quiz_id)
        REFERENCES questions(question_id, quiz_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS quiz_results (
    result_id INTEGER PRIMARY KEY AUTOINCREMENT,
    attempt_id INTEGER NOT NULL UNIQUE,
    score INTEGER NOT NULL CHECK (score >= 0),
    total_points INTEGER NOT NULL,
    finalized_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    CHECK (total_points > 0),
    CHECK (score <= total_points),
    FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(attempt_id) ON DELETE RESTRICT
);

-- MySQL ON UPDATE CURRENT_TIMESTAMP equivalent. Skipped when the UPDATE sets
-- updated_at itself; recursive triggers are off, so the inner UPDATE does not re-fire.
CREATE TRIGGER IF NOT EXISTS users_updated_at AFTER UPDATE ON users
FOR EACH ROW WHEN NEW.updated_at IS OLD.updated_at
BEGIN
    UPDATE users SET updated_at = datetime('now', 'localtime') WHERE user_id = NEW.user_id;
END;

CREATE TRIGGER IF NOT EXISTS quizzes_updated_at AFTER UPDATE ON quizzes
FOR EACH ROW WHEN NEW.updated_at IS OLD.updated_at
BEGIN
    UPDATE quizzes SET updated_at = datetime('now', 'localtime') WHERE quiz_id = NEW.quiz_id;
END;
