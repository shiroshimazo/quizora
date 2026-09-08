package quizora.model;

import java.util.List;

public record QuizChanges(String title, String description, long subjectId, long teacherId, int minutes, String state) {
    public QuizChanges {
        title = title == null ? "" : title.strip();
        description = description == null ? "" : description.strip();
        if (title.isEmpty() || title.length() > 200) throw new IllegalArgumentException("Title is required (up to 200 characters).");
        if (description.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000)
            throw new IllegalArgumentException("Description is too long.");
        if (subjectId <= 0 || teacherId <= 0) throw new IllegalArgumentException("Select a subject and teacher.");
        if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("Time limit must be 1 to 1440 minutes.");
        if (!List.of("draft", "published", "closed").contains(state == null ? "" : state))
            throw new IllegalArgumentException("Select a quiz status.");
    }
    public static QuizChanges from(QuizRecord q) {
        return new QuizChanges(q.title(), q.description(), q.subjectId(), q.teacherId(), q.minutes(), q.state());
    }
}
