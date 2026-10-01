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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AccountRecord;

/** Admin-only management of teacher accounts and their subject assignments. */
public final class TeacherManagementDAO extends AccountManagementDAO {
    /** An active subject the administrator can assign; archived subjects are never offered. */
    public record AssignableSubject(long id, String name, String category, boolean assigned) { }

    public TeacherManagementDAO() { super("teacher"); }

    public List<AssignableSubject> subjects(AuthenticatedUser admin, AccountRecord teacher) throws SQLException {
        try (var connection = databaseConnection.getReadOnlyConnection()) {
            connection.setAutoCommit(false);
            requireAdmin(connection, admin, false);
            requireTeacher(connection, teacher);
            List<AssignableSubject> subjects = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT s.subject_id, s.subject_name, COALESCE(s.category,''),
                        EXISTS(SELECT 1 FROM teacher_subjects ts WHERE ts.subject_id=s.subject_id AND ts.teacher_id=?)
                    FROM subjects s WHERE s.archived_at IS NULL
                    ORDER BY s.subject_name, s.subject_id
                    """)) {
                statement.setQueryTimeout(10);
                statement.setLong(1, teacher.id());
                try (var result = statement.executeQuery()) {
                    while (result.next()) subjects.add(new AssignableSubject(result.getLong(1), result.getString(2),
                            result.getString(3), result.getBoolean(4)));
                }
            }
            connection.commit();
            return List.copyOf(subjects);
        }
    }

    /**
     * Replaces the teacher's assignments to active subjects. Assignments to archived subjects are
     * left untouched; existing quizzes keep their subject regardless of assignment changes.
     */
    public void assign(AuthenticatedUser admin, AccountRecord teacher, Set<Long> subjectIds) throws SQLException {
        Set<Long> selected = Set.copyOf(subjectIds);
        try (var connection = databaseConnection.getConnection(); var control = connection.createStatement()) {
            control.execute("PRAGMA busy_timeout=5000");
            control.execute("PRAGMA foreign_keys=ON");
            control.execute("BEGIN IMMEDIATE");
            try {
                requireAdmin(connection, admin, true);
                requireTeacher(connection, teacher);
                Set<Long> active = new HashSet<>();
                try (var statement = connection.prepareStatement("SELECT subject_id FROM subjects WHERE archived_at IS NULL")) {
                    statement.setQueryTimeout(10);
                    try (var result = statement.executeQuery()) {
                        while (result.next()) active.add(result.getLong(1));
                    }
                }
                if (!active.containsAll(selected))
                    throw new IllegalArgumentException("A selected subject was archived or removed. Close the form and try again.");
                try (var remove = connection.prepareStatement("""
                        DELETE FROM teacher_subjects WHERE teacher_id=? AND subject_id=?
                        """)) {
                    for (long subjectId : active) {
                        if (selected.contains(subjectId)) continue;
                        remove.setLong(1, teacher.id());
                        remove.setLong(2, subjectId);
                        remove.addBatch();
                    }
                    remove.executeBatch();
                }
                try (var add = connection.prepareStatement("""
                        INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)
                        ON CONFLICT(teacher_id,subject_id) DO NOTHING
                        """)) {
                    for (long subjectId : selected) {
                        add.setLong(1, teacher.id());
                        add.setLong(2, subjectId);
                        add.addBatch();
                    }
                    add.executeBatch();
                }
                control.execute("COMMIT");
            } catch (SQLException | RuntimeException failure) {
                try { control.execute("ROLLBACK"); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    /** Archived teachers keep their history but cannot receive new assignments. */
    private static void requireTeacher(Connection connection, AccountRecord teacher) throws SQLException {
        if (teacher == null) throw new IllegalArgumentException("Select a teacher.");
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE user_id=? AND role='teacher' AND archived_at IS NULL")) {
            statement.setQueryTimeout(10);
            statement.setLong(1, teacher.id());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("This teacher is archived or no longer exists. Refresh and try again.");
            }
        }
    }
}
