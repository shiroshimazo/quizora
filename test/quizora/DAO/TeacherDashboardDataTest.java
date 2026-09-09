package quizora.DAO;

import java.sql.*;
import java.util.UUID;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

/** Transaction-scoped fixtures prove aggregation without leaving sample records. */
public class TeacherDashboardDataTest {
    public static void main(String[] args) throws Exception {
        var dao = new TeacherDashboardDAO();
        try {
            dao.load(new AuthenticatedUser(1, "Student", "student"));
            throw new AssertionError("Student access allowed");
        } catch (SecurityException expected) { }
        try {
            dao.load(new AuthenticatedUser(4294967295L, "Missing teacher", "teacher"));
            throw new AssertionError("Missing admin access allowed");
        } catch (SecurityException expected) { }
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String tag = UUID.randomUUID().toString();
                long teacher = insert(connection, "INSERT INTO users (full_name,username,email,password_hash,role) "
                        + "VALUES ('Teacher', ?, ?, 'test-only', 'teacher')", tag, tag + "@example.invalid");
                var before = dao.readSnapshot(connection, teacher);
                require(before.quizzes() == 0 && before.averageScore() == null, "Empty teacher");
                long student = insert(connection, "INSERT INTO users (full_name,username,email,password_hash,role) "
                        + "VALUES ('Student', ?, ?, 'test-only', 'student')", "s" + tag, "s" + tag + "@example.invalid");
                long subject = insert(connection, "INSERT INTO subjects (subject_name) VALUES (?)", tag);
                try (var assignment = connection.prepareStatement("INSERT INTO teacher_subjects VALUES (?,?)")) {
                    assignment.setLong(1, teacher); assignment.setLong(2, subject); assignment.executeUpdate();
                }
                long otherTeacher = insert(connection, "INSERT INTO users (full_name,username,email,password_hash,role) "
                        + "VALUES ('Other', ?, ?, 'test-only', 'teacher')", "o"+tag, "o"+tag+"@example.invalid");
                long otherQuiz = insert(connection, "INSERT INTO quizzes (subject_id,teacher_id,title,status) "
                        + "VALUES (?,?,'Other quiz','published')", subject, otherTeacher);
                long otherAttempt = insert(connection, "INSERT INTO quiz_attempts (quiz_id,student_id,status,submitted_at) "
                        + "VALUES (?,?,'submitted',CURRENT_TIMESTAMP)", otherQuiz, student);
                insert(connection, "INSERT INTO quiz_results (attempt_id,score,total_points) VALUES (?,0,10)", otherAttempt);
                insert(connection, "INSERT INTO quizzes (subject_id,teacher_id,title,archived_at) "
                        + "VALUES (?,?,'Archived quiz',CURRENT_TIMESTAMP)", subject, teacher);
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
                var after = dao.readSnapshot(connection, teacher);
                require(after.students() == 1 && after.published() == 1, "Role counts");
                require(after.quizzes() == before.quizzes()+2 && after.subjects() == before.subjects()+1, "Quiz/subject counts");
                require(after.submissions() == before.submissions()+3, "Only submitted attempts count");
                require(Math.abs(after.averageScore()-70) < 0.0001, "Normalized average and teacher isolation");
                require(after.submissionsByDay().size() == 14, "Fourteen-day series");
                for (int i=0; i<14; i++) {
                    long expected = before.submissionsByDay().get(i).value() + (i >= 12 ? 1 : 0);
                    require(after.submissionsByDay().get(i).value() == expected, "Date grouping and window bounds");
                }
                require(after.quizStatuses().get(0).value() == before.quizStatuses().get(0).value()+1, "Draft count");
                require(after.quizStatuses().get(1).value() == before.quizStatuses().get(1).value()+1, "Published count");
                require(after.quizStatuses().stream().mapToLong(s -> s.value()).sum() == after.quizzes(), "Pie reconciles");
                require(after.quizzesBySubject().stream().anyMatch(s -> s.label().equals(tag) && s.value()==2), "Subject bar count");
                System.out.println("PASS: KPI totals, normalized average, 14-day boundaries, status/subject charts teacher isolation, archive exclusion and access.");
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
