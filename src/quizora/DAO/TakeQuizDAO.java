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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

/**
 * Taking a quiz: starting or resuming an attempt, saving answers, the time limit, and scoring.
 * Every method locks the database first so two saves or submits for the same attempt cannot overlap.
 */
public final class TakeQuizDAO {

    /** A quiz in the Take Quiz drop-down. */
    public record Choice(long id, String title, String subject, int minutes, boolean resume) {
        @Override
        public String toString() {
            String text = title + " — " + subject;
            if (resume) {
                text = text + " (resume)";
            }
            return text;
        }
    }

    /** One question with its four options and the student's saved answer (null when unanswered). */
    public record Question(long id, String text, List<String> options, int points, String answer) { }

    public record Score(long earned, long possible) { }

    /** An attempt in progress, or a finished one when score is not null. */
    public record Attempt(long id, long quizId, String title, LocalDateTime deadline,
            List<Question> questions, Score score) { }

    /** The parts of an attempt needed to check access, the deadline and whether it is finished. */
    private record AttemptInfo(long quizId, String title, LocalDateTime started, LocalDateTime deadline, boolean submitted) { }

    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final userDAO.Connections connections;
    private final Clock clock;

    public TakeQuizDAO() {
        this(databaseConnection::getConnection, Clock.systemDefaultZone());
    }

