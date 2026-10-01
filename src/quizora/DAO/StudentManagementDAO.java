/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

/** Admin-only management of student accounts. All the work is in AccountManagementDAO. */
public final class StudentManagementDAO extends AccountManagementDAO {

    public StudentManagementDAO() {
        super("student");
    }
}
