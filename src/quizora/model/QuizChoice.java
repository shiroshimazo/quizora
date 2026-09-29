/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;

/** Combo-box identity; duplicate display names remain distinguishable by ID. */
public record QuizChoice(long id, String name) {
    @Override public String toString() { return name + " · ID " + id; }
}
