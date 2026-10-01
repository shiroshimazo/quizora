/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import quizora.auth.AuthenticatedUser;

/** Profile reads and edits for the signed-in teacher. */
public final class TeacherProfileDAO extends AdminProfileDAO {

    @Override
    protected void requireAccess(Connection connection, AuthenticatedUser user) throws SQLException {
        AccessCheck.requireTeacher(connection, user);
    }
}
