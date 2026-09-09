package quizora.DAO;

import java.sql.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.ReportData;

public final class ReportsDAO {
    public ReportData load(AuthenticatedUser admin, int threshold) throws SQLException {
        if (threshold < 0 || threshold > 100) throw new IllegalArgumentException("Passing score must be between 0 and 100.");
        try (var c = databaseConnection.getConnection()) {
            c.setReadOnly(true);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            AccountManagementDAO.requireAdmin(c, admin, false);
            ReportData data = snapshot(c, threshold);
            c.commit();
            return data;
        }
    }
    ReportData snapshot(Connection c, int threshold) throws SQLException {
        var students = accounts(c, "student");
        var teachers = accounts(c, "teacher");
        long quizzes, published, archived, attempts, submitted;
        java.time.LocalDateTime generated;
        try (var s = c.prepareStatement("""
                SELECT CURRENT_TIMESTAMP,
                (SELECT COUNT(*) FROM quizzes),
                (SELECT COUNT(*) FROM quizzes WHERE status='published' AND archived_at IS NULL),
                (SELECT COUNT(*) FROM quizzes WHERE archived_at IS NOT NULL),
                (SELECT COUNT(*) FROM quiz_attempts),
                (SELECT COUNT(*) FROM quiz_attempts WHERE status='submitted')
                """)) {
            s.setQueryTimeout(10);
            try (var r = s.executeQuery()) {
                r.next(); generated = r.getTimestamp(1).toLocalDateTime(); quizzes = r.getLong(2);
                published = r.getLong(3); archived = r.getLong(4); attempts = r.getLong(5); submitted = r.getLong(6);
            }
        }
        try (var s = c.prepareStatement("""
                SELECT COUNT(*), AVG(100.0*r.score/r.total_points),
                MIN(100.0*r.score/r.total_points), MAX(100.0*r.score/r.total_points),
                AVG(CASE WHEN 100.0*r.score/r.total_points >= ? THEN 100.0 ELSE 0 END)
                FROM quiz_results r JOIN quiz_attempts a ON a.attempt_id=r.attempt_id
                WHERE a.status='submitted' AND r.total_points>0
                """)) {
            s.setQueryTimeout(10); s.setInt(1, threshold);
            try (var r = s.executeQuery()) {
                r.next(); return new ReportData(generated, threshold, students, teachers, quizzes, published,
                        archived, attempts, submitted, r.getLong(1), number(r, 2), number(r, 3), number(r, 4), number(r, 5));
            }
        }
    }
    private ReportData.Accounts accounts(Connection c, String role) throws SQLException {
        try (var s = c.prepareStatement("""
                SELECT COUNT(*), COALESCE(SUM(is_active=TRUE AND archived_at IS NULL),0),
                COALESCE(SUM(is_active=FALSE AND archived_at IS NULL),0), COALESCE(SUM(archived_at IS NOT NULL),0)
                FROM users WHERE role=?
                """)) {
            s.setQueryTimeout(10); s.setString(1, role);
            try (var r = s.executeQuery()) { r.next(); return new ReportData.Accounts(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4)); }
        }
    }
    private Double number(ResultSet r, int column) throws SQLException { double n = r.getDouble(column); return r.wasNull() ? null : n; }
}
