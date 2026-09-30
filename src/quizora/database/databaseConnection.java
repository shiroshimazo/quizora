/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class databaseConnection {

    private static final String URL = "jdbc:sqlite:database/quizora.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static Connection getReadOnlyConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static boolean isDuplicateKey(SQLException error) {
        return error.getMessage().contains("UNIQUE");
    }

    public static void main(String[] args) {
        try {
            Connection connection = getConnection();
            System.out.println("Connected!");
            connection.close();
        } catch (SQLException error) {
            System.out.println("Error: " + error.getMessage());
        }
    }
}