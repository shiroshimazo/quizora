package quizora.DAO;

import java.time.Clock;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import quizora.auth.*;
import quizora.model.*;

public class StudentPanelsTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @FunctionalInterface interface Action{void run()throws Exception;}
    private static void reject(Action action)throws Exception{
        try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|SecurityException expected){}
    }
    public static byte[] picture()throws Exception{
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        try(var fixture=new TakeQuizTest.Fixture()){
            var student=TakeQuizTest.STUDENT;var other=TakeQuizTest.OTHER;
            var attempts=new TakeQuizDAO(fixture::connect,Clock.systemDefaultZone());
            var first=attempts.open(student,1);attempts.save(student,first.id(),1,"A");attempts.submit(student,first.id());
            var second=attempts.open(student,1);attempts.submit(student,second.id());
            attempts.submit(other,attempts.open(other,1).id());attempts.open(student,1);
            var results=new StudentResultsDAO(fixture::connect);
            var history=results.load(student);
            check(history.size()==2&&history.stream().allMatch(r->r.studentId()==student.id()),"Only own submitted results");
            check(history.getFirst().attemptId()==second.id()&&history.get(1).percentage()==60.0,"Newest attempts and correct percentage");
            fixture.sql("UPDATE quizzes SET archived_at='2026-01-01' WHERE quiz_id=1");
            check(results.load(student).size()==2,"Archived quiz history retained");
            check(results.load(other).size()==1,"Other student sees only own result");
            var profiles=new StudentProfileDAO(fixture::connect);var original=profiles.load(student);
            var changed=profiles.save(student,original,new ProfileChanges(" Updated Student ","updated","updated@example.com"," 12345 "));
            check(changed.name().equals("Updated Student")&&changed.contact().equals("12345"),"Validated profile saved");
            check(profiles.load(student).equals(changed),"Profile reload persists changes");
            check(fixture.count("SELECT COUNT(*) FROM users WHERE user_id=1 AND role='student' AND password_hash='unused' AND is_active=1")==1,"Role, password and activity preserved");
            reject(()->profiles.save(student,changed,new ProfileChanges("Name","OTHER","unique@example.com","")));
            reject(()->profiles.save(student,changed,new ProfileChanges("Name","unique","O@example.com","")));
            reject(()->profiles.save(student,changed,new ProfileChanges("Name","o@example.com","unique@example.com","")));
            reject(()->profiles.save(other,changed,new ProfileChanges("Name","unique","unique@example.com","")));
            try{profiles.save(student,original,new ProfileChanges("Old","old","old@example.com",""));throw new AssertionError("Stale save accepted");}
            catch(java.sql.SQLException expected){check("40001".equals(expected.getSQLState()),"Stale profile rejected");}
            var pictured=profiles.picture(student,changed,picture());
            check(!pictured.picture().isEmpty()&&profiles.load(student).equals(pictured),"Profile picture persists");
            reject(()->profiles.picture(student,pictured,new byte[]{1,2,3}));
            reject(()->new ProfileChanges(" ","valid","valid@example.com",""));
            reject(()->new ProfileChanges("Name","valid","invalid",""));
            for(var identity:new AuthenticatedUser[]{null,new AuthenticatedUser(2,"Teacher","teacher"),new AuthenticatedUser(2,"Forged","student")}){
                reject(()->profiles.load(identity));reject(()->results.load(identity));
            }
            fixture.sql("UPDATE users SET is_active=0 WHERE user_id=1");reject(()->profiles.load(student));reject(()->results.load(student));
            UserSession.signIn(student);long generation=UserSession.generation();
            UserSession.updateProfile(new AuthenticatedUser(student.id(),"Updated","student"));
            check(UserSession.generation()==generation&&UserSession.current().fullName().equals("Updated"),"Profile preserves login session");
            reject(()->UserSession.updateProfile(other));UserSession.clear();UserSession.signIn(student);
            check(UserSession.generation()!=generation,"New login invalidates previous session");UserSession.clear();
            System.out.println("Student panel DAO checks passed: private results, history, profile validation, duplicates, ownership, stale edits, picture, session continuity.");
        }
    }
}
