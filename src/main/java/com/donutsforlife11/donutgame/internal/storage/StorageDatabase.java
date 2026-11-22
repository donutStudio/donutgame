package com.donutsforlife11.donutgame.internal.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** One lazy connection per active module. SQLite coordinates modules sharing the same game id. */
public final class StorageDatabase implements AutoCloseable {
    private final Path file;
    private Connection connection;
    private boolean closed;

    public StorageDatabase(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public synchronized String get(String scope, String key) {
        try {
            Connection db = connection(false);
            if (db == null) return null;
            try (var query = db.prepareStatement("SELECT value FROM storage WHERE scope=? AND key=?")) {
                query.setString(1, scope);
                query.setString(2, key);
                try (var rows = query.executeQuery()) {
                    return rows.next() ? rows.getString(1) : null;
                }
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    public synchronized void set(String scope, String key, String value) {
        if (value == null) {
            remove(scope, key);
            return;
        }
        try (var query = connection(true).prepareStatement("""
            INSERT INTO storage(scope,key,value) VALUES(?,?,?)
            ON CONFLICT(scope,key) DO UPDATE SET value=excluded.value WHERE value<>excluded.value
            """)) {
            query.setString(1, scope);
            query.setString(2, key);
            query.setString(3, value);
            query.executeUpdate();
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    public synchronized boolean remove(String scope, String key) {
        try {
            Connection db = connection(false);
            if (db == null) return false;
            try (var query = db.prepareStatement("DELETE FROM storage WHERE scope=? AND key=?")) {
                query.setString(1, scope);
                query.setString(2, key);
                return query.executeUpdate() != 0;
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    public synchronized void clear(String scope) {
        try {
            Connection db = connection(false);
            if (db == null) return;
            try (var query = db.prepareStatement("DELETE FROM storage WHERE scope=?")) {
                query.setString(1, scope);
                query.executeUpdate();
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    private Connection connection(boolean create) throws SQLException {
        if (closed) throw new IllegalStateException("Game storage is closed.");
        if (connection != null) return connection;
        if (!create && !Files.exists(file)) return null;
        Connection opened = null;
        try {
            Files.createDirectories(file.getParent());
            Class.forName("org.sqlite.JDBC");
            opened = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (var statement = opened.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA auto_vacuum=FULL");
                statement.execute("CREATE TABLE IF NOT EXISTS storage (scope TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(scope,key)) WITHOUT ROWID");
            }
            connection = opened;
            return opened;
        } catch (Exception exception) {
            if (opened != null) opened.close();
            throw new SQLException("Could not open game database " + file, exception);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        try {
            if (connection != null) connection.close();
        } catch (SQLException exception) {
            throw failure(exception);
        } finally {
            connection = null;
        }
    }

    private IllegalStateException failure(Exception exception) {
        return new IllegalStateException("Could not access game database " + file + ".", exception);
    }
}
