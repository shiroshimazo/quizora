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
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.SubjectChanges;
import quizora.model.SubjectRecord;

/** Admin-only add, list, edit and archive for subjects and their categories. */
public final class SubjectManagementDAO {

    /** Every subject with how many quizzes and assigned teachers it has. */
    private static final String SELECT_SUBJECT = "SELECT s.*, "
            + "(SELECT COUNT(*) FROM quizzes q WHERE q.subject_id = s.subject_id) AS quizzes, "
            + "(SELECT COUNT(*) FROM teacher_subjects t WHERE t.subject_id = s.subject_id) AS teachers "
            + "FROM subjects s ";

    public List<SubjectRecord> load(AuthenticatedUser admin) throws SQLException {
        List<SubjectRecord> subjects = new ArrayList<>();
        try (Connection connection = databaseConnection.getConnection()) {
            AccessCheck.requireAdmin(connection, admin);
            try (PreparedStatement statement = connection.prepareStatement(SELECT_SUBJECT + "ORDER BY s.subject_id DESC");
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    subjects.add(readSubject(result));
                }
            }
        }
        return subjects;
    }

    /** Adds a subject when original is null, otherwise edits it. Duplicate names fail with a UNIQUE error. */
    public SubjectRecord save(AuthenticatedUser admin, SubjectRecord original, SubjectChanges changes) throws SQLException {
        if (changes == null) {
            throw new NullPointerException("Subject details are required.");
        }
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AccessCheck.requireAdmin(connection, admin);
                long subjectId;
                if (original == null) {
                    String sql = "INSERT INTO subjects (subject_name, category, description) VALUES (?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, changes.name());
                        statement.setString(2, changes.category());
                        statement.setString(3, changes.description());
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            keys.next();
                            subjectId = keys.getLong(1);
                        }
                    }
                } else {
                    checkCanChange(connection, original);
                    subjectId = original.id();
                    String sql = "UPDATE subjects SET subject_name = ?, category = ?, description = ? WHERE subject_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, changes.name());
                        statement.setString(2, changes.category());
                        statement.setString(3, changes.description());
                        statement.setLong(4, subjectId);
                        statement.executeUpdate();
                    }
                }
                SubjectRecord saved = findSubject(connection, subjectId);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Archiving hides the subject from new quizzes but keeps its quizzes and teacher assignments. */
    public SubjectRecord archive(AuthenticatedUser admin, SubjectRecord original) throws SQLException {
        if (original == null) {
            throw new NullPointerException("Select a subject.");
        }
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AccessCheck.requireAdmin(connection, admin);
                checkCanChange(connection, original);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE subjects SET archived_at = datetime('now', 'localtime') WHERE subject_id = ?")) {
                    statement.setLong(1, original.id());
                    statement.executeUpdate();
                }
                SubjectRecord archived = findSubject(connection, original.id());
                connection.commit();
                return archived;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Rejects subjects that changed since they were loaded, and archived subjects. */
    private void checkCanChange(Connection connection, SubjectRecord original) throws SQLException {
        SubjectRecord current = findSubject(connection, original.id());
        if (!current.equals(original)) {
            throw new SQLException("Subject changed. Refresh and try again.", "40001");
        }
        if (current.archived()) {
            throw new IllegalArgumentException("Archived subjects are read-only.");
        }
    }

    private SubjectRecord findSubject(Connection connection, long subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SUBJECT + "WHERE s.subject_id = ?")) {
            statement.setLong(1, subjectId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Subject no longer exists.", "40001");
                }
                return readSubject(result);
            }
        }
    }

    private SubjectRecord readSubject(ResultSet result) throws SQLException {
        String category = result.getString("category");
        String description = result.getString("description");
        return new SubjectRecord(
                result.getLong("subject_id"),
                result.getString("subject_name"),
                category == null ? "" : category,
                description == null ? "" : description,
                result.getTimestamp("created_at").toLocalDateTime(),
                result.getString("archived_at") != null,
                result.getInt("quizzes"),
                result.getInt("teachers"));
    }
}
