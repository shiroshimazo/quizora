package quizora.DAO;

import java.sql.*;
import java.util.*;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.*;

public final class QuizManagementDAO {
    private static final String SELECT = """
        SELECT q.*,s.subject_name,u.full_name,
        (SELECT COUNT(*) FROM questions x WHERE x.quiz_id=q.quiz_id) question_count,
        (SELECT COALESCE(SUM(points),0) FROM questions x WHERE x.quiz_id=q.quiz_id) points,
        (SELECT COUNT(*) FROM quiz_attempts a WHERE a.quiz_id=q.quiz_id) attempts
        FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id JOIN users u ON u.user_id=q.teacher_id
        """;
    public record Data(List<QuizRecord> quizzes,List<QuizChoice> subjects,List<QuizChoice> teachers) {}
    public Data load(AuthenticatedUser admin) throws SQLException {
        try(var c=databaseConnection.getConnection()) {
            c.setAutoCommit(false);
            AccountManagementDAO.requireAdmin(c,admin,false);
            var records=new ArrayList<QuizRecord>();
            try(var s=c.prepareStatement(SELECT+" ORDER BY q.quiz_id DESC")){s.setQueryTimeout(10);try(var r=s.executeQuery()){while(r.next())records.add(read(r));}}
            var subjects=choices(c,"SELECT subject_id,subject_name FROM subjects WHERE archived_at IS NULL ORDER BY subject_name");
            var teachers=choices(c,"SELECT user_id,full_name FROM users WHERE role='teacher' AND is_active=TRUE AND archived_at IS NULL ORDER BY full_name");
            c.commit();return new Data(List.copyOf(records),subjects,teachers);
        }
    }
    public Data teacherCreationData(AuthenticatedUser teacher) throws SQLException {
        try(var c=databaseConnection.getConnection()) {
            c.setAutoCommit(false);
            requireTeacher(c,teacher);
            var subjects=new ArrayList<QuizChoice>();
            try(var s=c.prepareStatement("SELECT s.subject_id,s.subject_name FROM teacher_subjects ts JOIN subjects s ON s.subject_id=ts.subject_id WHERE ts.teacher_id=? AND s.archived_at IS NULL ORDER BY s.subject_name")) {
                s.setQueryTimeout(10);s.setLong(1,teacher.id());
                try(var r=s.executeQuery()){while(r.next())subjects.add(new QuizChoice(r.getLong(1),r.getString(2)));}
            }
            c.commit();return new Data(List.of(),List.copyOf(subjects),List.of(new QuizChoice(teacher.id(),teacher.fullName())));
        }
    }
    static void requireTeacher(Connection c,AuthenticatedUser teacher)throws SQLException {
        if(teacher==null||!"teacher".equals(teacher.role()))throw new SecurityException("Teacher access is required.");
        try(var s=c.prepareStatement("SELECT user_id FROM users WHERE user_id=? AND role='teacher' AND is_active=TRUE AND archived_at IS NULL FOR SHARE")) {
            s.setQueryTimeout(10);s.setLong(1,teacher.id());
            try(var r=s.executeQuery()){if(!r.next())throw new SecurityException("Active teacher access is required.");}
        }
    }
    public QuizRecord createForTeacher(AuthenticatedUser teacher,QuizChanges change,List<QuizQuestion> items)throws SQLException {
        if(teacher==null||!"teacher".equals(teacher.role()))throw new SecurityException("Teacher access is required.");
        if(change.teacherId()!=teacher.id())throw new SecurityException("You can only create your own quizzes.");
        if(!List.of("draft","published").contains(change.state()))throw new IllegalArgumentException("Create a draft or published quiz.");
        return save(teacher,null,change,items,true);
    }
    private List<QuizChoice> choices(Connection c,String sql)throws SQLException{
        var list=new ArrayList<QuizChoice>();
        try(var s=c.prepareStatement(sql)){s.setQueryTimeout(10);try(var r=s.executeQuery()){while(r.next())list.add(new QuizChoice(r.getLong(1),r.getString(2)));}}
        return List.copyOf(list);
    }
    public QuizDetails details(AuthenticatedUser admin,long id)throws SQLException{
        try(var c=databaseConnection.getConnection()){
            c.setAutoCommit(false);AccountManagementDAO.requireAdmin(c,admin,false);
            var result=new QuizDetails(find(c,id),questions(c,id));c.commit();return result;
        }
    }
    private QuizRecord find(Connection c,long id)throws SQLException{
        try(var s=c.prepareStatement(SELECT+" WHERE q.quiz_id=?")){s.setQueryTimeout(10);s.setLong(1,id);try(var r=s.executeQuery()){
            if(!r.next())throw new SQLException("Quiz no longer exists. Refresh and try again.","40001");return read(r);
        }}
    }
    private List<QuizQuestion> questions(Connection c,long id)throws SQLException{
        var list=new ArrayList<QuizQuestion>();
        try(var s=c.prepareStatement("SELECT * FROM questions WHERE quiz_id=? ORDER BY question_order")){s.setQueryTimeout(10);s.setLong(1,id);try(var r=s.executeQuery()){
            while(r.next())list.add(new QuizQuestion(r.getLong("question_id"),r.getString("question_text"),r.getString("option_a"),r.getString("option_b"),r.getString("option_c"),r.getString("option_d"),r.getString("correct_answer"),r.getInt("points")));
        }}return List.copyOf(list);
    }
    public QuizRecord save(AuthenticatedUser admin,QuizDetails original,QuizChanges change,List<QuizQuestion> items)throws SQLException{
        return save(admin,original,change,items,false);
    }
    private QuizRecord save(AuthenticatedUser admin,QuizDetails original,QuizChanges change,List<QuizQuestion> items,boolean teacherCreation)throws SQLException{
        Objects.requireNonNull(change);items=List.copyOf(items);
        if(items.size()>500)throw new IllegalArgumentException("A quiz can contain at most 500 questions.");
        if(change.state().equals("published")&&items.isEmpty())throw new IllegalArgumentException("Add at least one question before publishing.");
        try(var c=databaseConnection.getConnection()){
            c.setAutoCommit(false);
            try{
                if(teacherCreation){
                    requireTeacher(c,admin);
                    try(var s=c.prepareStatement("SELECT ts.subject_id FROM teacher_subjects ts JOIN subjects s ON s.subject_id=ts.subject_id WHERE ts.teacher_id=? AND ts.subject_id=? AND s.archived_at IS NULL FOR SHARE")) {
                        s.setQueryTimeout(10);s.setLong(1,admin.id());s.setLong(2,change.subjectId());
                        try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("This subject is no longer assigned to you or has been archived. Refresh subjects and try again.");}
                    }
                }else AccountManagementDAO.requireAdmin(c,admin,true);
                long id=original==null?0:original.quiz().id();
                QuizRecord current=null;
                if(original!=null){
                    lock(c,id);current=find(c,id);
                    if(!current.equals(original.quiz())||!questions(c,id).equals(original.questions()))throw new SQLException("Quiz changed. Close the form, refresh, and try again.","40001");
                    if(current.archived())throw new IllegalArgumentException("Archived quizzes are read-only.");
                    if(current.attempts()>0 && (!items.equals(original.questions())||change.minutes()!=current.minutes()||change.subjectId()!=current.subjectId()||change.teacherId()!=current.teacherId()||change.state().equals("draft")))
                        throw new IllegalArgumentException("Quizzes with attempts retain their questions, time limit, subject and teacher, and cannot return to draft.");
                }
                try(var s=c.prepareStatement("SELECT user_id FROM users WHERE user_id=? AND role='teacher' AND is_active=TRUE AND archived_at IS NULL FOR SHARE")){
                    s.setLong(1,change.teacherId());try(var r=s.executeQuery()){
                        if(!r.next()&&(current==null||current.teacherId()!=change.teacherId()))throw new IllegalArgumentException("Select an active teacher.");
                    }
                }
                try(var s=c.prepareStatement("SELECT subject_id,archived_at FROM subjects WHERE subject_id=? FOR SHARE")){
                    s.setLong(1,change.subjectId());try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Subject no longer exists. Refresh and try again.");
                    if(r.getTimestamp("archived_at")!=null&&(current==null||current.subjectId()!=change.subjectId()))throw new IllegalArgumentException("Select an active subject.");}
                }
                String sql=id==0?"INSERT INTO quizzes(title,description,subject_id,teacher_id,time_limit_minutes,status) VALUES(?,?,?,?,?,?)":"UPDATE quizzes SET title=?,description=?,subject_id=?,teacher_id=?,time_limit_minutes=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE quiz_id=?";
                try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                    s.setQueryTimeout(10);s.setString(1,change.title());s.setString(2,change.description());s.setLong(3,change.subjectId());s.setLong(4,change.teacherId());s.setInt(5,change.minutes());s.setString(6,change.state());
                    if(id!=0)s.setLong(7,id);s.executeUpdate();
                    if(id==0)try(var keys=s.getGeneratedKeys()){if(!keys.next())throw new SQLException("Quiz ID missing");id=keys.getLong(1);}
                }
                if(original==null||!items.equals(original.questions())){
                    try(var s=c.prepareStatement("DELETE FROM questions WHERE quiz_id=?")){s.setLong(1,id);s.executeUpdate();}
                    try(var s=c.prepareStatement("INSERT INTO questions(quiz_id,question_text,option_a,option_b,option_c,option_d,correct_answer,points,question_order) VALUES(?,?,?,?,?,?,?,?,?)")){
                        int order=0;for(var q:items){s.setLong(1,id);s.setString(2,q.text());s.setString(3,q.a());s.setString(4,q.b());s.setString(5,q.c());s.setString(6,q.d());s.setString(7,q.answer());s.setInt(8,q.points());s.setInt(9,++order);s.addBatch();}s.executeBatch();
                    }
                }
                var result=find(c,id);c.commit();return result;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public QuizRecord archive(AuthenticatedUser admin,QuizRecord original)throws SQLException{
        try(var c=databaseConnection.getConnection()){
            c.setAutoCommit(false);try{
                AccountManagementDAO.requireAdmin(c,admin,true);lock(c,original.id());var current=find(c,original.id());
                if(!current.equals(original))throw new SQLException("Quiz changed. Refresh and try again.","40001");
                if(current.archived())throw new IllegalArgumentException("Quiz is already archived.");
                try(var s=c.prepareStatement("UPDATE quizzes SET status='closed',archived_at=CURRENT_TIMESTAMP WHERE quiz_id=?")){s.setLong(1,original.id());s.executeUpdate();}
                var result=find(c,original.id());c.commit();return result;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    private void lock(Connection c,long id)throws SQLException{
        try(var s=c.prepareStatement("SELECT quiz_id FROM quizzes WHERE quiz_id=? FOR UPDATE")){s.setQueryTimeout(10);s.setLong(1,id);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Quiz no longer exists.","40001");}}
    }
    private QuizRecord read(ResultSet r)throws SQLException{
        return new QuizRecord(r.getLong("quiz_id"),r.getLong("subject_id"),r.getString("subject_name"),r.getLong("teacher_id"),r.getString("full_name"),r.getString("title"),Objects.requireNonNullElse(r.getString("description"),""),r.getInt("time_limit_minutes"),r.getString("status"),r.getTimestamp("archived_at")!=null,r.getInt("question_count"),r.getLong("points"),r.getInt("attempts"),r.getTimestamp("updated_at").toLocalDateTime());
    }
}
