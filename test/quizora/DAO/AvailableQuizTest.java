package quizora.DAO;

import java.nio.file.*;
import java.sql.*;
import quizora.auth.AuthenticatedUser;

public class AvailableQuizTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path database = Files.createTempFile("quizora-catalogue-", ".db");
        String url = "jdbc:sqlite:" + database;
        try {
            try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
                String schema = Files.readString(Path.of("src/quizora/database/schema.sql"));
                schema = schema.substring(0, schema.indexOf("CREATE TRIGGER"));
                schema = schema.replaceAll("(?m)--[^\\r\\n]*", "");
                for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
                statement.execute("INSERT INTO users(user_id,full_name,username,email,password_hash,role) VALUES (1,'Student','student','s@example.com','unused','student'),(2,'Teacher','teacher','t@example.com','unused','teacher'),(3,'Other','other','o@example.com','unused','student')");
                statement.execute("INSERT INTO subjects(subject_id,subject_name) VALUES(1,'Mathematics'),(2,'Archived subject')");
                statement.execute("UPDATE subjects SET archived_at='2026-01-01' WHERE subject_id=2");
                for (int id = 1; id <= 12; id++) {
                    statement.execute("INSERT INTO quizzes(quiz_id,subject_id,teacher_id,title,status) VALUES(" + id + "," + (id == 12 ? 2 : 1) + ",2,'Quiz " + id + "','" + (id == 9 ? "draft" : id == 10 ? "closed" : "published") + "')");
                    if (id != 8) statement.execute("INSERT INTO questions(quiz_id,question_text,option_a,option_b,option_c,option_d,correct_answer,question_order,points) VALUES(" + id + ",'Question','A','B','C','D','A',1,3)");
                }
                statement.execute("UPDATE quizzes SET archived_at='2026-01-01' WHERE quiz_id=11");
                statement.execute("INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(1,1,'submitted',datetime('now','localtime')),(1,3,'submitted',datetime('now','localtime'))");
                statement.execute("INSERT INTO quiz_attempts(quiz_id,student_id) VALUES(2,1),(3,3)");
            }
            var dao = new AvailableQuizDAO(() -> DriverManager.getConnection(url));
            var student = new AuthenticatedUser(1,"Student","student");
            var quizzes = dao.load(student);
            check(quizzes.size() == 7, "Full catalogue exceeds overview limit; exclude unavailable quizzes");
            var first = quizzes.stream().filter(q -> q.id()==1).findFirst().orElseThrow();
            check(first.attempts() == 1 && first.points() == 3 && first.questions() == 1, "Own attempts and quiz totals");
            check(quizzes.stream().filter(q -> q.id()==2).findFirst().orElseThrow().inProgress(), "Own active attempt");
            check(!quizzes.stream().filter(q -> q.id()==3).findFirst().orElseThrow().inProgress(), "Other student's progress remains private");
            for (var identity : new AuthenticatedUser[]{null,new AuthenticatedUser(2,"Teacher","teacher"),new AuthenticatedUser(2,"Forged","student")}) {
                try { dao.load(identity); throw new AssertionError("Unauthorized access accepted"); }
                catch (SecurityException expected) { }
            }
            try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
                statement.execute("UPDATE users SET is_active=0 WHERE user_id=1");
            }
            try { dao.load(student); throw new AssertionError("Inactive student accepted"); }
            catch (SecurityException expected) { }
            System.out.println("Available quiz DAO checks passed: availability, full list, totals, private progress, student access.");
        } finally { Files.deleteIfExists(database); }
    }
}