    public TakeQuizDAO(userDAO.Connections connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    // ---------- Public actions ----------

    /** Quizzes the student can start, plus any quiz they already started (marked "resume"). */
    public List<Choice> choices(AuthenticatedUser student) throws SQLException {
        String sql = "SELECT q.quiz_id, q.title, s.subject_name, q.time_limit_minutes, "
                + "EXISTS (SELECT 1 FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id "
                + "AND a.student_id = ? AND a.status = 'in_progress') AS resume "
                + "FROM quizzes q JOIN subjects s ON s.subject_id = q.subject_id "
                + "WHERE (q.status = 'published' AND q.archived_at IS NULL AND s.archived_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM questions x WHERE x.quiz_id = q.quiz_id)) "
                + "OR EXISTS (SELECT 1 FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id "
                + "AND a.student_id = ? AND a.status = 'in_progress') "
                + "ORDER BY resume DESC, q.updated_at DESC, q.quiz_id DESC";
        try (Connection connection = connections.open()) {
            beginWrite(connection);
            try {
                AccessCheck.requireStudent(connection, student);
                List<Choice> choices = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, student.id());
                    statement.setLong(2, student.id());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            choices.add(new Choice(
                                    result.getLong("quiz_id"),
                                    result.getString("title"),
                                    result.getString("subject_name"),
                                    result.getInt("time_limit_minutes"),
                                    result.getBoolean("resume")));
                        }
                    }
                }
                execute(connection, "COMMIT");
                return choices;
            } catch (SQLException | RuntimeException error) {
                rollback(connection);
                throw error;
            }
        }
    }

    /** Resumes the student's unfinished attempt, or starts a new one if the quiz is still available. */
    public Attempt open(AuthenticatedUser student, long quizId) throws SQLException {
        try (Connection connection = connections.open()) {
            beginWrite(connection);
            try {
                AccessCheck.requireStudent(connection, student);
                long attemptId = findUnfinishedAttempt(connection, student.id(), quizId);
                if (attemptId == 0) {
                    checkQuizAvailable(connection, quizId);
                    attemptId = startAttempt(connection, student.id(), quizId);
                }
                AttemptInfo info = attemptInfo(connection, student, attemptId);
                if (!info.submitted() && isPastDeadline(info)) {
                    finish(connection, attemptId, info);
                }
                Attempt attempt = readAttempt(connection, student, attemptId);
                execute(connection, "COMMIT");
                return attempt;
            } catch (SQLException | RuntimeException error) {
                rollback(connection);
                throw error;
            }
        }
    }

    /** Saves one answer (A, B, C or D), or clears it when answer is null. */
    public Attempt save(AuthenticatedUser student, long attemptId, long questionId, String answer) throws SQLException {
        if (answer != null && !answer.equals("A") && !answer.equals("B") && !answer.equals("C") && !answer.equals("D")) {
            throw new IllegalArgumentException("Select one of the four answers.");
        }
        try (Connection connection = connections.open()) {
            beginWrite(connection);
            try {
                AccessCheck.requireStudent(connection, student);
                AttemptInfo info = attemptInfo(connection, student, attemptId);
                if (!info.submitted()) {
                    if (isPastDeadline(info)) {
                        // Time is up: score what was already saved instead of accepting this answer.
                        finish(connection, attemptId, info);
                    } else {
                        checkQuestionInQuiz(connection, questionId, info.quizId());
                        saveAnswer(connection, attemptId, questionId, info.quizId(), answer);
                    }
                }
                Attempt attempt = readAttempt(connection, student, attemptId);
                execute(connection, "COMMIT");
                return attempt;
            } catch (SQLException | RuntimeException error) {
                rollback(connection);
                throw error;
            }
        }
    }

    /** Scores and finishes the attempt. Submitting twice is harmless. */
    public Attempt submit(AuthenticatedUser student, long attemptId) throws SQLException {
        try (Connection connection = connections.open()) {
            beginWrite(connection);
            try {
                AccessCheck.requireStudent(connection, student);
                AttemptInfo info = attemptInfo(connection, student, attemptId);
                if (!info.submitted()) {
                    finish(connection, attemptId, info);
                }
                Attempt attempt = readAttempt(connection, student, attemptId);
                execute(connection, "COMMIT");
                return attempt;
            } catch (SQLException | RuntimeException error) {
                rollback(connection);
                throw error;
            }
        }
    }

    // ---------- Steps ----------

    /** Returns the ID of the student's unfinished attempt for this quiz, or 0 if there is none. */
    private long findUnfinishedAttempt(Connection connection, long studentId, long quizId) throws SQLException {
        String sql = "SELECT attempt_id FROM quiz_attempts "
                + "WHERE quiz_id = ? AND student_id = ? AND status = 'in_progress' "
                + "ORDER BY attempt_id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, quizId);
            statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong("attempt_id");
                }
                return 0;
            }
        }
    }

    private void checkQuizAvailable(Connection connection, long quizId) throws SQLException {
        String sql = "SELECT q.quiz_id FROM quizzes q JOIN subjects s ON s.subject_id = q.subject_id "
                + "WHERE q.quiz_id = ? AND q.status = 'published' AND q.archived_at IS NULL AND s.archived_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM questions x WHERE x.quiz_id = q.quiz_id)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, quizId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("This quiz is no longer available. Refresh the quiz list.");
                }
            }
        }
    }

    private long startAttempt(Connection connection, long studentId, long quizId) throws SQLException {
        String sql = "INSERT INTO quiz_attempts (quiz_id, student_id, started_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, quizId);
            statement.setLong(2, studentId);
            statement.setString(3, LocalDateTime.now(clock).format(SQL_TIME));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void checkQuestionInQuiz(Connection connection, long questionId, long quizId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT question_id FROM questions WHERE question_id = ? AND quiz_id = ?")) {
            statement.setLong(1, questionId);
            statement.setLong(2, quizId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Question does not belong to this quiz.");
                }
            }
        }
    }

    /** Inserts the answer, or replaces the earlier answer to the same question. */
    private void saveAnswer(Connection connection, long attemptId, long questionId, long quizId, String answer)
            throws SQLException {
        String sql = "INSERT INTO student_answers (attempt_id, question_id, quiz_id, selected_answer) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (attempt_id, question_id) DO UPDATE SET selected_answer = excluded.selected_answer";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, attemptId);
            statement.setLong(2, questionId);
            statement.setLong(3, quizId);
            statement.setString(4, answer);
            statement.executeUpdate();
        }
    }

    private boolean isPastDeadline(AttemptInfo info) {
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isBefore(info.deadline());
    }

    /** Loads the attempt; a student can only open their own attempts. */
    private AttemptInfo attemptInfo(Connection connection, AuthenticatedUser student, long attemptId) throws SQLException {
        String sql = "SELECT q.quiz_id, q.title, a.started_at, q.time_limit_minutes, a.status "
                + "FROM quiz_attempts a JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "WHERE a.attempt_id = ? AND a.student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, attemptId);
            statement.setLong(2, student.id());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SecurityException("This attempt is not available to your account.");
                }
                LocalDateTime started = LocalDateTime.parse(result.getString("started_at"), SQL_TIME);
                LocalDateTime deadline = started.plusMinutes(result.getInt("time_limit_minutes"));
                boolean submitted = result.getString("status").equals("submitted");
                return new AttemptInfo(result.getLong("quiz_id"), result.getString("title"), started, deadline, submitted);
            }
        }
    }

    /**
     * Scores the attempt and marks it submitted. Each correct answer earns that question's points;
     * unanswered questions earn zero. The submit time never goes past the deadline.
     */
    private void finish(Connection connection, long attemptId, AttemptInfo info) throws SQLException {
        String scoreSql = "INSERT INTO quiz_results (attempt_id, score, total_points) "
                + "SELECT ?, "
                + "COALESCE(SUM(CASE WHEN a.selected_answer = q.correct_answer THEN q.points ELSE 0 END), 0), "
                + "SUM(q.points) "
                + "FROM questions q "
                + "LEFT JOIN student_answers a ON a.question_id = q.question_id AND a.attempt_id = ? "
                + "WHERE q.quiz_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(scoreSql)) {
            statement.setLong(1, attemptId);
            statement.setLong(2, attemptId);
            statement.setLong(3, info.quizId());
            statement.executeUpdate();
        }

        LocalDateTime submittedAt = LocalDateTime.now(clock);
        if (submittedAt.isAfter(info.deadline())) {
            submittedAt = info.deadline();
        }
        if (submittedAt.isBefore(info.started())) {
            submittedAt = info.started();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE quiz_attempts SET status = 'submitted', submitted_at = ? WHERE attempt_id = ?")) {
            statement.setString(1, submittedAt.format(SQL_TIME));
            statement.setLong(2, attemptId);
            statement.executeUpdate();
        }
    }

    /** The attempt with its questions in order, saved answers, and the score once submitted. */
    private Attempt readAttempt(Connection connection, AuthenticatedUser student, long attemptId) throws SQLException {
        AttemptInfo info = attemptInfo(connection, student, attemptId);

        List<Question> questions = new ArrayList<>();
        String questionSql = "SELECT q.question_id, q.question_text, q.option_a, q.option_b, q.option_c, q.option_d, "
                + "q.points, a.selected_answer "
                + "FROM questions q "
                + "LEFT JOIN student_answers a ON a.question_id = q.question_id AND a.attempt_id = ? "
                + "WHERE q.quiz_id = ? ORDER BY q.question_order, q.question_id";
        try (PreparedStatement statement = connection.prepareStatement(questionSql)) {
            statement.setLong(1, attemptId);
            statement.setLong(2, info.quizId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    List<String> options = List.of(
                            result.getString("option_a"),
                            result.getString("option_b"),
                            result.getString("option_c"),
                            result.getString("option_d"));
                    questions.add(new Question(
                            result.getLong("question_id"),
                            result.getString("question_text"),
                            options,
                            result.getInt("points"),
                            result.getString("selected_answer")));
                }
            }
        }

        Score score = null;
        if (info.submitted()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT score, total_points FROM quiz_results WHERE attempt_id = ?")) {
                statement.setLong(1, attemptId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Submitted attempt has no result");
                    }
                    score = new Score(result.getLong("score"), result.getLong("total_points"));
                }
            }
        }
        return new Attempt(attemptId, info.quizId(), info.title(), info.deadline(), questions, score);
    }

    // ---------- Locking ----------

    /** Takes SQLite's write lock before reading, so concurrent starts and submits cannot race. */
    private static void beginWrite(Connection connection) throws SQLException {
        execute(connection, "PRAGMA busy_timeout = 5000");
        execute(connection, "PRAGMA foreign_keys = ON");
        execute(connection, "BEGIN IMMEDIATE");
    }

    private static void rollback(Connection connection) {
        try {
            execute(connection, "ROLLBACK");
        } catch (SQLException ignored) {
            // Keep the original error; it explains why the action failed.
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
