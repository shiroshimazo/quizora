package quizora.DAO;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

public class AssignedSubjectsTest {
    public static void main(String[] args)throws Exception {
        var dao=new AssignedSubjectsDAO();
        for(var user:List.of(new AuthenticatedUser(1,"Student","student"),new AuthenticatedUser(4294967295L,"Missing","teacher"))) {
            try{dao.load(user);throw new AssertionError("Unauthorized access");}catch(SecurityException expected){}
        }
        try(var c=databaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                String tag="as-"+UUID.randomUUID();
                long teacher=QuizManagementTest.insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Teacher',?,?,'test-only','teacher')",tag,tag+"@example.invalid");
                long other=QuizManagementTest.insert(c,"INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Other',?,?,'test-only','teacher')","o"+tag,"o"+tag+"@example.invalid");
                long subject=QuizManagementTest.insert(c,"INSERT INTO subjects(subject_name,category,description) VALUES(?,'Math','Fractions and ratios')",tag);
                long empty=QuizManagementTest.insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag+"-empty");
                long hidden=QuizManagementTest.insert(c,"INSERT INTO subjects(subject_name,archived_at) VALUES(?,CURRENT_TIMESTAMP)",tag+"-archived");
                long unassigned=QuizManagementTest.insert(c,"INSERT INTO subjects(subject_name) VALUES(?)",tag+"-unassigned");
                require(dao.readAssignments(c,teacher).isEmpty(),"Empty assignments");
                try(var s=c.prepareStatement("INSERT INTO teacher_subjects VALUES(?,?)")){for(long id:List.of(subject,empty,hidden)){s.setLong(1,teacher);s.setLong(2,id);s.executeUpdate();}}
                QuizManagementTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status) VALUES(?,?,'Published','published')",subject,teacher);
                QuizManagementTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status) VALUES(?,?,'Draft','draft')",subject,teacher);
                QuizManagementTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status) VALUES(?,?,'Other teacher','published')",subject,other);
                QuizManagementTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title,status,archived_at) VALUES(?,?,'Archived','published',CURRENT_TIMESTAMP)",subject,teacher);
                QuizManagementTest.insert(c,"INSERT INTO quizzes(subject_id,teacher_id,title) VALUES(?,?,'Unassigned')",unassigned,teacher);
                var rows=dao.readAssignments(c,teacher);require(rows.size()==2,"Only current assignments");
                var row=rows.stream().filter(s->s.id()==subject).findFirst().orElseThrow();require(row.quizzes()==2&&row.published()==1,"Scoped quiz counts");
                require(row.matches("RATIOS")&&row.matches("math")&&!row.matches("biology"),"Search metadata");
                var zero=rows.stream().filter(s->s.id()==empty).findFirst().orElseThrow();require(zero.quizzes()==0&&zero.published()==0&&zero.category().isEmpty(),"Empty subject and null metadata");
                require(dao.readAssignments(c,other).isEmpty(),"No assignment leakage");
                System.out.println("PASS: assignments, teacher isolation, archive exclusions, zero quiz counts, metadata search and access.");
            }finally{c.rollback();}
        }
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
