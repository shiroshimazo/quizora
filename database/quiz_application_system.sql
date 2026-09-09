-- Quizora initial schema: MySQL 8.0.16+ (enforced CHECK constraints).
-- Intended for a fresh database. Never drops or alters existing tables/data.
CREATE DATABASE IF NOT EXISTS quiz_application_system
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE quiz_application_system;

CREATE TABLE IF NOT EXISTS users (
    user_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    contact_number VARCHAR(50) NULL,
    profile_picture MEDIUMBLOB NULL,
    role ENUM('admin', 'teacher', 'student') NOT NULL DEFAULT 'student',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    archived_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS subjects (
    subject_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    subject_name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100),
    description TEXT,
    archived_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_subjects (
    teacher_id INT UNSIGNED NOT NULL,
    subject_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (teacher_id, subject_id),
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS quizzes (
    quiz_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    subject_id INT UNSIGNED NOT NULL,
    teacher_id INT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    time_limit_minutes INT UNSIGNED NOT NULL DEFAULT 30,
    status ENUM('draft', 'published', 'closed') NOT NULL DEFAULT 'draft',
    archived_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (time_limit_minutes > 0),
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE RESTRICT,
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Initial question format: four choices, one correct answer.
CREATE TABLE IF NOT EXISTS questions (
    question_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    quiz_id INT UNSIGNED NOT NULL,
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_answer ENUM('A', 'B', 'C', 'D') NOT NULL,
    points INT UNSIGNED NOT NULL DEFAULT 1,
    question_order INT UNSIGNED NOT NULL,
    UNIQUE (quiz_id, question_order),
    UNIQUE (question_id, quiz_id),
    CHECK (points > 0),
    CHECK (question_order > 0),
    FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS quiz_attempts (
    attempt_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    quiz_id INT UNSIGNED NOT NULL,
    student_id INT UNSIGNED NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP NULL,
    status ENUM('in_progress', 'submitted') NOT NULL DEFAULT 'in_progress',
    UNIQUE (attempt_id, quiz_id),
    CHECK (submitted_at IS NULL OR submitted_at >= started_at),
    CHECK ((status = 'in_progress' AND submitted_at IS NULL)
        OR (status = 'submitted' AND submitted_at IS NOT NULL)),
    FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_answers (
    answer_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    attempt_id INT UNSIGNED NOT NULL,
    question_id INT UNSIGNED NOT NULL,
    quiz_id INT UNSIGNED NOT NULL,
    selected_answer ENUM('A', 'B', 'C', 'D') NULL,
    UNIQUE (attempt_id, question_id),
    -- Prevent answers to questions belonging to a different quiz.
    FOREIGN KEY (attempt_id, quiz_id)
        REFERENCES quiz_attempts(attempt_id, quiz_id) ON DELETE RESTRICT,
    FOREIGN KEY (question_id, quiz_id)
        REFERENCES questions(question_id, quiz_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS quiz_results (
    result_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    attempt_id INT UNSIGNED NOT NULL UNIQUE,
    score INT UNSIGNED NOT NULL,
    total_points INT UNSIGNED NOT NULL,
    finalized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (total_points > 0),
    CHECK (score <= total_points),
    FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(attempt_id) ON DELETE RESTRICT
) ENGINE=InnoDB;
