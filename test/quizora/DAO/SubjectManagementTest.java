package quizora.DAO;
import java.util.*;
import java.sql.*;
import quizora.auth.*;
import quizora.database.databaseConnection;
import quizora.model.*;
public class SubjectManagementTest {
 public static void main(String[] args)throws Exception{
  var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();
  var dao=new SubjectManagementDAO();var quizzes=new QuizManagementDAO();String tag="subject-test-"+UUID.randomUUID();long id=0,quiz=0,teacher=0;
  try(var c=databaseConnection.getConnection()){
   try{
    var changes=new SubjectChanges(tag,"Science","Description");
    try{dao.save(null,null,changes);throw new AssertionError("Anonymous write allowed");}catch(SecurityException expected){}
    try{new SubjectChanges(" ","","");throw new AssertionError("Blank name allowed");}catch(IllegalArgumentException expected){}
    var created=dao.save(admin,null,changes);id=created.id();
    require(created.matches("SCIENCE","Active","Science"),"Search and category filter");
    try{dao.save(admin,null,changes);throw new AssertionError("Duplicate allowed");}catch(SQLException expected){require(expected.getErrorCode()==1062,"Duplicate guard");}
    var edited=dao.save(admin,created,new SubjectChanges(tag,"Math","Edited"));
    try{dao.archive(admin,created);throw new AssertionError("Stale archive allowed");}catch(SQLException expected){require("40001".equals(expected.getSQLState()),"Stale guard");}
    teacher=quizzes.load(admin).teachers().getFirst().id();
    var q=quizzes.save(admin,null,new QuizChanges(tag,"",id,teacher,30,"draft"),List.of());quiz=q.id();
    try(var s=c.prepareStatement("INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)")){s.setLong(1,teacher);s.setLong(2,id);s.executeUpdate();}
    long sid=id;var current=dao.load(admin).stream().filter(s->s.id()==sid).findFirst().orElseThrow();
    var archived=dao.archive(admin,current);require(archived.archived()&&archived.quizzes()==1&&archived.teachers()==1,"Links preserved");
    require(quizzes.load(admin).subjects().stream().noneMatch(s->s.id()==sid),"Archived subject hidden in quiz choices");
    try{quizzes.save(admin,null,new QuizChanges(tag,"",id,teacher,30,"draft"),List.of());throw new AssertionError("Archived subject assignment allowed");}catch(IllegalArgumentException expected){}
    var details=quizzes.details(admin,quiz);quizzes.save(admin,details,QuizChanges.from(details.quiz()),details.questions());
    try{dao.save(admin,archived,changes);throw new AssertionError("Archived edit allowed");}catch(IllegalArgumentException expected){}
    System.out.println("PASS: subject CRUD validation, duplicates, admin/stale guards, archive preserves links and blocks new quiz assignments while existing quizzes remain editable.");
   }finally{
    try(var s=c.prepareStatement("DELETE FROM quizzes WHERE quiz_id=?")){s.setLong(1,quiz);s.executeUpdate();}
    try(var s=c.prepareStatement("DELETE FROM teacher_subjects WHERE subject_id=?")){s.setLong(1,id);s.executeUpdate();}
    try(var s=c.prepareStatement("DELETE FROM subjects WHERE subject_id=?")){s.setLong(1,id);s.executeUpdate();}
   }
  }
 }
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
