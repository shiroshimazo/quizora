package quizora.DAO;
import java.sql.*;
import java.util.*;
import quizora.auth.*;
import quizora.database.databaseConnection;
import quizora.model.*;

public class TeacherQuizCreationTest {
    public static void main(String[] args)throws Exception {
        var dao=new QuizManagementDAO();var quizzes=new ArrayList<Long>();long teacher=0,subject=0,other=0;
        try(var c=databaseConnection.getConnection()) {
            try {
                String tag="tc-"+UUID.randomUUID();
                teacher=QuizManagementTest.insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Test teacher',?,?,'test-only','teacher')",tag,tag+"@example.invalid");
                subject=QuizManagementTest.insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag);
                other=QuizManagementTest.insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag+"-unassigned");
                var identity=new AuthenticatedUser(teacher,"Test teacher","teacher");
                require(dao.teacherCreationData(identity).subjects().isEmpty(),"No assignments");
                try(var s=c.prepareStatement("INSERT INTO teacher_subjects VALUES(?,?)")){s.setLong(1,teacher);s.setLong(2,subject);s.executeUpdate();}
                require(dao.teacherCreationData(identity).subjects().size()==1,"Only assigned subjects");
                var draft=new QuizChanges(tag,"Description",subject,teacher,30,"draft");
                var question=new QuizQuestion(0,"2+2?","4","3","2","1","A",2);
                reject(()->dao.createForTeacher(null,draft,List.of()),"Anonymous");
                reject(()->dao.createForTeacher(new AuthenticatedUser(identity.id(),"Student","student"),draft,List.of()),"Wrong role");
                long otherSubject=other;
                reject(()->dao.createForTeacher(identity,new QuizChanges(tag,"",otherSubject,identity.id(),30,"draft"),List.of()),"Unassigned subject");
                reject(()->dao.createForTeacher(identity,new QuizChanges(tag,"",draft.subjectId(),identity.id()+1,30,"draft"),List.of()),"Forged ownership");
                var published=new QuizChanges(tag,"Description",subject,teacher,30,"published");
                reject(()->dao.createForTeacher(identity,published,List.of()),"Empty publication");
                var saved=dao.createForTeacher(identity,draft,List.of());quizzes.add(saved.id());require(saved.questions()==0&&saved.state().equals("draft"),"Empty draft saved");
                saved=dao.createForTeacher(identity,published,List.of(question,question));quizzes.add(saved.id());
                require(saved.questions()==2&&saved.points()==4&&saved.teacherId()==teacher&&saved.state().equals("published"),"Published totals and owner");
                try(var s=c.prepareStatement("SELECT correct_answer,question_order FROM questions WHERE quiz_id=? ORDER BY question_order")){s.setLong(1,saved.id());try(var r=s.executeQuery()){for(int i=1;i<=2;i++){require(r.next()&&r.getString(1).equals("A")&&r.getInt(2)==i,"Answers and ordering");}}}
                try(var s=c.prepareStatement("UPDATE subjects SET archived_at=CURRENT_TIMESTAMP WHERE subject_id=?")){s.setLong(1,subject);s.executeUpdate();}
                reject(()->dao.createForTeacher(identity,draft,List.of()),"Archived subject");
                require(dao.teacherCreationData(identity).subjects().isEmpty(),"Archived subject hidden");
                try(var s=c.prepareStatement("UPDATE users SET is_active=FALSE WHERE user_id=?")){s.setLong(1,teacher);s.executeUpdate();}
                reject(()->dao.createForTeacher(identity,draft,List.of()),"Inactive teacher");
                System.out.println("PASS: teacher creation, draft/publish, questions, ownership, assigned subjects, archive and inactive access.");
            } finally {
                for(long id:quizzes){QuizManagementTest.delete(c,"DELETE FROM questions WHERE quiz_id=?",id);QuizManagementTest.delete(c,"DELETE FROM quizzes WHERE quiz_id=?",id);}
                QuizManagementTest.delete(c,"DELETE FROM teacher_subjects WHERE teacher_id=?",teacher);
                QuizManagementTest.delete(c,"DELETE FROM subjects WHERE subject_id=?",subject);QuizManagementTest.delete(c,"DELETE FROM subjects WHERE subject_id=?",other);
                QuizManagementTest.delete(c,"DELETE FROM users WHERE user_id=?",teacher);
            }
        }
    }
    interface Action {void run()throws Exception;}
    static void reject(Action action,String label)throws Exception {try{action.run();throw new AssertionError(label+" allowed");}catch(SecurityException|IllegalArgumentException expected){}}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
