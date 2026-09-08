package quizora.DAO;

/** Admin-only management of student accounts. */
public final class StudentManagementDAO extends AccountManagementDAO {
    public StudentManagementDAO() { super("student"); }
}
