package quizora.model;

import java.time.LocalDate;
import java.util.List;

/** One consistent read of the teacher's assessment overview. */
public record TeacherDashboardData(long students, long published, long quizzes, long subjects,
        long submissions, Double averageScore, LocalDate today,
        List<Count> quizzesBySubject, List<DailyCount> submissionsByDay, List<Count> quizStatuses) {
    public record Count(String label, long value) { }
    public record DailyCount(LocalDate date, long value) { }
}
