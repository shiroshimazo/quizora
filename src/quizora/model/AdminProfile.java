/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;

/** Own profile projection, excluding password hashes. Picture is encoded for value equality. */
public record AdminProfile(long id, String name, String username, String email, String contact,
        String picture, java.time.LocalDateTime createdAt) { }
