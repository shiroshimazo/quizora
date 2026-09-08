package quizora.model;

/** Combo-box identity; duplicate display names remain distinguishable by ID. */
public record QuizChoice(long id, String name) {
    @Override public String toString() { return name + " · ID " + id; }
}
