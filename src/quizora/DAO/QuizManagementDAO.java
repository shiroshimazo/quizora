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
import quizora.model.QuizChanges;
import quizora.model.QuizChoice;
import quizora.model.QuizDetails;
import quizora.model.QuizQuestion;
import quizora.model.QuizRecord;

/** Creates, reads, edits and archives quizzes and their questions. Used by admin Quiz Management and teacher Create Quiz. */
public final class QuizManagementDAO {

    /** Everything the quiz form needs: the quiz list and the subject and teacher drop-downs. */
    public record Data(List<QuizRecord> quizzes, List<QuizChoice> subjects, List<QuizChoice> teachers) { }

    /** Every quiz with its subject, teacher, question count, total points and attempt count. */
    private static final String SELECT_QUIZ = "SELECT q.*, s.subject_name, u.full_name, "
            + "(SELECT COUNT(*) FROM questions x WHERE x.quiz_id = q.quiz_id) AS question_count, "
            + "(SELECT COALESCE(SUM(points), 0) FROM questions x WHERE x.quiz_id = q.quiz_id) AS points, "
            + "(SELECT COUNT(*) FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id) AS attempts "
            + "FROM quizzes q "
            + "JOIN subjects s ON s.subject_id = q.subject_id "
            + "JOIN users u ON u.user_id = q.teacher_id ";

    // ---------- Reading ----------

