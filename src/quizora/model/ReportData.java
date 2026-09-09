package quizora.model;

import java.time.LocalDateTime;
import java.util.List;

/** One consistent report snapshot, shared by the screen and PDF export. */
public record ReportData(LocalDateTime generatedAt, int passThreshold, Accounts students,
        Accounts teachers, long quizzes, long published, long archivedQuizzes, long attempts,
        long submitted, long scored, Double average, Double lowest, Double highest, Double passRate) {
    public record Accounts(long total, long active, long inactive, long archived) { }
    public record Metric(String label, String value) { }
    public List<Metric> metrics(String type) {
        if (!"Quiz statistics".equals(type)) {
            Accounts a = "Student statistics".equals(type) ? students : teachers;
            return List.of(new Metric("Total accounts", "" + a.total), new Metric("Active", "" + a.active),
                    new Metric("Inactive", "" + a.inactive), new Metric("Archived", "" + a.archived));
        }
        return List.of(new Metric("Total quizzes", "" + quizzes), new Metric("Published (unarchived)", "" + published),
                new Metric("Archived quizzes", "" + archivedQuizzes), new Metric("Total attempts", "" + attempts),
                new Metric("Submitted attempts", "" + submitted), new Metric("Scored submissions", "" + scored),
                new Metric("Average score", percent(average)), new Metric("Lowest score", percent(lowest)),
                new Metric("Highest score", percent(highest)), new Metric("Pass rate (at least " + passThreshold + "%)", percent(passRate)));
    }
    public static String percent(Double value) { return value == null ? "N/A" : String.format(java.util.Locale.ROOT, "%.1f%%", value); }
}
