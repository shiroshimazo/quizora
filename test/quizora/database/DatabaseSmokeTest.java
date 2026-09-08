package quizora.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** Integration check against local MySQL; fixture rows are always rolled back. */
public class DatabaseSmokeTest {
    public static void main(String[] args) throws SQLException {
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String tag = UUID.randomUUID().toString();
                long teacher = insert(connection, "INSERT INTO users "
                        + "(full_name, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                        "Test Teacher", tag, tag + "@example.invalid", "test-only", "teacher");
                long student = insert(connection, "INSERT INTO users "
                        + "(full_name, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                        "Test Student", "s" + tag, "s" + tag + "@example.invalid", "test-only", "student");
                long subject = insert(connection,
                        "INSERT INTO subjects (subject_name) VALUES (?)", tag);
                execute(connection, "INSERT INTO teacher_subjects VALUES (?, ?)", teacher, subject);
                long quiz = insert(connection,
                        "INSERT INTO quizzes (subject_id, teacher_id, title) VALUES (?, ?, ?)",
                        subject, teacher, "Test Quiz");
                long otherQuiz = insert(connection,
                        "INSERT INTO quizzes (subject_id, teacher_id, title) VALUES (?, ?, ?)",
                        subject, teacher, "Other Quiz");
                long question = insert(connection, "INSERT INTO questions (quiz_id, question_text, "
                        + "option_a, option_b, option_c, option_d, correct_answer, question_order) "
                        + "VALUES (?, '2+2?', '4', '3', '2', '1', 'A', 1)", quiz);
                long attempt = insert(connection,
                        "INSERT INTO quiz_attempts (quiz_id, student_id) VALUES (?, ?)", quiz, student);
                execute(connection, "INSERT INTO student_answers "
                        + "(attempt_id, question_id, quiz_id, selected_answer) VALUES (?, ?, ?, 'A')",
                        attempt, question, quiz);
                reject(connection, 1062, "INSERT INTO student_answers "
                        + "(attempt_id, question_id, quiz_id) VALUES (?, ?, ?)", attempt, question, quiz);
                long otherAttempt = insert(connection,
                        "INSERT INTO quiz_attempts (quiz_id, student_id) VALUES (?, ?)", otherQuiz, student);
                reject(connection, 1452, "INSERT INTO student_answers "
                        + "(attempt_id, question_id, quiz_id) VALUES (?, ?, ?)", otherAttempt, question, otherQuiz);
                execute(connection, "UPDATE quiz_attempts SET status='submitted', "
                        + "submitted_at=CURRENT_TIMESTAMP WHERE attempt_id=?", attempt);
                execute(connection, "INSERT INTO quiz_results (attempt_id, score, total_points) "
                        + "VALUES (?, 1, 1)", attempt);
                reject(connection, 1062, "INSERT INTO quiz_results (attempt_id, score, total_points) "
                        + "VALUES (?, 1, 1)", attempt);
                reject(connection, 3819, "INSERT INTO quiz_results (attempt_id, score, total_points) "
                        + "VALUES (?, 2, 1)", otherAttempt);
                reject(connection, 1451, "DELETE FROM quizzes WHERE quiz_id=?", quiz);
                System.out.println("PASS: eight-table workflow, duplicate protection, quiz references, "
                        + "score bounds, historical deletion protection. Fixture rows rolled back.");
            } finally {
                connection.rollback();
            }
        }
    }

    private static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated key");
                return keys.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static void reject(Connection connection, int code, String sql, Object... values)
            throws SQLException {
        try {
            execute(connection, sql, values);
        } catch (SQLException expected) {
            if (expected.getErrorCode() == code) return;
            throw expected;
        }
        throw new AssertionError("Expected MySQL constraint error " + code);
    }
}
