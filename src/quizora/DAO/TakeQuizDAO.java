package quizora.DAO;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

/** Owns attempt authorization, deadlines, saved answers, and atomic grading. */
public final class TakeQuizDAO {
    public record Choice(long id, String title, String subject, int minutes, boolean resume) {
        @Override public String toString() { return title + " — " + subject + (resume ? " (resume)" : ""); }
    }
    public record Question(long id, String text, List<String> options, int points, String answer) { }
    public record Score(long earned, long possible) { }
    public record Attempt(long id, long quizId, String title, LocalDateTime deadline,
            List<Question> questions, Score score) { }
    private record Header(long quizId, String title, LocalDateTime started, LocalDateTime deadline, boolean submitted) { }
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final userDAO.Connections connections;
    private final Clock clock;
    public TakeQuizDAO() { this(databaseConnection::getConnection, Clock.systemDefaultZone()); }
    public TakeQuizDAO(userDAO.Connections connections, Clock clock) { this.connections = connections; this.clock = clock; }
    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws SQLException; }

    // Acquire SQLite's write lock before reading so concurrent starts/submits cannot race.
    private <T> T transaction(AuthenticatedUser user, Work<T> work) throws SQLException {
        if (user == null || !"student".equals(user.role())) throw new SecurityException("Student access is required.");
        try (var connection = connections.open(); var control = connection.createStatement()) {
            control.execute("PRAGMA busy_timeout=5000");
            control.execute("PRAGMA foreign_keys=ON");
            control.execute("BEGIN IMMEDIATE");
            try {
                try (var statement = connection.prepareStatement("SELECT 1 FROM users WHERE user_id=? AND role='student' AND is_active=1 AND archived_at IS NULL")) {
                    statement.setLong(1,user.id());
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SecurityException("Student access is required.");
                    }
                }
                T value = work.run(connection);
                control.execute("COMMIT");
                return value;
            } catch (SQLException | RuntimeException failure) {
                try { control.execute("ROLLBACK"); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    public List<Choice> choices(AuthenticatedUser user) throws SQLException {
        return transaction(user, connection -> {
            List<Choice> choices = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT q.quiz_id,q.title,s.subject_name,q.time_limit_minutes,
                        EXISTS(SELECT 1 FROM quiz_attempts a WHERE a.quiz_id=q.quiz_id AND a.student_id=? AND a.status='in_progress') AS resume
                    FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id
                    WHERE (q.status='published' AND q.archived_at IS NULL AND s.archived_at IS NULL
                        AND EXISTS(SELECT 1 FROM questions x WHERE x.quiz_id=q.quiz_id))
                        OR EXISTS(SELECT 1 FROM quiz_attempts a WHERE a.quiz_id=q.quiz_id AND a.student_id=? AND a.status='in_progress')
                    ORDER BY resume DESC,q.updated_at DESC,q.quiz_id DESC
                    """)) {
                statement.setLong(1,user.id()); statement.setLong(2,user.id());
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) choices.add(new Choice(rows.getLong(1),rows.getString(2),rows.getString(3),rows.getInt(4),rows.getBoolean(5)));
                }
            }
            return List.copyOf(choices);
        });
    }

    public Attempt open(AuthenticatedUser user, long quizId) throws SQLException {
        return transaction(user, connection -> {
            long attemptId = 0;
            try (var statement = connection.prepareStatement("SELECT attempt_id FROM quiz_attempts WHERE quiz_id=? AND student_id=? AND status='in_progress' ORDER BY attempt_id LIMIT 1")) {
                statement.setLong(1,quizId); statement.setLong(2,user.id());
                try (var rows = statement.executeQuery()) { if (rows.next()) attemptId = rows.getLong(1); }
            }
            if (attemptId == 0) {
                try (var statement = connection.prepareStatement("""
                        SELECT 1 FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id
                        WHERE q.quiz_id=? AND q.status='published' AND q.archived_at IS NULL AND s.archived_at IS NULL
                            AND EXISTS(SELECT 1 FROM questions x WHERE x.quiz_id=q.quiz_id)
                        """)) {
                    statement.setLong(1,quizId);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new IllegalArgumentException("This quiz is no longer available. Refresh the quiz list.");
                    }
                }
                try (var statement = connection.prepareStatement("INSERT INTO quiz_attempts(quiz_id,student_id,started_at) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1,quizId); statement.setLong(2,user.id());
                    statement.setString(3,LocalDateTime.now(clock).format(SQL_TIME)); statement.executeUpdate();
                    try (var keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Attempt ID missing");
                        attemptId = keys.getLong(1);
                    }
                }
            }
            Header header = header(connection,user,attemptId);
            if (!header.submitted() && expired(header)) finish(connection,attemptId,header);
            return read(connection,user,attemptId);
        });
    }

    public Attempt save(AuthenticatedUser user, long attemptId, long questionId, String answer) throws SQLException {
        if (answer != null && !Set.of("A","B","C","D").contains(answer)) throw new IllegalArgumentException("Select one of the four answers.");
        return transaction(user, connection -> {
            Header header = header(connection,user,attemptId);
            if (header.submitted()) return read(connection,user,attemptId);
            if (expired(header)) {
                finish(connection,attemptId,header);
                return read(connection,user,attemptId);
            }
            try (var statement = connection.prepareStatement("SELECT 1 FROM questions WHERE question_id=? AND quiz_id=?")) {
                statement.setLong(1,questionId); statement.setLong(2,header.quizId());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Question does not belong to this quiz.");
                }
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO student_answers(attempt_id,question_id,quiz_id,selected_answer) VALUES(?,?,?,?)
                    ON CONFLICT(attempt_id,question_id) DO UPDATE SET selected_answer=excluded.selected_answer
                    """)) {
                statement.setLong(1,attemptId); statement.setLong(2,questionId); statement.setLong(3,header.quizId());
                statement.setString(4,answer); statement.executeUpdate();
            }
            return read(connection,user,attemptId);
        });
    }

    public Attempt submit(AuthenticatedUser user, long attemptId) throws SQLException {
        return transaction(user, connection -> {
            Header header = header(connection,user,attemptId);
            if (!header.submitted()) finish(connection,attemptId,header);
            return read(connection,user,attemptId);
        });
    }

    private boolean expired(Header header) { return !LocalDateTime.now(clock).isBefore(header.deadline()); }
    private Header header(Connection connection, AuthenticatedUser user, long attemptId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT q.quiz_id,q.title,a.started_at,q.time_limit_minutes,a.status
                FROM quiz_attempts a JOIN quizzes q ON q.quiz_id=a.quiz_id WHERE a.attempt_id=? AND a.student_id=?
                """)) {
            statement.setLong(1,attemptId); statement.setLong(2,user.id());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("This attempt is not available to your account.");
                LocalDateTime started = LocalDateTime.parse(rows.getString(3),SQL_TIME);
                return new Header(rows.getLong(1),rows.getString(2),started,started.plusMinutes(rows.getInt(4)),"submitted".equals(rows.getString(5)));
            }
        }
    }
    private void finish(Connection connection,long attemptId,Header header) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO quiz_results(attempt_id,score,total_points)
                SELECT ?,COALESCE(SUM(CASE WHEN a.selected_answer=q.correct_answer THEN q.points ELSE 0 END),0),SUM(q.points)
                FROM questions q LEFT JOIN student_answers a ON a.question_id=q.question_id AND a.attempt_id=?
                WHERE q.quiz_id=?
                """)) {
            statement.setLong(1,attemptId); statement.setLong(2,attemptId); statement.setLong(3,header.quizId());
            statement.executeUpdate();
        }
        LocalDateTime submitted = LocalDateTime.now(clock);
        if (submitted.isAfter(header.deadline())) submitted = header.deadline();
        if (submitted.isBefore(header.started())) submitted = header.started();
        try (var statement = connection.prepareStatement("UPDATE quiz_attempts SET status='submitted',submitted_at=? WHERE attempt_id=?")) {
            statement.setString(1,submitted.format(SQL_TIME)); statement.setLong(2,attemptId); statement.executeUpdate();
        }
    }
    private Attempt read(Connection connection,AuthenticatedUser user,long attemptId) throws SQLException {
        Header header = header(connection,user,attemptId);
        List<Question> questions = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT q.question_id,q.question_text,q.option_a,q.option_b,q.option_c,q.option_d,q.points,a.selected_answer
                FROM questions q LEFT JOIN student_answers a ON a.question_id=q.question_id AND a.attempt_id=?
                WHERE q.quiz_id=? ORDER BY q.question_order,q.question_id
                """)) {
            statement.setLong(1,attemptId); statement.setLong(2,header.quizId());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) questions.add(new Question(rows.getLong(1),rows.getString(2),
                        List.of(rows.getString(3),rows.getString(4),rows.getString(5),rows.getString(6)),rows.getInt(7),rows.getString(8)));
            }
        }
        Score score = null;
        if (header.submitted()) {
            try (var statement = connection.prepareStatement("SELECT score,total_points FROM quiz_results WHERE attempt_id=?")) {
                statement.setLong(1,attemptId);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Submitted attempt has no result");
                    score = new Score(rows.getLong(1),rows.getLong(2));
                }
            }
        }
        return new Attempt(attemptId,header.quizId(),header.title(),header.deadline(),List.copyOf(questions),score);
    }
}
