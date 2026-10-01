package quizora.auth;

import java.sql.SQLException;
import java.util.Arrays;
import quizora.DAO.AccountManagementDAO;
import quizora.DAO.userDAO;
import quizora.model.AccountChanges;

/** Public registration always creates an active student account. */
public final class RegistrationService {
    private final userDAO users;
    public RegistrationService() { this(new userDAO()); }
    public RegistrationService(userDAO users) { this.users = users; }

    public void register(String name, String username, String email, char[] password, char[] confirmation)
            throws SQLException {
        try {
            AccountChanges details = new AccountChanges(name, username, email, true);
            AccountManagementDAO.validatePassword(password == null ? null : new String(password));
            if (!Arrays.equals(password, confirmation))
                throw new IllegalArgumentException("Passwords do not match.");
            if (!users.createStudent(details, PasswordHasher.hash(password)))
                throw new IllegalArgumentException("Username or email is already in use. Choose another or sign in.");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
