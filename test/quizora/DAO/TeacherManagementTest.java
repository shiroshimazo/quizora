package quizora.DAO;

import java.sql.SQLException;
import java.util.UUID;
import quizora.auth.*;
import quizora.database.databaseConnection;
import quizora.model.*;

/** Disposable teacher fixtures exercise the shared DAO's role boundary. */
public class TeacherManagementTest {
    public static void main(String[] args) throws Exception {
        var auth = new AuthenticationService();
        var admin = auth.authenticate("admin", "Admin@123".toCharArray()).orElseThrow();
        var teachers = new TeacherManagementDAO();
        var students = new StudentManagementDAO();
        String tag = "teacher-" + UUID.randomUUID().toString().substring(0, 20);
        long teacherId = 0, studentId = 0, subjectId = 0, quizId = 0;
        try (var connection = databaseConnection.getConnection()) {
            try {
                var details = new AccountChanges("Test Teacher", tag, tag + "@example.invalid", true);
                try { teachers.create(null, details, "Test@123"); throw new AssertionError("Anonymous create allowed"); }
                catch (SecurityException expected) { }
                var teacher = teachers.create(admin, details, "Test@123");
                teacherId = teacher.id();
                require(auth.authenticate(tag, "Test@123".toCharArray()).orElseThrow().role().equals("teacher"), "Teacher login role");
                long id = teacherId;
                require(students.load(admin).stream().noneMatch(s -> s.id() == id), "Teacher excluded from students");
                var student = students.create(admin, new AccountChanges("Test Student", "s-" + tag, "s-" + tag + "@example.invalid", true), "Test@123");
                studentId = student.id();
                long sid = studentId;
                require(teachers.load(admin).stream().noneMatch(s -> s.id() == sid), "Student excluded from teachers");
                try { teachers.archive(admin, student); throw new AssertionError("Teacher DAO archived student"); }
                catch (SQLException expected) { require("40001".equals(expected.getSQLState()), "Cross-role update blocked"); }
                try { teachers.create(admin, new AccountChanges("Duplicate", student.username(), details.email(), true), "Test@123");
                    throw new AssertionError("Cross-role duplicate allowed"); }
                catch (SQLException expected) { require(expected.getErrorCode() == 1062, "Cross-role duplicate blocked"); }
                try { teachers.create(new AuthenticatedUser(teacherId, "Forged", "admin"), details, "Test@123");
                    throw new AssertionError("Forged admin allowed"); }
                catch (SecurityException expected) { }
                try (var s = connection.prepareStatement("SELECT password_hash FROM users WHERE user_id=?")) {
                    s.setLong(1, teacherId);
                    try (var r = s.executeQuery()) {
                        r.next();
                        require(!r.getString(1).equals("Test@123") && PasswordHasher.verify("Test@123".toCharArray(), r.getString(1)), "Password is hashed");
                    }
                }
                teacher = teachers.edit(admin, teacher, new AccountChanges("Edited Teacher", tag, details.email(), false));
                require(auth.authenticate(tag, "Test@123".toCharArray()).isEmpty(), "Inactive teacher cannot log in");
                teacher = teachers.edit(admin, teacher, details);
                subjectId = insert(connection, "INSERT INTO subjects(subject_name) VALUES(?)", tag);
                quizId = insert(connection, "INSERT INTO quizzes(subject_id,teacher_id,title) VALUES(?,?,'Teacher archive test')", subjectId, teacherId);
                var archived = teachers.archive(admin, teacher);
                require(archived.archived() && !archived.active(), "Teacher archived");
                require(auth.authenticate(tag, "Test@123".toCharArray()).isEmpty(), "Archived teacher cannot log in");
                try (var s = connection.prepareStatement("SELECT teacher_id FROM quizzes WHERE quiz_id=?")) {
                    s.setLong(1, quizId);
                    try (var r = s.executeQuery()) { require(r.next() && r.getLong(1) == teacherId, "Authored quiz preserved"); }
                }
                System.out.println("PASS: teacher creation/login, hashed password, role isolation, duplicate/admin checks, edit/status, archive and authored quiz preservation.");
            } finally {
                delete(connection, "DELETE FROM quizzes WHERE quiz_id=?", quizId);
                delete(connection, "DELETE FROM subjects WHERE subject_id=?", subjectId);
                delete(connection, "DELETE FROM users WHERE user_id=?", teacherId);
                delete(connection, "DELETE FROM users WHERE user_id=?", studentId);
            }
        }
    }
    private static long insert(java.sql.Connection c, String sql, Object... values) throws SQLException {
        try (var s = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
            try (var r = s.getGeneratedKeys()) { r.next(); return r.getLong(1); }
        }
    }
    private static void delete(java.sql.Connection c, String sql, long id) throws SQLException {
        if (id == 0) return;
        try (var s = c.prepareStatement(sql)) { s.setLong(1, id); s.executeUpdate(); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
