package quizora.DAO;

import java.sql.*;
import java.util.UUID;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

/** Transaction-scoped fixtures prove aggregation without leaving sample records. */
public class AdminDashboardDataTest {
    public static void main(String[] args) throws Exception {
        var dao = new AdminDashboardDAO();
        try {
            dao.load(new AuthenticatedUser(1, "Student", "student"));
            throw new AssertionError("Student access allowed");
        } catch (SecurityException expected) { }
        try {
            dao.load(new AuthenticatedUser(4294967295L, "Missing admin", "admin"));
            throw new AssertionError("Missing admin access allowed");
        } catch (SecurityException expected) { }
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var before = dao.readSnapshot(connection);
                if (before.quizzes() == 0) {
                    require(before.averageScore() == null, "Empty scores must be unavailable");
                    require(before.quizStatuses().stream().allMatch(s -> s.value() == 0), "Empty pie");
                }
                long finalized;
                try (var s = connection.prepareStatement("SELECT COUNT(*) FROM quiz_results r "
                        + "JOIN quiz_attempts a ON a.attempt_id=r.attempt_id WHERE a.status='submitted'");
                     var r = s.executeQuery()) { r.next(); finalized = r.getLong(1); }
                String tag = UUID.randomUUID().toString();
                long teacher = insert(connection, "INSERT INTO users (full_name,username,email,password_hash,role) "
                        + "VALUES ('Teacher', ?, ?, 'test-only', 'teacher')", tag, tag + "@example.invalid");
                long student = insert(connection, "INSERT INTO users (full_name,username,email,password_hash,role) "
                        + "VALUES ('Student', ?, ?, 'test-only', 'student')", "s" + tag, "s" + tag + "@example.invalid");
                long subject = insert(connection, "INSERT INTO subjects (subject_name) VALUES (?)", tag);
                long quiz = insert(connection, "INSERT INTO quizzes (subject_id,teacher_id,title,status) "
                        + "VALUES (?,?,'Test published','published')", subject, teacher);
                insert(connection, "INSERT INTO quizzes (subject_id,teacher_id,title,status) "
                        + "VALUES (?,?,'Test draft','draft')", subject, teacher);
                long attempt = insert(connection, "INSERT INTO quiz_attempts (quiz_id,student_id,status,submitted_at) "
                        + "VALUES (?,?,'submitted',CURRENT_TIMESTAMP)", quiz, student);
                insert(connection, "INSERT INTO quiz_results (attempt_id,score,total_points) VALUES (?,1,2)", attempt);
                long yesterday = insert(connection, "INSERT INTO quiz_attempts "
                        + "(quiz_id,student_id,status,started_at,submitted_at) "
                        + "VALUES (?,?,'submitted',CURRENT_DATE - INTERVAL 1 DAY,CURRENT_DATE - INTERVAL 1 DAY)", quiz, student);
                insert(connection, "INSERT INTO quiz_results (attempt_id,score,total_points) VALUES (?,9,10)", yesterday);
                insert(connection, "INSERT INTO quiz_attempts (quiz_id,student_id,status,started_at,submitted_at) "
                        + "VALUES (?,?,'submitted',CURRENT_DATE - INTERVAL 14 DAY,CURRENT_DATE - INTERVAL 14 DAY)", quiz, student);
                insert(connection, "INSERT INTO quiz_attempts (quiz_id,student_id) VALUES (?,?)", quiz, student);
                var after = dao.readSnapshot(connection);
                require(after.students() == before.students()+1 && after.teachers() == before.teachers()+1, "Role counts");
                require(after.quizzes() == before.quizzes()+2 && after.subjects() == before.subjects()+1, "Quiz/subject counts");
                require(after.submissions() == before.submissions()+3, "Only submitted attempts count");
                double expectedAverage = ((before.averageScore() == null ? 0 : before.averageScore()*finalized)+140)/(finalized+2);
                require(Math.abs(after.averageScore()-expectedAverage) < 0.0001, "Mean of percentages, not pooled points");
                require(after.submissionsByDay().size() == 14, "Fourteen-day series");
                for (int i=0; i<14; i++) {
                    long expected = before.submissionsByDay().get(i).value() + (i >= 12 ? 1 : 0);
                    require(after.submissionsByDay().get(i).value() == expected, "Date grouping and window bounds");
                }
                require(after.quizStatuses().get(0).value() == before.quizStatuses().get(0).value()+1, "Draft count");
                require(after.quizStatuses().get(1).value() == before.quizStatuses().get(1).value()+1, "Published count");
                require(after.quizStatuses().stream().mapToLong(s -> s.value()).sum() == after.quizzes(), "Pie reconciles");
                require(after.quizzesBySubject().stream().anyMatch(s -> s.label().equals(tag) && s.value()==2), "Subject bar count");
                System.out.println("PASS: KPI totals, normalized average, 14-day boundaries, status/subject charts and admin access.");
            } finally { connection.rollback(); }
        }
    }

    private static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i=0;i<values.length;i++) statement.setObject(i+1, values[i]);
            statement.executeUpdate();
            try (var keys=statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
