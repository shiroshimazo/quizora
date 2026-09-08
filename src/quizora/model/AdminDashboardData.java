package quizora.model;

import java.time.LocalDate;
import java.util.List;

/** One consistent read of the administrator's system overview. */
public record AdminDashboardData(long students, long teachers, long quizzes, long subjects,
        long submissions, Double averageScore, LocalDate today,
        List<Count> quizzesBySubject, List<DailyCount> submissionsByDay, List<Count> quizStatuses) {
    public record Count(String label, long value) { }
    public record DailyCount(LocalDate date, long value) { }
}
