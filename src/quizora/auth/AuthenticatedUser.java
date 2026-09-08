package quizora.auth;

/** Session identity contains no password or password hash. */
public record AuthenticatedUser(long id, String fullName, String role) {
    public AuthenticatedUser {
        if (!java.util.Set.of("admin", "teacher", "student").contains(role)) {
            throw new IllegalArgumentException("Unsupported account role");
        }
    }
}
