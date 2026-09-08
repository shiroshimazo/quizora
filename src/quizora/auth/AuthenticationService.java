package quizora.auth;

import java.sql.SQLException;
import java.util.Optional;
import quizora.DAO.userDAO;

public final class AuthenticationService {
    private final userDAO users = new userDAO();
    private static final String DUMMY_HASH = PasswordHasher.hash("not-an-account".toCharArray());

    public Optional<AuthenticatedUser> authenticate(String identifier, char[] password) throws SQLException {
        if (identifier == null || identifier.isBlank() || password == null || password.length == 0) {
            return Optional.empty();
        }
        var account = users.findForLogin(identifier.trim());
        boolean valid = PasswordHasher.verify(password,
                account.map(userDAO.LoginAccount::passwordHash).orElse(DUMMY_HASH));
        if (!valid || account.isEmpty() || !account.get().active()) return Optional.empty();
        var found = account.get();
        if (!java.util.Set.of("admin", "teacher", "student").contains(found.role())) return Optional.empty();
        return Optional.of(new AuthenticatedUser(found.id(), found.fullName(), found.role()));
    }
}
