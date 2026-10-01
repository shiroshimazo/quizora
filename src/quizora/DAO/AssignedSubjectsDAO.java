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
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AssignedSubject;

/** The subjects an admin assigned to the signed-in teacher, with that teacher's quiz counts. */
public final class AssignedSubjectsDAO {

    public List<AssignedSubject> load(AuthenticatedUser teacher) throws SQLException {
        List<AssignedSubject> subjects = new ArrayList<>();
        String sql = "SELECT s.subject_id, s.subject_name, "
                + "COALESCE(s.category, '') AS category, COALESCE(s.description, '') AS description, "
                + "COUNT(q.quiz_id) AS quizzes, COALESCE(SUM(q.status = 'published'), 0) AS published "
                + "FROM teacher_subjects ts "
                + "JOIN subjects s ON s.subject_id = ts.subject_id "
                + "LEFT JOIN quizzes q ON q.subject_id = s.subject_id AND q.teacher_id = ts.teacher_id "
                + "AND q.archived_at IS NULL "
                + "WHERE ts.teacher_id = ? AND s.archived_at IS NULL "
                + "GROUP BY s.subject_id, s.subject_name, s.category, s.description "
                + "ORDER BY s.subject_name, s.subject_id";
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            AccessCheck.requireTeacher(connection, teacher);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, teacher.id());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        subjects.add(new AssignedSubject(
                                result.getLong("subject_id"),
                                result.getString("subject_name"),
                                result.getString("category"),
                                result.getString("description"),
                                result.getLong("quizzes"),
                                result.getLong("published")));
                    }
                }
            }
        }
        return subjects;
    }
}
