## Project Title
Quiz Application System

## Overview

The Quiz Application System is a desktop-based application developed to automate the process of creating, managing, and taking quizzes. The system aims to replace traditional paper-based assessments by providing a digital platform where administrators, teachers, and students can efficiently manage and conduct quizzes.

The system provides different features based on user roles. Administrators manage users, quizzes, subjects, accounts, and system records. Teachers create and manage quizzes, questions, and monitor student performance. Students can access available quizzes, complete assessments, and view their results.

The system uses a database to securely store user information, quiz questions, answers, and assessment results. Through automation, the system improves the accuracy of scoring, reduces manual work, and provides a more organized assessment process.

---

# User Roles and Functions

## Administrator

The administrator is responsible for managing the overall system operations and maintaining system data.

### Features:
- Login authentication
- Manage student accounts
  - Add students
  - View student records
  - Update student information
  - Delete student records
- Manage teacher accounts
  - Add teachers
  - View teacher records
  - Update teacher information
  - Delete teacher records
- Manage quizzes and questions
  - Add quizzes and questions
  - View quiz information
  - Update quiz details
  - Delete quizzes and questions
- Manage subjects and categories
  - Add subjects/categories
  - View subjects/categories
  - Update subject information
  - Delete subjects/categories
- View reports
  - Quiz statistics
  - Number of students
  - Number of teachers
- View student quiz results
- Manage system accounts and user roles

---
## Teacher

The teacher is responsible for creating quizzes, managing questions, and monitoring student performance.
### Features:
- Login authentication
- Create quizzes
- Add, edit, and delete quiz questions
- Manage assigned subjects
- View student quiz results
- View quiz statistics

---
## Student

The student uses the system to take quizzes and monitor personal performance.

### Features:
- Login authentication
- View available quizzes
- Take quizzes
- Submit quiz answers
- View quiz scores
- View previous quiz results

---

# System Goals

The Quiz Application System aims to:

- Provide a digital platform for conducting quizzes and assessments.
- Reduce the time required for quiz preparation, checking, and record management.
- Automate quiz scoring to improve accuracy and reduce human errors.
- Provide organized management of users, questions, subjects, and results.
- Improve accessibility and convenience for administrators, teachers, and students.
- Maintain secure and reliable storage of quiz-related information.

---

# Technology Overview

## Application Type
- Desktop-based application

## User Interface
- JavaFX desktop UI
- FXML for declarative screen layouts
- CSS for shared colors, typography, spacing, and interaction states

## Programming Language
- Java 26

## Database
- MySQL / SQLite

## Supported Platform
- Windows 10/11 (64-bit)

## Main System Components
- User Authentication Module
- User Management Module
- Quiz Management Module
- Question Management Module
- Result Management Module
- Reporting Module
- Database Management Module

[[System Architecture.md]]
[[System UI Rules.md]]
