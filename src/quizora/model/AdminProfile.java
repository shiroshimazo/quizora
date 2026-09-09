package quizora.model;

/** Own profile projection, excluding password hashes. Picture is encoded for value equality. */
public record AdminProfile(long id, String name, String username, String email, String contact,
        String picture, java.time.LocalDateTime createdAt) { }
