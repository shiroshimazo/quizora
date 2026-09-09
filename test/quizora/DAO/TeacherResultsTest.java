package quizora.DAO;
import java.sql.*;
import java.util.*;
import quizora.auth.*;
import quizora.database.databaseConnection;
public class TeacherResultsTest {
 public static void main(String[]args)throws Exception{
  var dao=new ResultsDAO();try{dao.loadForTeacher(null);throw new AssertionError("Anonymous allowed");}catch(SecurityException expected){}
  for(var identity:List.of(new AuthenticatedUser(1,"Admin","admin"),new AuthenticatedUser(1,"Student","student"),new AuthenticatedUser(4294967295L,"Missing","teacher"))) {
   try{dao.loadForTeacher(identity);throw new AssertionError("Wrong role or missing user allowed");}catch(SecurityException expected){}
  }
  try(var c=databaseConnection.getConnection()){c.setAutoCommit(false);try{
   String tag="result-"+UUID.randomUUID();long student=insert(c,"INSERT INTO users(full_name,username,email,password_hash,role,archived_at) VALUES('Archived Student',?,?,'unused','student',CURRENT_TIMESTAMP)",tag,tag+"@example.invalid");
   long teacher=insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Teacher',?,?,'unused','teacher')","t"+tag,"t"+tag+"@example.invalid");
   long other=insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Other',?,?,'unused','teacher')","o"+tag,"o"+tag+"@example.invalid");
   long subject=insert(c,"INSERT INTO subjects(subject_name,archived_at) VALUES(?,CURRENT_TIMESTAMP)",tag);
   long quiz=insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status,archived_at) VALUES(?,?,'Archived Quiz','closed',CURRENT_TIMESTAMP)",subject,teacher);
   long scored=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quiz,student);
   insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,3,4)",scored);
   long unfinished=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id) VALUES(?,?)",quiz,student);
   long unscored=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quiz,student);
   long otherQuiz=insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status) VALUES(?,?,'Other quiz','published')",subject,other);
   long otherAttempt=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",otherQuiz,student);
   insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,1,1)",otherAttempt);
   var rows=dao.readForTeacher(c,teacher);
   require(rows.size()==1&&rows.stream().noneMatch(r->r.attemptId()==otherAttempt),"Other teacher results excluded even with shared subject and student");
   require(dao.readForTeacher(c,other).size()==1,"Other teacher has their own result");
   require(dao.readForTeacher(c,4294967295L).isEmpty(),"No matching quizzes");var result=rows.stream().filter(r->r.attemptId()==scored).findFirst().orElseThrow();
   require(result.percentage()==75&&result.passed(75)&&!result.passed(76),"Normalized score and inclusive threshold");
   require(result.subjectId()==subject&&result.studentId()==student&&result.quizId()==quiz,"Archived identities preserved");
   require(rows.stream().noneMatch(r->r.attemptId()==unfinished||r.attemptId()==unscored),"Unfinished and unscored excluded");
   System.out.println("PASS: teacher ownership, role authorization, finalized-only selection, archived history, scores and threshold boundaries.");
  }finally{c.rollback();}}
 }
 static long insert(Connection c,String sql,Object... values)throws SQLException{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
