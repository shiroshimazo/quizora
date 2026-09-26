package quizora.model;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only overview for the signed-in student. Null scores mean no finalized results yet. */
public record StudentDashboardData(long available, long notAttempted, long submissions, long completedQuizzes,
        Double averageScore, Double bestScore, List<AvailableQuiz> availableQuizzes,
        List<RecentResult> recentResults, List<SubjectAverage> averageBySubject) {
    public record AvailableQuiz(long id, String title, String subject, String teacher, int questions,
            int minutes, long attempts) { }
    public record RecentResult(long attemptId, String quiz, String subject, long score, long totalPoints,
            LocalDateTime submittedAt) {
        public double percent() { return 100.0 * score / totalPoints; }
    }
    public record SubjectAverage(String subject, double average, long attempts) { }
}
