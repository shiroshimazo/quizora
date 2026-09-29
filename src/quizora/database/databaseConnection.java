/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * Local SQLite connection factory. Call from DAOs, off the JavaFX UI thread.
 *
 * <p>The database file defaults to database/quizora.db under the working directory;
 * override it with -Dquizora.db=path. Missing tables are created from schema.sql on
 * the first connection of each run.
 *
 * <p>Write transactions start with BEGIN IMMEDIATE, which takes the database write lock
 * up front. That replaces MySQL row locks (FOR UPDATE / FOR SHARE), so a transaction's
 * checks and writes cannot interleave with another writer.
 */
public final class databaseConnection {
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final int BUSY_TIMEOUT_MILLIS = 10_000;
    private static volatile boolean schemaReady;

    private databaseConnection() {
    }

    /** Returns a fresh read/write connection; callers must close it with try-with-resources. */
    public static Connection getConnection() throws SQLException {
        SQLiteConfig config = baseConfig();
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return open(config);
    }

    /** Returns a fresh connection that rejects writes, for dashboards, lists and reports. */
    public static Connection getReadOnlyConnection() throws SQLException {
        SQLiteConfig config = baseConfig();
        config.setReadOnly(true);
        return open(config);
    }

    /** True when a statement failed on a UNIQUE constraint (or a DAO reported a duplicate). */
    public static boolean isDuplicateKey(SQLException error) {
        return error.getErrorCode() == 1062
                || error instanceof SQLiteException sqlite
                && sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
    }

    public static Path databaseFile() {
        return Path.of(System.getProperty("quizora.db", "database/quizora.db")).toAbsolutePath();
    }

    private static SQLiteConfig baseConfig() {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        // Timestamps are stored as local-time text, e.g. 2026-09-26 14:05:00.
        config.setDateClass("TEXT");
        config.setDateStringFormat(DATE_FORMAT);
        return config;
    }

    private static Connection open(SQLiteConfig config) throws SQLException {
        ensureSchema();
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile(), config.toProperties());
    }

    private static synchronized void ensureSchema() throws SQLException {
        if (schemaReady) return;
        String schema;
        try (InputStream in = databaseConnection.class.getResourceAsStream("schema.sql")) {
            if (in == null) throw new SQLException("schema.sql is missing from the application");
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Files.createDirectories(databaseFile().getParent());
        } catch (IOException error) {
            throw new SQLException("Unable to prepare the database file", error);
        }
        SQLiteConfig config = baseConfig();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile(), config.toProperties());
             var statement = connection.createStatement()) {
            // Every statement is CREATE ... IF NOT EXISTS; existing tables and data are untouched.
            statement.executeUpdate(schema);
        }
        schemaReady = true;
    }

    /** Read-only connectivity check, runnable directly from NetBeans. */
    public static void main(String[] args) throws SQLException {
        try (Connection connection = getReadOnlyConnection();
             var statement = connection.prepareStatement(
                     "SELECT sqlite_version(), (SELECT COUNT(*) FROM users)");
             var result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("SQLite returned no connection information");
            }
            System.out.println("Connected to " + databaseFile() + " on SQLite " + result.getString(1)
                    + " (" + result.getLong(2) + " users)");
        }
    }
}
