package quizora.DAO;
import java.sql.*;
import java.util.*;
import quizora.auth.*;
import quizora.database.databaseConnection;
public class ResultsTest {
 public static void main(String[]args)throws Exception{
  var dao=new ResultsDAO();try{dao.load(null);throw new AssertionError("Anonymous allowed");}catch(SecurityException expected){}
  try(var c=databaseConnection.getConnection()){c.setAutoCommit(false);try{
   String tag="result-"+UUID.randomUUID();long student=insert(c,"INSERT INTO users(full_name,username,email,password_hash,role,archived_at) VALUES('Archived Student',?,?,'unused','student',CURRENT_TIMESTAMP)",tag,tag+"@example.invalid");
   long teacher;try(var s=c.prepareStatement("SELECT user_id FROM users WHERE role='teacher' LIMIT 1");var r=s.executeQuery()){r.next();teacher=r.getLong(1);}
   long subject=insert(c,"INSERT INTO subjects(subject_name,archived_at) VALUES(?,CURRENT_TIMESTAMP)",tag);
   long quiz=insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status,archived_at) VALUES(?,?,'Archived Quiz','closed',CURRENT_TIMESTAMP)",subject,teacher);
   long scored=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quiz,student);
   insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,3,4)",scored);
   long unfinished=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id) VALUES(?,?)",quiz,student);
   long unscored=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quiz,student);
   var rows=dao.read(c);var result=rows.stream().filter(r->r.attemptId()==scored).findFirst().orElseThrow();
   require(result.percentage()==75&&result.passed(75)&&!result.passed(76),"Normalized score and inclusive threshold");
   require(result.subjectId()==subject&&result.studentId()==student&&result.quizId()==quiz,"Archived identities preserved");
   require(rows.stream().noneMatch(r->r.attemptId()==unfinished||r.attemptId()==unscored),"Unfinished and unscored excluded");
   System.out.println("PASS: results authorization, finalized-only selection, archived history, scores and threshold boundaries.");
  }finally{c.rollback();}}
 }
 static long insert(Connection c,String sql,Object... values)throws SQLException{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
