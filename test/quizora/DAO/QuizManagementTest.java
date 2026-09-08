package quizora.DAO;

import java.sql.*;
import java.util.*;
import quizora.auth.*;
import quizora.database.databaseConnection;
import quizora.model.*;

public class QuizManagementTest {
    public static void main(String[] args)throws Exception{
        var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();
        var dao=new QuizManagementDAO();long subject=0,quiz=0,attempt=0;String tag="quiz-test-"+UUID.randomUUID();
        try(var c=databaseConnection.getConnection()){
            try{
                subject=insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag);
                long teacher=dao.load(admin).teachers().getFirst().id();
                var changes=new QuizChanges(tag,"Description",subject,teacher,30,"published");
                var question=new QuizQuestion(0,"2 + 2?","4","3","2","1","A",2);
                try{dao.save(null,null,changes,List.of(question));throw new AssertionError("Anonymous write allowed");}catch(SecurityException expected){}
                try{dao.save(admin,null,changes,List.of());throw new AssertionError("Empty published quiz allowed");}catch(IllegalArgumentException expected){}
                var created=dao.save(admin,null,changes,List.of(question));quiz=created.id();
                require(created.questions()==1&&created.points()==2,"Question totals saved");
                var details=dao.details(admin,quiz);require(details.questions().getFirst().answer().equals("A"),"Answer saved");
                var edited=dao.save(admin,details,new QuizChanges(tag+" edited","Updated",subject,teacher,45,"published"),details.questions());
                try{dao.save(admin,details,changes,details.questions());throw new AssertionError("Stale edit allowed");}catch(SQLException expected){require("40001".equals(expected.getSQLState()),"Stale check");}
                long student;try(var s=c.prepareStatement("SELECT user_id FROM users WHERE role='student' LIMIT 1");var r=s.executeQuery()){r.next();student=r.getLong(1);}
                attempt=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id) VALUES(?,?)",quiz,student);
                details=dao.details(admin,quiz);
                try{dao.save(admin,details,QuizChanges.from(details.quiz()),List.of(question));throw new AssertionError("Attempt question changes allowed");}catch(IllegalArgumentException expected){}
                var closed=dao.save(admin,details,new QuizChanges(tag,"Closed",subject,teacher,45,"closed"),details.questions());
                var archived=dao.archive(admin,closed);require(archived.archived()&&archived.state().equals("closed"),"Archive state");
                require(dao.details(admin,quiz).questions().equals(details.questions()),"Archive preserves question IDs and scoring");
                require(archived.attempts()==1,"Attempt preserved");
                try{dao.save(admin,dao.details(admin,quiz),changes,List.of(question));throw new AssertionError("Archived edit allowed");}catch(IllegalArgumentException expected){}
                System.out.println("PASS: quiz creation, questions, validation, authorization, stale edits, attempt protection and archive preservation.");
            }finally{
                delete(c,"DELETE FROM quiz_attempts WHERE attempt_id=?",attempt);delete(c,"DELETE FROM questions WHERE quiz_id=?",quiz);delete(c,"DELETE FROM quizzes WHERE quiz_id=?",quiz);delete(c,"DELETE FROM subjects WHERE subject_id=?",subject);
            }
        }
    }
    static long insert(Connection c,String sql,Object... values)throws SQLException{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}}
    static void delete(Connection c,String sql,long id)throws SQLException{if(id==0)return;try(var s=c.prepareStatement(sql)){s.setLong(1,id);s.executeUpdate();}}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
