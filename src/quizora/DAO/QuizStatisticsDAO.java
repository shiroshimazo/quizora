package quizora.DAO;

import java.sql.*;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.QuizStatisticsData;
import quizora.model.QuizStatisticsData.*;

public final class QuizStatisticsDAO {
    public QuizStatisticsData load(AuthenticatedUser teacher)throws SQLException {
        try(var c=databaseConnection.getConnection()) {
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            QuizManagementDAO.requireTeacher(c,teacher);
            var data=read(c,teacher.id());c.commit();return data;
        }
    }
    QuizStatisticsData read(Connection c,long teacherId)throws SQLException {
        java.time.LocalDate today;
        try(var s=c.prepareStatement("SELECT CURRENT_DATE")){s.setQueryTimeout(10);try(var r=s.executeQuery()){r.next();today=r.getDate(1).toLocalDate();}}
        var quizzes=new ArrayList<Summary>();
        try(var s=c.prepareStatement("""
            SELECT q.quiz_id,q.title,s.subject_name,q.status,q.archived_at,
                   COUNT(a.attempt_id) attempts,
                   COALESCE(SUM(a.status='submitted'),0) submitted,
                   COUNT(r.result_id) scored,
                   AVG(100.0*r.score/NULLIF(r.total_points,0)) average_score
            FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id
            LEFT JOIN quiz_attempts a ON a.quiz_id=q.quiz_id
            LEFT JOIN quiz_results r ON r.attempt_id=a.attempt_id AND a.status='submitted'
                AND a.submitted_at IS NOT NULL AND r.total_points>0
            WHERE q.teacher_id=?
            GROUP BY q.quiz_id,q.title,s.subject_name,q.status,q.archived_at
            ORDER BY q.quiz_id DESC
            """)) {
            s.setQueryTimeout(15);s.setLong(1,teacherId);
            try(var r=s.executeQuery()){while(r.next()){
                double average=r.getDouble("average_score");Double score=r.wasNull()?null:average;
                quizzes.add(new Summary(r.getLong("quiz_id"),r.getString("title"),r.getString("subject_name"),r.getString("status"),r.getTimestamp("archived_at")!=null,r.getLong("attempts"),r.getLong("submitted"),r.getLong("scored"),score));
            }}
        }
        var days=new ArrayList<Daily>();
        try(var s=c.prepareStatement("""
            SELECT q.quiz_id,DATE(a.submitted_at) day,COUNT(*) total
            FROM quiz_attempts a JOIN quizzes q ON q.quiz_id=a.quiz_id
            WHERE q.teacher_id=? AND a.status='submitted' AND a.submitted_at>=? AND a.submitted_at<?
            GROUP BY q.quiz_id,DATE(a.submitted_at) ORDER BY day,q.quiz_id
            """)) {
            s.setQueryTimeout(15);s.setLong(1,teacherId);s.setDate(2,java.sql.Date.valueOf(today.minusDays(13)));s.setDate(3,java.sql.Date.valueOf(today.plusDays(1)));
            try(var r=s.executeQuery()){while(r.next())days.add(new Daily(r.getLong(1),r.getDate(2).toLocalDate(),r.getLong(3)));}
        }
        return new QuizStatisticsData(today,List.copyOf(quizzes),List.copyOf(days));
    }
}
