package eu.doytchinov.tracecraft.database;

import eu.doytchinov.tracecraft.TraceCraft;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseHelper implements Runnable {
    private static final Lock DB_LOCK = new ReentrantLock();
    private static final Logger LOGGER = Logger.getLogger(DatabaseHelper.class.getName());
    private final Connection conn;
    private final PreparedStatement ps;

    public DatabaseHelper(Path dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + dbPath +
                    "?busy_timeout=10000" +      // 10 s busy wait
                    "&_journal_mode=WAL";        // activate WAL immediately

            conn = DriverManager.getConnection(url);
            try (var st = conn.createStatement()) {
                st.execute("""
                CREATE TABLE IF NOT EXISTS events(
                  id   INTEGER PRIMARY KEY AUTOINCREMENT,
                  event TEXT        NOT NULL,
                  ts    INTEGER,
                  json  TEXT
                )""");
            }

            conn.setAutoCommit(false);
            ps = conn.prepareStatement(
                    "INSERT INTO events(event, ts, json) VALUES(?,?,?)");

            LOGGER.info("Database connection initialised (%s)".formatted(dbPath));
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "DB init failed", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        DB_LOCK.lock();
        try {
            var batch = TraceCraft.QUEUE.drain(256);
            if (batch.isEmpty()) { return; }

            for (int i = 0; i < batch.size(); i++) {
                var ev = batch.get(i);
                ps.setString(1, ev.getEvent());
                ps.setLong(2, ev.getTimestamp());
                ps.setString(3, ev.toString());
                ps.addBatch();
            }

            int retries = 3;
            while (retries > 0) {
                try {
                    ps.executeBatch();
                    ps.clearBatch();
                    conn.commit();
                    break;
                } catch (SQLException ex) {
                    if (ex.getMessage().contains("SQLITE_BUSY")) {
                        retries--;
                        LOGGER.warning("Database is busy. Retrying... Remaining retries: " + retries);
                        if (retries == 0) {
                            LOGGER.severe("Failed to commit transaction after multiple retries.");
                            throw ex;
                        }
                        Thread.sleep(1000);
                    } else {
                        LOGGER.severe("SQL Exception occurred: " + ex.getMessage());
                        throw ex;
                    }
                }
            }
        } catch (SQLException | InterruptedException ex) {
            LOGGER.severe("Error during database operation: " + ex.getMessage());
        } finally {
            DB_LOCK.unlock();
        }
    }
}