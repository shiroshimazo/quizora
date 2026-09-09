package quizora.DAO;
import java.sql.*;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.*;
public final class SubjectManagementDAO {
 private static final String SELECT="""
 SELECT s.*,(SELECT COUNT(*) FROM quizzes q WHERE q.subject_id=s.subject_id) quizzes,
 (SELECT COUNT(*) FROM teacher_subjects t WHERE t.subject_id=s.subject_id) teachers FROM subjects s
 """;
 public List<SubjectRecord> load(AuthenticatedUser admin)throws SQLException{
  try(var c=databaseConnection.getConnection()){
   c.setAutoCommit(false);AccountManagementDAO.requireAdmin(c,admin,false);var list=new ArrayList<SubjectRecord>();
   try(var s=c.prepareStatement(SELECT+" ORDER BY s.subject_id DESC")){s.setQueryTimeout(10);try(var r=s.executeQuery()){while(r.next())list.add(read(r));}}
   c.commit();return List.copyOf(list);
  }
 }
 public SubjectRecord save(AuthenticatedUser admin,SubjectRecord original,SubjectChanges changes)throws SQLException{
  Objects.requireNonNull(changes);return change(admin,original,changes);
 }
 public SubjectRecord archive(AuthenticatedUser admin,SubjectRecord original)throws SQLException{
  Objects.requireNonNull(original);return change(admin,original,null);
 }
 private SubjectRecord change(AuthenticatedUser admin,SubjectRecord original,SubjectChanges changes)throws SQLException{
  try(var c=databaseConnection.getConnection()){
   c.setAutoCommit(false);try{
    AccountManagementDAO.requireAdmin(c,admin,true);long id=original==null?0:original.id();
    if(original!=null){
     try(var s=c.prepareStatement("SELECT subject_id FROM subjects WHERE subject_id=? FOR UPDATE")){s.setQueryTimeout(10);s.setLong(1,id);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Subject no longer exists.","40001");}}
     var current=find(c,id);if(!current.equals(original))throw new SQLException("Subject changed. Refresh and try again.","40001");
     if(current.archived())throw new IllegalArgumentException("Archived subjects are read-only.");
    }
    if(changes==null){try(var s=c.prepareStatement("UPDATE subjects SET archived_at=CURRENT_TIMESTAMP WHERE subject_id=?")){s.setLong(1,id);s.executeUpdate();}}
    else{
     String sql=id==0?"INSERT INTO subjects(subject_name,category,description) VALUES(?,?,?)":"UPDATE subjects SET subject_name=?,category=?,description=? WHERE subject_id=?";
     try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){s.setQueryTimeout(10);s.setString(1,changes.name());s.setString(2,changes.category());s.setString(3,changes.description());if(id!=0)s.setLong(4,id);s.executeUpdate();
      if(id==0)try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Subject ID missing");id=r.getLong(1);}
     }
    }
    var result=find(c,id);c.commit();return result;
   }catch(SQLException|RuntimeException e){c.rollback();throw e;}
  }
 }
 private SubjectRecord find(Connection c,long id)throws SQLException{
  try(var s=c.prepareStatement(SELECT+" WHERE s.subject_id=?")){s.setLong(1,id);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Subject no longer exists.","40001");return read(r);}}
 }
 private SubjectRecord read(ResultSet r)throws SQLException{return new SubjectRecord(r.getLong("subject_id"),r.getString("subject_name"),Objects.requireNonNullElse(r.getString("category"),""),Objects.requireNonNullElse(r.getString("description"),""),r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("archived_at")!=null,r.getInt("quizzes"),r.getInt("teachers"));}
}
