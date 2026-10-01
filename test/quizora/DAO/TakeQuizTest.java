package quizora.DAO;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.concurrent.*;
import quizora.auth.AuthenticatedUser;

public class TakeQuizTest {
    public static final AuthenticatedUser STUDENT = new AuthenticatedUser(1,"Student","student");
    public static final AuthenticatedUser OTHER = new AuthenticatedUser(3,"Other","student");
    public static class Fixture implements AutoCloseable {
        public final Path database;
        public final String url;
        public Fixture() throws Exception {
            database = Files.createTempFile("quizora-attempt-test-", ".db"); url = "jdbc:sqlite:"+database;
            String schema = Files.readString(Path.of("src/quizora/database/schema.sql"));
            schema = schema.substring(0,schema.indexOf("CREATE TRIGGER")).replaceAll("(?m)--[^\\r\\n]*", "");
            try (var connection=connect(); var s=connection.createStatement()) {
                for (String sql:schema.split(";")) if(!sql.isBlank()) s.execute(sql);
                s.execute("INSERT INTO users(user_id,full_name,username,email,password_hash,role) VALUES(1,'Student','student','s@example.com','unused','student'),(2,'Teacher','teacher','t@example.com','unused','teacher'),(3,'Other','other','o@example.com','unused','student')");
                s.execute("INSERT INTO subjects(subject_id,subject_name) VALUES(1,'Math')");
                s.execute("INSERT INTO quizzes(quiz_id,subject_id,teacher_id,title,time_limit_minutes,status) VALUES(1,1,2,'Algebra',1,'published'),(2,1,2,'Draft',1,'draft'),(3,1,2,'Empty',1,'published'),(4,1,2,'Closed',1,'closed')");
                s.execute("INSERT INTO questions(question_id,quiz_id,question_text,option_a,option_b,option_c,option_d,correct_answer,points,question_order) VALUES(1,1,'2 + 2?','4','3','2','1','A',3,1),(2,1,'3 + 3?','5','6','7','8','B',2,2),(3,2,'Draft question','1','2','3','4','A',1,1)");
            }
        }
        public Connection connect() throws SQLException { return DriverManager.getConnection(url); }
        public void sql(String sql) throws SQLException { try(var c=connect();var s=c.createStatement()) {s.execute(sql);} }
        public long count(String sql) throws SQLException { try(var c=connect();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);} }
        public void close() throws Exception { Files.deleteIfExists(database); }
    }
    private static class MutableClock extends Clock {
        Instant time=Instant.parse("2026-10-01T10:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return time;}
        void seconds(long value){time=time.plusSeconds(value);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @FunctionalInterface interface Action { void run() throws Exception; }
    private static void reject(Action action) throws Exception {
        try {action.run();throw new AssertionError("Expected rejection");}
        catch(IllegalArgumentException|SecurityException expected) { }
    }
    public static void main(String[] args) throws Exception {
        try(var fixture=new Fixture()) {
            var clock=new MutableClock();var dao=new TakeQuizDAO(fixture::connect,clock);
            check(dao.choices(STUDENT).size()==1,"Only available quizzes listed");
            reject(()->dao.open(STUDENT,2));reject(()->dao.open(STUDENT,3));reject(()->dao.open(STUDENT,4));
            reject(()->dao.open(new AuthenticatedUser(2,"Teacher","teacher"),1));
            reject(()->dao.open(new AuthenticatedUser(2,"Forged","student"),1));
            reject(()->dao.choices(null));
            var attempt=dao.open(STUDENT,1);
            check(attempt.questions().size()==2 && attempt.questions().getFirst().answer()==null,"Ordered unanswered questions");
            check(attempt.deadline().equals(LocalDateTime.of(2026,10,1,10,1)),"One-minute deadline");
            check(dao.open(STUDENT,1).id()==attempt.id(),"Resume existing attempt");
            reject(()->dao.save(OTHER,attempt.id(),1,"A"));reject(()->dao.submit(OTHER,attempt.id()));
            reject(()->dao.save(STUDENT,attempt.id(),3,"A"));reject(()->dao.save(STUDENT,attempt.id(),1,"E"));
            dao.save(STUDENT,attempt.id(),1,"A");
            var resumed=new TakeQuizDAO(fixture::connect,clock).open(STUDENT,1);
            check(resumed.questions().getFirst().answer().equals("A") && resumed.deadline().equals(attempt.deadline()),"Saved answer and deadline survive reload");
            dao.save(STUDENT,attempt.id(),1,null);check(dao.open(STUDENT,1).questions().getFirst().answer()==null,"Clear saved answer");
            dao.save(STUDENT,attempt.id(),1,"A");clock.seconds(59);
            check(dao.save(STUDENT,attempt.id(),2,"A").score()==null,"Answer accepted before deadline");clock.seconds(1);
            var expired=dao.save(STUDENT,attempt.id(),2,"B");
            check(expired.score().earned()==3 && expired.score().possible()==5,"Exact deadline rejects late correct answer and grades saved answers");
            check(dao.submit(STUDENT,attempt.id()).score().earned()==3,"Submit is idempotent");
            check(dao.save(STUDENT,attempt.id(),1,"D").score().earned()==3,"Submitted answers immutable");
            check(fixture.count("SELECT COUNT(*) FROM quiz_results")==1,"One result per attempt");
            var next=dao.open(STUDENT,1);clock.seconds(60);
            check(dao.open(STUDENT,1).score().earned()==0,"Expired resume submits unanswered attempt");
            var active=dao.open(STUDENT,1);
            fixture.sql("UPDATE quizzes SET status='closed' WHERE quiz_id=1");
            check(dao.choices(STUDENT).getFirst().resume(),"Closed quiz still resumes existing attempt");
            check(dao.open(STUDENT,1).id()==active.id(),"Closure does not discard started attempt");
            fixture.sql("CREATE TRIGGER reject_result BEFORE INSERT ON quiz_results BEGIN SELECT RAISE(ABORT,'test failure'); END");
            try {dao.submit(STUDENT,active.id());throw new AssertionError("Expected database failure");}catch(SQLException expected){}
            check(fixture.count("SELECT COUNT(*) FROM quiz_attempts WHERE attempt_id="+active.id()+" AND status='in_progress'")==1,"Failed grading rolls back submission");
            fixture.sql("DROP TRIGGER reject_result");dao.submit(STUDENT,active.id());reject(()->dao.open(STUDENT,1));
            fixture.sql("UPDATE quizzes SET status='published' WHERE quiz_id=1");
            try(var executor=Executors.newFixedThreadPool(2)) {
                var a=executor.submit(()->dao.open(STUDENT,1));var b=executor.submit(()->dao.open(STUDENT,1));
                check(a.get().id()==b.get().id(),"Concurrent starts resume same attempt");
                long id=a.get().id();
                var x=executor.submit(()->dao.submit(STUDENT,id));var y=executor.submit(()->dao.submit(STUDENT,id));
                check(x.get().score().equals(y.get().score()),"Concurrent submit returns same result");
            }
            fixture.sql("UPDATE users SET is_active=0 WHERE user_id=1");reject(()->dao.choices(STUDENT));reject(()->dao.submit(STUDENT,attempt.id()));
            System.out.println("Take quiz DAO checks passed: availability, access, resume, save, clear, deadlines, scoring, rollback, concurrency, idempotency.");
        }
    }
}
