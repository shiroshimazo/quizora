/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.auth;

/** In-memory identity, cleared on logout and never persisted as a password. */
public final class UserSession {
    private static AuthenticatedUser current;
    private static long generation;
    private UserSession() { }
    public static AuthenticatedUser current() { return current; }
    public static void signIn(AuthenticatedUser user) { current = java.util.Objects.requireNonNull(user); generation++; }
    public static void clear() { current = null; generation++; }
    public static long generation() { return generation; }
    /** Updating display details must not invalidate an active quiz in the same login session. */
    public static void updateProfile(AuthenticatedUser user) {
        if (current == null || user.id() != current.id() || !user.role().equals(current.role()))
            throw new SecurityException("The account session changed.");
        current = user;
    }
}
