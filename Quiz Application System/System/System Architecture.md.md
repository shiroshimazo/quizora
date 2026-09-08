# System Architecture

## Overview

The Quiz Application System follows a three-layer architecture that separates the user interface, system processes, and data storage. This structure improves system organization, maintainability, and reliability by allowing each component to perform specific functions.

The system is developed as a desktop-based Java 26 application. JavaFX provides the presentation runtime, FXML defines screen layouts, and CSS provides the shared visual design system. A database securely stores user accounts, quiz information, questions, and assessment results.

---

# Architecture Components

## 1. Presentation Layer (User Interface)

The Presentation Layer serves as the interaction between users and the system. JavaFX controllers load FXML views and apply shared CSS styling, then provide different interfaces depending on the user's role.

### Administrator Interface
Allows administrators to:
- Login to the system
- Manage student and teacher accounts
- Manage quizzes and questions
- Manage subjects and categories
- View reports and quiz statistics
- View student results
- Manage user roles and accounts

### Teacher Interface
Allows teachers to:
- Login to the system
- Create and manage quizzes
- Add, edit, and delete quiz questions
- Manage assigned subjects
- View student results
- Monitor quiz statistics

### Student Interface
Allows students to:
- Login to the system
- View available quizzes
- Take quizzes
- Submit answers
- View scores and previous results

---

# 2. Application Layer (Business Logic)

The Application Layer handles the main operations and processing of the system. It manages user requests, applies system rules, and communicates between the user interface and database.

### Main Functions:

## Authentication Module
- Validates user login credentials.
- Identifies user roles such as administrator, teacher, and student.
- Controls access based on assigned permissions.

## User Management Module
- Handles adding, viewing, updating, and deleting student and teacher accounts.
- Manages user roles and account information.

## Quiz Management Module
- Allows teachers and administrators to create, update, and delete quizzes.
- Organizes quiz information based on subjects and categories.

## Question Management Module
- Handles storing, updating, and deleting quiz questions.
- Ensures questions are properly associated with quizzes.

## Assessment Module
- Processes student quiz attempts.
- Checks submitted answers automatically.
- Calculates and generates quiz scores.

## Result Management Module
- Stores and displays student performance records.
- Allows teachers and administrators to monitor results.

## Reporting Module
- Generates quiz statistics and system reports.
- Provides information such as student count, teacher count, and quiz performance.

---

# 3. Data Layer (Database)

The Data Layer manages the storage and retrieval of system information. It ensures that data is organized, consistent, and securely maintained.

### Stored Data:

## User Data
- Administrator accounts
- Teacher accounts
- Student accounts
- Login credentials
- User roles

## Quiz Data
- Quiz information
- Subjects/categories
- Questions
- Answer choices
- Correct answers

## Assessment Data
- Student quiz attempts
- Submitted answers
- Scores
- Previous quiz results

---

# System Data Flow

1. Users login through the user interface.
2. The authentication module verifies account information from the database.
3. The system identifies the user's role and provides access to authorized features.
4. Users perform actions such as creating quizzes, taking quizzes, or viewing results.
5. The application layer processes requests and communicates with the database.
6. The database stores updated information and returns required data to the system.
7. The results are displayed through the user interface.
# Architecture Diagram

```mermaid
flowchart TD

    A[Administrator]
    B[Teacher]
    C[Student]

    A --> D[JavaFX Desktop Application]
    B --> D
    C --> D

    D --> E[Presentation Layer<br>JavaFX + FXML + CSS]

    E --> F[Application Layer<br>Business Logic]

    F --> G[Authentication Module]
    F --> H[Quiz Management Module]
    F --> I[Assessment Module]
    F --> J[Result Management Module]
    F --> K[Reporting Module]

    G --> L[(MySQL / SQLite Database)]
    H --> L
    I --> L
    J --> L
    K --> L
```
