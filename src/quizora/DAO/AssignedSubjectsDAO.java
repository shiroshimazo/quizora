package quizora.DAO;

import java.sql.*;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AssignedSubject;

public final class AssignedSubjectsDAO {
    public List<AssignedSubject> load(AuthenticatedUser teacher)throws SQLException {
        try(var c=databaseConnection.getConnection()) {
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            QuizManagementDAO.requireTeacher(c,teacher);
            var result=readAssignments(c,teacher.id());c.commit();return result;
        }
    }
    List<AssignedSubject> readAssignments(Connection c,long teacherId)throws SQLException {
        var subjects=new ArrayList<AssignedSubject>();
        try(var s=c.prepareStatement("""
            SELECT s.subject_id,s.subject_name,COALESCE(s.category,'') category,
                   COALESCE(s.description,'') description,COUNT(q.quiz_id) quizzes,
                   COALESCE(SUM(q.status='published'),0) published
            FROM teacher_subjects ts JOIN subjects s ON s.subject_id=ts.subject_id
            LEFT JOIN quizzes q ON q.subject_id=s.subject_id AND q.teacher_id=ts.teacher_id AND q.archived_at IS NULL
            WHERE ts.teacher_id=? AND s.archived_at IS NULL
            GROUP BY s.subject_id,s.subject_name,s.category,s.description
            ORDER BY s.subject_name,s.subject_id
            """)) {
            s.setQueryTimeout(10);s.setLong(1,teacherId);
            try(var r=s.executeQuery()){while(r.next())subjects.add(new AssignedSubject(r.getLong("subject_id"),r.getString("subject_name"),r.getString("category"),r.getString("description"),r.getLong("quizzes"),r.getLong("published")));}
        }
        return List.copyOf(subjects);
    }
}