    public Data load(AuthenticatedUser admin) throws SQLException {
        try (Connection connection = databaseConnection.getConnection()) {
            AccessCheck.requireAdmin(connection, admin);
            List<QuizRecord> quizzes = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_QUIZ + "ORDER BY q.quiz_id DESC");
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    quizzes.add(readQuiz(result));
                }
            }
            List<QuizChoice> subjects = choices(connection,
                    "SELECT subject_id, subject_name FROM subjects WHERE archived_at IS NULL ORDER BY subject_name");
            List<QuizChoice> teachers = choices(connection,
                    "SELECT user_id, full_name FROM users "
                    + "WHERE role = 'teacher' AND is_active = 1 AND archived_at IS NULL ORDER BY full_name");
            return new Data(quizzes, subjects, teachers);
        }
    }

    /** The subjects a teacher may create quizzes for: their active assigned subjects. */
    public Data teacherCreationData(AuthenticatedUser teacher) throws SQLException {
        try (Connection connection = databaseConnection.getConnection()) {
            AccessCheck.requireTeacher(connection, teacher);
            List<QuizChoice> subjects = new ArrayList<>();
            String sql = "SELECT s.subject_id, s.subject_name FROM teacher_subjects ts "
                    + "JOIN subjects s ON s.subject_id = ts.subject_id "
                    + "WHERE ts.teacher_id = ? AND s.archived_at IS NULL ORDER BY s.subject_name";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, teacher.id());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        subjects.add(new QuizChoice(result.getLong("subject_id"), result.getString("subject_name")));
                    }
                }
            }
            List<QuizChoice> teachers = new ArrayList<>();
            teachers.add(new QuizChoice(teacher.id(), teacher.fullName()));
            return new Data(new ArrayList<>(), subjects, teachers);
        }
    }

    public QuizDetails details(AuthenticatedUser admin, long quizId) throws SQLException {
        try (Connection connection = databaseConnection.getConnection()) {
            AccessCheck.requireAdmin(connection, admin);
            return new QuizDetails(findQuiz(connection, quizId), findQuestions(connection, quizId));
        }
    }

    // ---------- Saving ----------

    /** A teacher can only create a draft or published quiz of their own, for an assigned subject. */
    public QuizRecord createForTeacher(AuthenticatedUser teacher, QuizChanges changes, List<QuizQuestion> questions)
            throws SQLException {
        if (teacher == null || !teacher.role().equals("teacher")) {
            throw new SecurityException("Teacher access is required.");
        }
        if (changes.teacherId() != teacher.id()) {
            throw new SecurityException("You can only create your own quizzes.");
        }
        if (!changes.state().equals("draft") && !changes.state().equals("published")) {
            throw new IllegalArgumentException("Create a draft or published quiz.");
        }
        return saveQuiz(teacher, null, changes, questions, true);
    }

    /** Admin add (original is null) or edit (original is the quiz shown in the form). */
    public QuizRecord save(AuthenticatedUser admin, QuizDetails original, QuizChanges changes, List<QuizQuestion> questions)
            throws SQLException {
        return saveQuiz(admin, original, changes, questions, false);
    }

    private QuizRecord saveQuiz(AuthenticatedUser user, QuizDetails original, QuizChanges changes,
            List<QuizQuestion> questions, boolean teacherCreation) throws SQLException {
        if (changes == null) {
            throw new NullPointerException("Quiz details are required.");
        }
        List<QuizQuestion> newQuestions = new ArrayList<>(questions);
        if (newQuestions.size() > 500) {
            throw new IllegalArgumentException("A quiz can contain at most 500 questions.");
        }
        if (changes.state().equals("published") && newQuestions.isEmpty()) {
            throw new IllegalArgumentException("Add at least one question before publishing.");
        }

        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (teacherCreation) {
                    AccessCheck.requireTeacher(connection, user);
                    checkSubjectAssigned(connection, user.id(), changes.subjectId());
                } else {
                    AccessCheck.requireAdmin(connection, user);
                }

                QuizRecord current = null;
                if (original != null) {
                    current = findQuiz(connection, original.quiz().id());
                    checkCanEdit(connection, current, original, changes, newQuestions);
                }
                checkTeacher(connection, changes, current);
                checkSubject(connection, changes, current);

                long quizId;
                if (original == null) {
                    quizId = insertQuiz(connection, changes);
                } else {
                    quizId = original.quiz().id();
                    updateQuiz(connection, quizId, changes);
                }
                if (original == null || !newQuestions.equals(original.questions())) {
                    replaceQuestions(connection, quizId, newQuestions);
                }

                QuizRecord saved = findQuiz(connection, quizId);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Archiving closes the quiz but keeps its questions, attempts and results. */
    public QuizRecord archive(AuthenticatedUser admin, QuizRecord original) throws SQLException {
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AccessCheck.requireAdmin(connection, admin);
                QuizRecord current = findQuiz(connection, original.id());
                if (!current.equals(original)) {
                    throw new SQLException("Quiz changed. Refresh and try again.", "40001");
                }
                if (current.archived()) {
                    throw new IllegalArgumentException("Quiz is already archived.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE quizzes SET status = 'closed', archived_at = datetime('now', 'localtime') WHERE quiz_id = ?")) {
                    statement.setLong(1, original.id());
                    statement.executeUpdate();
                }
                QuizRecord archived = findQuiz(connection, original.id());
                connection.commit();
                return archived;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    // ---------- Checks used while saving ----------

    private void checkSubjectAssigned(Connection connection, long teacherId, long subjectId) throws SQLException {
        String sql = "SELECT ts.subject_id FROM teacher_subjects ts "
                + "JOIN subjects s ON s.subject_id = ts.subject_id "
                + "WHERE ts.teacher_id = ? AND ts.subject_id = ? AND s.archived_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            statement.setLong(2, subjectId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("This subject is no longer assigned to you or has been archived. Refresh subjects and try again.");
                }
            }
        }
    }

    /** Stops edits to stale or archived quizzes, and keeps scoring fixed once students have attempts. */
    private void checkCanEdit(Connection connection, QuizRecord current, QuizDetails original, QuizChanges changes,
            List<QuizQuestion> newQuestions) throws SQLException {
        List<QuizQuestion> currentQuestions = findQuestions(connection, current.id());
        if (!current.equals(original.quiz()) || !currentQuestions.equals(original.questions())) {
            throw new SQLException("Quiz changed. Close the form, refresh, and try again.", "40001");
        }
        if (current.archived()) {
            throw new IllegalArgumentException("Archived quizzes are read-only.");
        }
        if (current.attempts() > 0) {
            boolean questionsChanged = !newQuestions.equals(original.questions());
            boolean timeChanged = changes.minutes() != current.minutes();
            boolean subjectChanged = changes.subjectId() != current.subjectId();
            boolean teacherChanged = changes.teacherId() != current.teacherId();
            boolean backToDraft = changes.state().equals("draft");
            if (questionsChanged || timeChanged || subjectChanged || teacherChanged || backToDraft) {
                throw new IllegalArgumentException("Quizzes with attempts retain their questions, time limit, subject and teacher, and cannot return to draft.");
            }
        }
    }

    /** The teacher must be active, unless the quiz already belongs to that teacher. */
    private void checkTeacher(Connection connection, QuizChanges changes, QuizRecord current) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE user_id = ? AND role = 'teacher' AND is_active = 1 AND archived_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, changes.teacherId());
            try (ResultSet result = statement.executeQuery()) {
                boolean activeTeacher = result.next();
                boolean sameTeacher = current != null && current.teacherId() == changes.teacherId();
                if (!activeTeacher && !sameTeacher) {
                    throw new IllegalArgumentException("Select an active teacher.");
                }
            }
        }
    }

    /** The subject must exist and be active, unless the quiz already uses that subject. */
    private void checkSubject(Connection connection, QuizChanges changes, QuizRecord current) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT archived_at FROM subjects WHERE subject_id = ?")) {
            statement.setLong(1, changes.subjectId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Subject no longer exists. Refresh and try again.");
                }
                boolean archived = result.getString("archived_at") != null;
                boolean sameSubject = current != null && current.subjectId() == changes.subjectId();
                if (archived && !sameSubject) {
                    throw new IllegalArgumentException("Select an active subject.");
                }
            }
        }
    }

    // ---------- Writing rows ----------

    private long insertQuiz(Connection connection, QuizChanges changes) throws SQLException {
        String sql = "INSERT INTO quizzes (title, description, subject_id, teacher_id, time_limit_minutes, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, changes.title());
            statement.setString(2, changes.description());
            statement.setLong(3, changes.subjectId());
            statement.setLong(4, changes.teacherId());
            statement.setInt(5, changes.minutes());
            statement.setString(6, changes.state());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void updateQuiz(Connection connection, long quizId, QuizChanges changes) throws SQLException {
        String sql = "UPDATE quizzes SET title = ?, description = ?, subject_id = ?, teacher_id = ?, "
                + "time_limit_minutes = ?, status = ?, updated_at = datetime('now', 'localtime') WHERE quiz_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changes.title());
            statement.setString(2, changes.description());
            statement.setLong(3, changes.subjectId());
            statement.setLong(4, changes.teacherId());
            statement.setInt(5, changes.minutes());
            statement.setString(6, changes.state());
            statement.setLong(7, quizId);
            statement.executeUpdate();
        }
    }

    /** Deletes the old questions and saves the new ones in order. */
    private void replaceQuestions(Connection connection, long quizId, List<QuizQuestion> questions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM questions WHERE quiz_id = ?")) {
            statement.setLong(1, quizId);
            statement.executeUpdate();
        }
        String sql = "INSERT INTO questions (quiz_id, question_text, option_a, option_b, option_c, option_d, "
                + "correct_answer, points, question_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int order = 1;
            for (QuizQuestion question : questions) {
                statement.setLong(1, quizId);
                statement.setString(2, question.text());
                statement.setString(3, question.a());
                statement.setString(4, question.b());
                statement.setString(5, question.c());
                statement.setString(6, question.d());
                statement.setString(7, question.answer());
                statement.setInt(8, question.points());
                statement.setInt(9, order);
                statement.executeUpdate();
                order++;
            }
        }
    }

    // ---------- Reading rows ----------

    private QuizRecord findQuiz(Connection connection, long quizId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_QUIZ + "WHERE q.quiz_id = ?")) {
            statement.setLong(1, quizId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Quiz no longer exists. Refresh and try again.", "40001");
                }
                return readQuiz(result);
            }
        }
    }

    private List<QuizQuestion> findQuestions(Connection connection, long quizId) throws SQLException {
        List<QuizQuestion> questions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM questions WHERE quiz_id = ? ORDER BY question_order")) {
            statement.setLong(1, quizId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    questions.add(new QuizQuestion(
                            result.getLong("question_id"),
                            result.getString("question_text"),
                            result.getString("option_a"),
                            result.getString("option_b"),
                            result.getString("option_c"),
                            result.getString("option_d"),
                            result.getString("correct_answer"),
                            result.getInt("points")));
                }
            }
        }
        return questions;
    }

    private List<QuizChoice> choices(Connection connection, String sql) throws SQLException {
        List<QuizChoice> choices = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                choices.add(new QuizChoice(result.getLong(1), result.getString(2)));
            }
        }
        return choices;
    }

    private QuizRecord readQuiz(ResultSet result) throws SQLException {
        String description = result.getString("description");
        return new QuizRecord(
                result.getLong("quiz_id"),
                result.getLong("subject_id"),
                result.getString("subject_name"),
                result.getLong("teacher_id"),
                result.getString("full_name"),
                result.getString("title"),
                description == null ? "" : description,
                result.getInt("time_limit_minutes"),
                result.getString("status"),
                result.getString("archived_at") != null,
                result.getInt("question_count"),
                result.getLong("points"),
                result.getInt("attempts"),
                result.getTimestamp("updated_at").toLocalDateTime());
    }
}
