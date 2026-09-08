package quizora.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Local connection factory. Call from DAOs, off the JavaFX UI thread. */
public final class databaseConnection {
    private static final String URL =
            "jdbc:mysql://localhost:3306/quiz_application_system?connectTimeout=5000";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private databaseConnection() {
    }

    /** Returns a fresh connection; callers must close it with try-with-resources. */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Read-only connectivity check, runnable directly from NetBeans. */
    public static void main(String[] args) throws SQLException {
        try (Connection connection = getConnection();
             var statement = connection.prepareStatement("SELECT DATABASE(), VERSION()");
             var result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("MySQL returned no connection information");
            }
            System.out.println("Connected to " + result.getString(1)
                    + " on MySQL " + result.getString(2));
        }
    }
}
