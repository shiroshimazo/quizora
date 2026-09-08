package quizora.model;

import java.util.List;

public record QuizQuestion(long id, String text, String a, String b, String c, String d, String answer, int points) {
    public QuizQuestion {
        text = required(text); a = required(a); b = required(b); c = required(c); d = required(d);
        if (!List.of("A", "B", "C", "D").contains(answer == null ? "" : answer))
            throw new IllegalArgumentException("Choose a correct answer for every question.");
        if (points < 1 || points > 100000) throw new IllegalArgumentException("Question points must be 1 to 100000.");
    }
    private static String required(String value) {
        value = value == null ? "" : value.strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Every question needs text and four answer choices.");
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000)
            throw new IllegalArgumentException("Question text or answer choice is too long.");
        return value;
    }
}
