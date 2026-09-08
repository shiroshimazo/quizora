package quizora.model;

import java.util.List;

public record QuizDetails(QuizRecord quiz, List<QuizQuestion> questions) {
    public QuizDetails { questions = List.copyOf(questions); }
}
