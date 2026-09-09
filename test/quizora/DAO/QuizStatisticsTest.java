package quizora.DAO;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

public class QuizStatisticsTest {
 public static void main(String[] args)throws Exception {
  var dao=new QuizStatisticsDAO();
  try{dao.load(null);throw new AssertionError("Anonymous access");}catch(SecurityException expected){}
  for(var user:List.of(new AuthenticatedUser(1,"Admin","admin"),new AuthenticatedUser(1,"Student","student"),new AuthenticatedUser(4294967295L,"Missing","teacher"))){try{dao.load(user);throw new AssertionError("Invalid access");}catch(SecurityException expected){}}
  try(var c=databaseConnection.getConnection()){c.setAutoCommit(false);try {
   String tag="qs-"+UUID.randomUUID();
   long teacher=ResultsTest.insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Teacher',?,?,'unused','teacher')",tag,tag+"@example.invalid");
   long other=ResultsTest.insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Other',?,?,'unused','teacher')","o"+tag,"o"+tag+"@example.invalid");
   long student=ResultsTest.insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Student',?,?,'unused','student')","s"+tag,"s"+tag+"@example.invalid");
   long subject=ResultsTest.insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag);
   require(dao.read(c,teacher).quizzes().isEmpty(),"No quizzes");
   long quiz=ResultsTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status,archived_at) VALUES(?,?,'Archived','closed',CURRENT_TIMESTAMP)",subject,teacher);
   long empty=ResultsTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title) VALUES(?,?,'Empty')",subject,teacher);
   long otherQuiz=ResultsTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title) VALUES(?,?,'Other')",subject,other);
   long first=ResultsTest.insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quiz,student);
   ResultsTest.insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,1,2)",first);
   long second=ResultsTest.insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,started_at,submitted_at) VALUES(?,?,'submitted',CURRENT_DATE-INTERVAL 13 DAY,CURRENT_DATE-INTERVAL 13 DAY)",quiz,student);
   ResultsTest.insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,9,10)",second);
   ResultsTest.insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,started_at,submitted_at) VALUES(?,?,'submitted',CURRENT_DATE-INTERVAL 14 DAY,CURRENT_DATE-INTERVAL 14 DAY)",quiz,student);
   long unfinished=ResultsTest.insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id) VALUES(?,?)",quiz,student);
   ResultsTest.insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,0,10)",unfinished);
   ResultsTest.insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",otherQuiz,student);
   var snapshot=dao.read(c,teacher);require(snapshot.quizzes().size()==2,"Teacher ownership");
   var row=snapshot.quizzes().stream().filter(q->q.id()==quiz).findFirst().orElseThrow();
   require(row.archived()&&row.attempts()==4&&row.submitted()==3&&row.scored()==2,"Archive, retakes, unscored and unfinished attempts");
   require(Math.abs(row.average()-70)<0.001,"Mean percentages, not pooled points");
   var zero=snapshot.quizzes().stream().filter(q->q.id()==empty).findFirst().orElseThrow();require(zero.attempts()==0&&zero.average()==null,"Zero attempts and unavailable score");
   require(snapshot.submissionsByDay().size()==2&&snapshot.submissionsByDay().stream().mapToLong(d->d.count()).sum()==2,"Fourteen-day boundary excludes older attempts and other teacher");
   require(snapshot.submissionsByDay().stream().anyMatch(d->d.date().equals(snapshot.today().minusDays(13))),"First included day");
   System.out.println("PASS: statistics authorization, ownership, archive history, empty quizzes, normalized scores and date boundaries.");
  }finally{c.rollback();}}
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
