package quizora.DAO;

import java.sql.*;
import java.util.UUID;
import quizora.auth.*;
import quizora.database.databaseConnection;
import quizora.model.*;

/** Tests use uniquely named disposable fixtures; no existing student is modified. */
public class StudentManagementTest {
    public static void main(String[] args) throws Exception {
        var auth = new AuthenticationService();
        var admin = auth.authenticate("admin", "Admin@123".toCharArray()).orElseThrow();
        var dao = new StudentManagementDAO();
        String tag = UUID.randomUUID().toString().substring(0, 20);
        long studentId=0, subjectId=0, quizId=0, attemptId=0;
        try (var connection = databaseConnection.getConnection()) {
            try {
                var details = new AccountChanges("Test Student",tag,tag+"@example.invalid",true);
                try { dao.create(null,details,"Test@123"); throw new AssertionError("Anonymous create allowed"); }
                catch(SecurityException expected) { }
                try { dao.create(admin,details,"short"); throw new AssertionError("Short password accepted"); }
                catch(IllegalArgumentException expected) { }
                AccountRecord created=dao.create(admin,details,"Test@123");
                studentId=created.id();
                require(created.active() && !created.archived(),"New student active and unarchived");
                require(auth.authenticate(tag,"Test@123".toCharArray()).orElseThrow().role().equals("student"),"Created student can log in");
                try { dao.create(admin,details,"Test@123"); throw new AssertionError("Duplicate create allowed"); }
                catch(SQLException expected) { require(expected.getErrorCode()==1062,"Duplicate create validation"); }
                try { dao.create(admin,new AccountChanges("Collision",details.email(),tag+"-other@example.invalid",true),"Test@123");
                    throw new AssertionError("Cross-field duplicate allowed"); }
                catch(SQLException expected) { require(expected.getErrorCode()==1062,"Cross-field duplicate validation"); }
                subjectId=insert(connection,"INSERT INTO subjects(subject_name) VALUES(?)",tag);
                long teacherId;
                try (var s=connection.prepareStatement("SELECT user_id FROM users WHERE role='teacher' LIMIT 1");
                     var r=s.executeQuery()) { if(!r.next()) throw new AssertionError("Teacher fixture missing"); teacherId=r.getLong(1); }
                quizId=insert(connection,"INSERT INTO quizzes(subject_id,teacher_id,title) VALUES(?,?,'Archive history test')",subjectId,teacherId);
                attemptId=insert(connection,"INSERT INTO quiz_attempts(quiz_id,student_id,status,submitted_at) "
                        + "VALUES(?,?,'submitted',CURRENT_TIMESTAMP)",quizId,studentId);
                insert(connection,"INSERT INTO quiz_results(attempt_id,score,total_points) VALUES(?,1,1)",attemptId);
                AccountRecord original=find(dao,admin,studentId);
                require(original.matches("TEST STUDENT","Active"),"Case-insensitive name search");
                require(original.matches(Long.toString(studentId),"All statuses"),"ID search");
                require(!original.matches("missing","Active"),"Search exclusion");
                require(!original.matches("","Inactive"),"Combined status filter");
                require(dao.load(admin).stream().noneMatch(s -> s.id()==admin.id()),"Only students listed");
                try { dao.load(new AuthenticatedUser(studentId,"Student","student")); throw new AssertionError("Student read allowed"); }
                catch(SecurityException expected) { }
                try { dao.archive(new AuthenticatedUser(studentId,"Fake admin","admin"),original); throw new AssertionError("Forged admin write allowed"); }
                catch(SecurityException expected) { }
                try { new AccountChanges("","","bad",true); throw new AssertionError("Invalid edit accepted"); }
                catch(IllegalArgumentException expected) { }
                try { dao.edit(admin,original,new AccountChanges("Student","admin",tag+"@example.invalid",true)); throw new AssertionError("Duplicate accepted"); }
                catch(SQLException expected) { require(expected.getErrorCode()==1062,"Duplicate validation"); }
                AccountRecord edited=dao.edit(admin,original,new AccountChanges("Edited Student","edited-"+tag,tag+"@example.invalid",false));
                require(edited.name().equals("Edited Student") && edited.status().equals("Inactive"),"Edit saved");
                require(auth.authenticate(edited.username(),"Test@123".toCharArray()).isEmpty(),"Inactive login denied");
                try { dao.archive(admin,original); throw new AssertionError("Stale archive accepted"); }
                catch(SQLException expected) { require("40001".equals(expected.getSQLState()),"Stale record protection"); }
                edited=dao.edit(admin,edited,new AccountChanges(edited.name(),edited.username(),edited.email(),true));
                require(auth.authenticate(edited.email(),"Test@123".toCharArray()).isPresent(),"Reactivated login and preserved password");
                AccountRecord archived=dao.archive(admin,edited);
                require(archived.archived() && !archived.active() && archived.status().equals("Archived"),"Archive status");
                require(auth.authenticate(archived.username(),"Test@123".toCharArray()).isEmpty(),"Archived login denied");
                require(archived.matches("","Archived") && !archived.matches("","Inactive"),"Archived separate from inactive");
                try { dao.edit(admin,archived,new AccountChanges("Changed",archived.username(),archived.email(),true));
                    throw new AssertionError("Archived edit accepted"); } catch(IllegalArgumentException expected) { }
                try(var s=connection.prepareStatement("SELECT COUNT(*) FROM quiz_results WHERE attempt_id=?")) {
                    s.setLong(1,attemptId); try(var r=s.executeQuery()) { r.next(); require(r.getInt(1)==1,"Quiz history preserved"); }
                }
                // Archive guard still works if an external tool changes only the legacy active flag.
                execute(connection,"UPDATE users SET is_active=TRUE WHERE user_id=?",studentId);
                require(auth.authenticate(archived.username(),"Test@123".toCharArray()).isEmpty(),"Archive timestamp blocks legacy reactivation");
                System.out.println("PASS: student-only listing, admin access, search/status, edit validation, duplicates, stale writes, archive and preserved history/login rules.");
            } finally {
                execute(connection,"DELETE FROM quiz_results WHERE attempt_id=?",attemptId);
                execute(connection,"DELETE FROM quiz_attempts WHERE attempt_id=?",attemptId);
                execute(connection,"DELETE FROM quizzes WHERE quiz_id=?",quizId);
                execute(connection,"DELETE FROM subjects WHERE subject_id=?",subjectId);
                execute(connection,"DELETE FROM users WHERE user_id=?",studentId);
            }
        }
    }
    private static AccountRecord find(StudentManagementDAO dao,AuthenticatedUser admin,long id) throws SQLException {
        return dao.load(admin).stream().filter(s -> s.id()==id).findFirst().orElseThrow();
    }
    private static long insert(Connection c,String sql,Object... values) throws SQLException {
        try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);
            s.executeUpdate(); try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}
        }
    }
    private static void execute(Connection c,String sql,long id) throws SQLException {
        if(id==0)return;
        try(var s=c.prepareStatement(sql)){s.setLong(1,id);s.executeUpdate();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
