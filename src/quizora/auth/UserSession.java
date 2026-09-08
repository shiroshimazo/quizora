package quizora.auth;

/** In-memory identity, cleared on logout and never persisted as a password. */
public final class UserSession {
    private static AuthenticatedUser current;
    private UserSession() { }
    public static AuthenticatedUser current() { return current; }
    public static void signIn(AuthenticatedUser user) { current = java.util.Objects.requireNonNull(user); }
    public static void clear() { current = null; }
}
