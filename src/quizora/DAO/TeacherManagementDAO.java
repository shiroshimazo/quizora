/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AccountRecord;

/** Admin-only management of teacher accounts and the subjects each teacher may create quizzes for. */
public final class TeacherManagementDAO extends AccountManagementDAO {

    /** An active subject the admin can assign; archived subjects are never offered. */
    public record AssignableSubject(long id, String name, String category, boolean assigned) { }

    public TeacherManagementDAO() {
        super("teacher");
    }

    public List<AssignableSubject> subjects(AuthenticatedUser admin, AccountRecord teacher) throws SQLException {
        List<AssignableSubject> subjects = new ArrayList<>();
        String sql = "SELECT s.subject_id, s.subject_name, COALESCE(s.category, '') AS category, "
                + "EXISTS (SELECT 1 FROM teacher_subjects ts WHERE ts.subject_id = s.subject_id AND ts.teacher_id = ?) AS assigned "
                + "FROM subjects s WHERE s.archived_at IS NULL "
                + "ORDER BY s.subject_name, s.subject_id";
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            AccessCheck.requireAdmin(connection, admin);
            checkTeacherExists(connection, teacher);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, teacher.id());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        subjects.add(new AssignableSubject(
                                result.getLong("subject_id"),
                                result.getString("subject_name"),
                                result.getString("category"),
                                result.getBoolean("assigned")));
                    }
                }
            }
        }
        return subjects;
    }

    /**
     * Replaces the teacher's assignments to active subjects with the selected ones.
     * Assignments to archived subjects are left alone, and existing quizzes keep their subject.
     */
    public void assign(AuthenticatedUser admin, AccountRecord teacher, Set<Long> subjectIds) throws SQLException {
        Set<Long> selected = new HashSet<>(subjectIds);
        try (Connection connection = databaseConnection.getConnection()) {
            beginWrite(connection);
            try {
                AccessCheck.requireAdmin(connection, admin);
                checkTeacherExists(connection, teacher);

                Set<Long> activeSubjects = new HashSet<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT subject_id FROM subjects WHERE archived_at IS NULL");
                        ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        activeSubjects.add(result.getLong("subject_id"));
                    }
                }
                if (!activeSubjects.containsAll(selected)) {
                    throw new IllegalArgumentException("A selected subject was archived or removed. Close the form and try again.");
                }

                String removeSql = "DELETE FROM teacher_subjects WHERE teacher_id = ? AND subject_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(removeSql)) {
                    for (long subjectId : activeSubjects) {
                        if (!selected.contains(subjectId)) {
                            statement.setLong(1, teacher.id());
                            statement.setLong(2, subjectId);
                            statement.executeUpdate();
                        }
                    }
                }

                String addSql = "INSERT INTO teacher_subjects (teacher_id, subject_id) VALUES (?, ?) "
                        + "ON CONFLICT (teacher_id, subject_id) DO NOTHING";
                try (PreparedStatement statement = connection.prepareStatement(addSql)) {
                    for (long subjectId : selected) {
                        statement.setLong(1, teacher.id());
                        statement.setLong(2, subjectId);
                        statement.executeUpdate();
                    }
                }
                execute(connection, "COMMIT");
            } catch (SQLException | RuntimeException error) {
                rollback(connection);
                throw error;
            }
        }
    }

    /** Archived teachers keep their history but cannot receive new assignments. */
    private static void checkTeacherExists(Connection connection, AccountRecord teacher) throws SQLException {
        if (teacher == null) {
            throw new IllegalArgumentException("Select a teacher.");
        }
        String sql = "SELECT user_id FROM users WHERE user_id = ? AND role = 'teacher' AND archived_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacher.id());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("This teacher is archived or no longer exists. Refresh and try again.");
                }
            }
        }
    }

    /** Locks the database for writing so two saves cannot interleave. */
    private static void beginWrite(Connection connection) throws SQLException {
        execute(connection, "PRAGMA busy_timeout = 5000");
        execute(connection, "PRAGMA foreign_keys = ON");
        execute(connection, "BEGIN IMMEDIATE");
    }

    private static void rollback(Connection connection) {
        try {
            execute(connection, "ROLLBACK");
        } catch (SQLException ignored) {
            // Keep the original error; it explains why the save failed.
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
