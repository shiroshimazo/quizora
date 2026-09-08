package quizora.model;

import java.time.LocalDateTime;
import java.util.Locale;

public record QuizRecord(long id, long subjectId, String subject, long teacherId, String teacher,
        String title, String description, int minutes, String state, boolean archived,
        int questions, long points, int attempts, LocalDateTime updatedAt) {
    public String status() { return archived ? "Archived" : state.substring(0, 1).toUpperCase(Locale.ROOT) + state.substring(1); }
    public boolean matches(String query, String status) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return (status == null || status.equals("All statuses") || status.equals(status()))
                && (id + " " + title + " " + subject + " " + teacher).toLowerCase(Locale.ROOT).contains(term);
    }
}
