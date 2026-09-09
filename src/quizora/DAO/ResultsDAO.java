package quizora.DAO;
import java.sql.*;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.ResultRecord;
public final class ResultsDAO {
 public List<ResultRecord> load(AuthenticatedUser admin)throws SQLException{
  try(var c=databaseConnection.getConnection()){
   c.setReadOnly(true);c.setAutoCommit(false);AccountManagementDAO.requireAdmin(c,admin,false);
   var data=read(c);c.commit();return data;
  }
 }
 public List<ResultRecord> loadForTeacher(AuthenticatedUser teacher)throws SQLException {
  try(var c=databaseConnection.getConnection()) {
   c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
   QuizManagementDAO.requireTeacher(c,teacher);
   var data=readForTeacher(c,teacher.id());c.commit();return data;
  }
 }
 List<ResultRecord> read(Connection c)throws SQLException{return read(c,null);}
 List<ResultRecord> readForTeacher(Connection c,long teacherId)throws SQLException{return read(c,teacherId);}
 private List<ResultRecord> read(Connection c,Long teacherId)throws SQLException{
  var list=new ArrayList<ResultRecord>();
  try(var s=c.prepareStatement("""
   SELECT a.attempt_id,u.user_id,u.full_name,u.username,q.quiz_id,q.title,s.subject_id,s.subject_name,r.score,r.total_points,a.submitted_at
   FROM quiz_results r JOIN quiz_attempts a ON a.attempt_id=r.attempt_id
   JOIN users u ON u.user_id=a.student_id JOIN quizzes q ON q.quiz_id=a.quiz_id
   JOIN subjects s ON s.subject_id=q.subject_id
   WHERE a.status='submitted' AND a.submitted_at IS NOT NULL AND r.total_points>0
   """+(teacherId==null?"":" AND q.teacher_id=? ")+" ORDER BY a.submitted_at DESC,a.attempt_id DESC")){s.setQueryTimeout(15);if(teacherId!=null)s.setLong(1,teacherId);try(var r=s.executeQuery()){
    while(r.next())list.add(new ResultRecord(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getLong(5),r.getString(6),r.getLong(7),r.getString(8),r.getLong(9),r.getLong(10),r.getTimestamp(11).toLocalDateTime()));
   }}return List.copyOf(list);
 }
}
