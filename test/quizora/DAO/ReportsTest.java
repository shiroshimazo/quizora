package quizora.DAO;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import quizora.auth.*;
import quizora.database.databaseConnection;
import quizora.model.*;
public class ReportsTest {
 public static void main(String[] args)throws Exception{
  ReportsDAO dao=new ReportsDAO();var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();
  try{dao.load(null,75);throw new AssertionError("Anonymous report allowed");}catch(SecurityException expected){}
  try{dao.load(new AuthenticatedUser(admin.id(),"fake","student"),75);throw new AssertionError("Student report allowed");}catch(SecurityException expected){}
  try{dao.load(admin,101);throw new AssertionError("Invalid threshold allowed");}catch(IllegalArgumentException expected){}
  try(var c=databaseConnection.getConnection()){
   c.setAutoCommit(false);try{
    var baseline=dao.snapshot(c,75);String tag="reports-"+UUID.randomUUID();
    long student=insert(c,"INSERT INTO users(full_name,username,email,password_hash,role,is_active,archived_at) VALUES('Report Student',?,?,'unused','student',TRUE,CURRENT_TIMESTAMP)",tag,tag+"@example.invalid");
    long teacher=insert(c,"INSERT INTO users(full_name,username,email,password_hash,role,is_active) VALUES('Report Teacher',?,?,'unused','teacher',FALSE)","t"+tag,"t"+tag+"@example.invalid");
    long subject=insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag);
    long quiz=insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title) VALUES(?,?,'Report test')",subject,teacher);
    for(int score:new int[]{1,2}){long attempt=insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quiz,student);insert(c,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,?,2)",attempt,score);}
    insert(c,"INSERT INTO quiz_attempts(quiz_id,student_id) VALUES(?,?)",quiz,student);
    var report=dao.snapshot(c,75);
    require(report.students().total()==baseline.students().total()+1&&report.students().archived()==baseline.students().archived()+1&&report.students().active()==baseline.students().active(),"Archive count separate from active");
    require(report.teachers().inactive()==baseline.teachers().inactive()+1,"Inactive teacher count");
    require(report.attempts()==baseline.attempts()+3&&report.scored()==baseline.scored()+2,"Attempt versus scored counts");
    double expected=((baseline.average()==null?0:baseline.average()*baseline.scored())+150)/(baseline.scored()+2);
    require(Math.abs(report.average()-expected)<0.001,"Percentage average");
    double pass=((baseline.passRate()==null?0:baseline.passRate()*baseline.scored()/100)+1)*100/(baseline.scored()+2);
    require(Math.abs(report.passRate()-pass)<0.001,"Pass denominator excludes unscored attempts");
    require(dao.snapshot(c,50).passRate()>=report.passRate(),"Adjustable threshold");
    for(String type:List.of("Quiz statistics","Student statistics","Teacher statistics"))ReportPdf.write(Path.of("build",type.replace(' ','-')+".pdf"),report,type);
    var empty=new ReportData(report.generatedAt(),75,new ReportData.Accounts(0,0,0,0),new ReportData.Accounts(0,0,0,0),0,0,0,0,0,0,null,null,null,null);
    require(empty.metrics("Quiz statistics").stream().anyMatch(m->m.label().equals("Average score")&&m.value().equals("N/A")),"No scores is N/A");
    System.out.println("PASS: admin guards, account states, quiz/attempt counts, normalized average, pass rate, empty score display and three PDF exports.");
   }finally{c.rollback();}
  }
 }
 static long insert(Connection c,String sql,Object... values)throws SQLException{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
