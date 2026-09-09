package quizora.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import quizora.auth.AuthenticatedUser;

/** Reuses own-profile persistence with a fixed teacher authorization boundary. */
public final class TeacherProfileDAO extends AdminProfileDAO {
    @Override protected void requireAccess(Connection c,AuthenticatedUser teacher,boolean lock)throws SQLException {
        if(teacher==null||!"teacher".equals(teacher.role()))throw new SecurityException("Teacher access is required.");
        try(var s=c.prepareStatement("SELECT user_id FROM users WHERE user_id=? AND role='teacher' AND is_active=TRUE AND archived_at IS NULL"+(lock?" FOR UPDATE":""))) {
            s.setQueryTimeout(10);s.setLong(1,teacher.id());
            try(var r=s.executeQuery()){if(!r.next())throw new SecurityException("Active teacher access is required.");}
        }
    }
}
