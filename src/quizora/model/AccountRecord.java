/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;

import java.util.Locale;

/** Managed account list projection; excludes credentials and preserves archive state. */
public record AccountRecord(long id, String name, String username, String email,
        boolean active, boolean archived) {
    public String status() { return archived ? "Archived" : active ? "Active" : "Inactive"; }

    public boolean matches(String query, String status) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return (status == null || status.equals("All statuses") || status.equals(status()))
                && (Long.toString(id).contains(term) || name.toLowerCase(Locale.ROOT).contains(term)
                || username.toLowerCase(Locale.ROOT).contains(term) || email.toLowerCase(Locale.ROOT).contains(term));
    }
}
