package dev.k1zik.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ViolationDatabase {
   private final Object lock = new Object();
   private final Logger logger;
   private Connection connection;

   public ViolationDatabase(Logger logger) {
      this.logger = logger;
   }

   public void open(File dataFolder) throws SQLException {
      synchronized (this.lock) {
         close();
         if (!dataFolder.exists()) {
            dataFolder.mkdirs();
         }
         File databaseFile = new File(dataFolder, "violations.db");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
         try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS player_stats (
                    uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    total_violations INTEGER NOT NULL,
                    first_violation INTEGER NOT NULL,
                    last_violation INTEGER NOT NULL
                  )
                  """);
            statement.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS filter_stats (
                    uuid TEXT NOT NULL,
                    filter_name TEXT NOT NULL,
                    count INTEGER NOT NULL,
                    PRIMARY KEY (uuid, filter_name)
                  )
                  """);
            statement.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS violation_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    filter_name TEXT NOT NULL,
                    world TEXT,
                    x INTEGER,
                    y INTEGER,
                    z INTEGER,
                    created_at INTEGER NOT NULL
                  )
                  """);
         }
      }
   }

   public Map<UUID, StoredPlayerStats> loadAll() throws SQLException {
      synchronized (this.lock) {
         ensureOpen();
         Map<UUID, StoredPlayerStats> result = new HashMap<>();
         try (PreparedStatement statement = this.connection.prepareStatement(
               "SELECT uuid, player_name, total_violations, first_violation, last_violation FROM player_stats");
              ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
               UUID playerId = UUID.fromString(rows.getString(1));
               StoredPlayerStats stats = new StoredPlayerStats(
                     rows.getString(2),
                     rows.getInt(3),
                     rows.getLong(4),
                     rows.getLong(5));
               result.put(playerId, stats);
            }
         }
         try (PreparedStatement statement = this.connection.prepareStatement(
               "SELECT uuid, filter_name, count FROM filter_stats");
              ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
               UUID playerId = UUID.fromString(rows.getString(1));
               StoredPlayerStats stats = result.get(playerId);
               if (stats != null) {
                  stats.filterCounts().put(rows.getString(2), rows.getInt(3));
               }
            }
         }
         return result;
      }
   }

   public void upsertPlayerStats(UUID playerId, String playerName, int total, long first, long last,
         Map<String, Integer> filterCounts) throws SQLException {
      synchronized (this.lock) {
         ensureOpen();
         boolean autoCommit = this.connection.getAutoCommit();
         this.connection.setAutoCommit(false);
         try {
            try (PreparedStatement upsert = this.connection.prepareStatement("""
                  INSERT INTO player_stats(uuid, player_name, total_violations, first_violation, last_violation)
                  VALUES (?, ?, ?, ?, ?)
                  ON CONFLICT(uuid) DO UPDATE SET
                    player_name=excluded.player_name,
                    total_violations=excluded.total_violations,
                    first_violation=excluded.first_violation,
                    last_violation=excluded.last_violation
                  """)) {
               upsert.setString(1, playerId.toString());
               upsert.setString(2, playerName);
               upsert.setInt(3, total);
               upsert.setLong(4, first);
               upsert.setLong(5, last);
               upsert.executeUpdate();
            }

            try (PreparedStatement delete = this.connection.prepareStatement(
                  "DELETE FROM filter_stats WHERE uuid = ?")) {
               delete.setString(1, playerId.toString());
               delete.executeUpdate();
            }

            try (PreparedStatement insert = this.connection.prepareStatement(
                  "INSERT INTO filter_stats(uuid, filter_name, count) VALUES (?, ?, ?)")) {
               for (Map.Entry<String, Integer> entry : filterCounts.entrySet()) {
                  insert.setString(1, playerId.toString());
                  insert.setString(2, entry.getKey());
                  insert.setInt(3, entry.getValue());
                  insert.addBatch();
               }
               insert.executeBatch();
            }
            this.connection.commit();
         } catch (SQLException exception) {
            this.connection.rollback();
            throw exception;
         } finally {
            this.connection.setAutoCommit(autoCommit);
         }
      }
   }

   public void insertEvent(UUID playerId, String playerName, String filterName,
         String world, Integer x, Integer y, Integer z, long createdAt) throws SQLException {
      synchronized (this.lock) {
         ensureOpen();
         try (PreparedStatement insert = this.connection.prepareStatement("""
               INSERT INTO violation_events(uuid, player_name, filter_name, world, x, y, z, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)
               """)) {
            insert.setString(1, playerId.toString());
            insert.setString(2, playerName);
            insert.setString(3, filterName);
            insert.setString(4, world);
            if (x == null) {
               insert.setNull(5, java.sql.Types.INTEGER);
            } else {
               insert.setInt(5, x);
            }
            if (y == null) {
               insert.setNull(6, java.sql.Types.INTEGER);
            } else {
               insert.setInt(6, y);
            }
            if (z == null) {
               insert.setNull(7, java.sql.Types.INTEGER);
            } else {
               insert.setInt(7, z);
            }
            insert.setLong(8, createdAt);
            insert.executeUpdate();
         }
      }
   }

   public void deletePlayer(UUID playerId) throws SQLException {
      synchronized (this.lock) {
         ensureOpen();
         try (PreparedStatement deleteStats = this.connection.prepareStatement("DELETE FROM player_stats WHERE uuid = ?");
              PreparedStatement deleteFilters = this.connection.prepareStatement("DELETE FROM filter_stats WHERE uuid = ?");
              PreparedStatement deleteEvents = this.connection.prepareStatement("DELETE FROM violation_events WHERE uuid = ?")) {
            String id = playerId.toString();
            deleteStats.setString(1, id);
            deleteFilters.setString(1, id);
            deleteEvents.setString(1, id);
            deleteStats.executeUpdate();
            deleteFilters.executeUpdate();
            deleteEvents.executeUpdate();
         }
      }
   }

   public void deleteAll() throws SQLException {
      synchronized (this.lock) {
         ensureOpen();
         try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate("DELETE FROM filter_stats");
            statement.executeUpdate("DELETE FROM player_stats");
            statement.executeUpdate("DELETE FROM violation_events");
         }
      }
   }

   public void close() {
      synchronized (this.lock) {
         if (this.connection == null) {
            return;
         }
         try {
            this.connection.close();
         } catch (SQLException exception) {
            this.logger.warning("Failed to close violations DB: " + exception.getMessage());
         } finally {
            this.connection = null;
         }
      }
   }

   private void ensureOpen() throws SQLException {
      if (this.connection == null || this.connection.isClosed()) {
         throw new SQLException("Violation database is not open");
      }
   }

   public record StoredPlayerStats(
         String playerName,
         int totalViolations,
         long firstViolation,
         long lastViolation,
         Map<String, Integer> filterCounts) {
      public StoredPlayerStats(String playerName, int totalViolations, long firstViolation, long lastViolation) {
         this(playerName, totalViolations, firstViolation, lastViolation, new HashMap<>());
      }
   }
}
