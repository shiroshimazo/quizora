package quizora.model;

/** Editable student fields; role, password and historical records are not editable here. */
public record StudentChanges(String name, String username, String email, boolean active) {
    public StudentChanges {
        name = name == null ? "" : name.strip();
        username = username == null ? "" : username.strip();
        email = email == null ? "" : email.strip();
        if (name.isEmpty() || name.length() > 150)
            throw new IllegalArgumentException("Name is required and must be at most 150 characters.");
        if (username.isEmpty() || username.length() > 50 || username.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Username is required, without spaces, and at most 50 characters.");
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter a valid email address (up to 254 characters).");
    }
}
