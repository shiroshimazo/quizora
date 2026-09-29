/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;

public record ProfileChanges(String name, String username, String email, String contact) {
    public ProfileChanges {
        var validated = new AccountChanges(name, username, email, true);
        name = validated.name(); username = validated.username(); email = validated.email();
        contact = contact == null ? "" : contact.strip();
        if (contact.length() > 50) throw new IllegalArgumentException("Contact number must be at most 50 characters.");
    }
}
