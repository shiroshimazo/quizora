/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;

import java.util.List;

public record QuizDetails(QuizRecord quiz, List<QuizQuestion> questions) {
    public QuizDetails { questions = List.copyOf(questions); }
}
