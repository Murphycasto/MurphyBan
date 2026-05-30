package com.murphy.ban.database;

import com.murphy.ban.ConfigManager;
import com.murphy.ban.MurphyBan;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.BanLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MySQLDatabase implements DatabaseManager {

    private static final String CREATE_PUNISHMENTS_TABLE = """
            CREATE TABLE IF NOT EXISTS punishments (
                id INT PRIMARY KEY AUTO_INCREMENT,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                ip VARCHAR(45),
                type VARCHAR(16) NOT NULL,
                reason TEXT NOT NULL,
                issued_by VARCHAR(16) NOT NULL,
                issued_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                active BOOLEAN NOT NULL DEFAULT 1,
                revoked_by VARCHAR(16) DEFAULT NULL,
                revoked_at BIGINT DEFAULT NULL
            )
            """;

    private static final String ALTER_ADD_REVOKED_BY = "ALTER TABLE punishments ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(16) DEFAULT NULL";
    private static final String ALTER_ADD_REVOKED_AT = "ALTER TABLE punishments ADD COLUMN IF NOT EXISTS revoked_at BIGINT DEFAULT NULL";

    private static final String CREATE_IP_HISTORY_TABLE = """
            CREATE TABLE IF NOT EXISTS ip_history (
                id INT PRIMARY KEY AUTO_INCREMENT,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                ip VARCHAR(45) NOT NULL,
                last_seen BIGINT NOT NULL,
                UNIQUE KEY uniq_uuid_ip (uuid, ip)
            )
            """;

    private static final String INSERT_PUNISHMENT = """
            INSERT INTO punishments
                (uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_ACTIVE_BY_UUID_AND_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE uuid = ? AND type = ? AND active = 1
            ORDER BY issued_at DESC
            LIMIT 1
            """;

    private static final String SELECT_ACTIVE_BY_IP_AND_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE ip = ? AND type = ? AND active = 1
            ORDER BY issued_at DESC
            LIMIT 1
            """;

    private static final String SELECT_HISTORY_BY_UUID = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE uuid = ?
            ORDER BY issued_at DESC
            """;

    private static final String SELECT_HISTORY_BY_STAFF = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE LOWER(issued_by) = LOWER(?)
            ORDER BY issued_at DESC
            """;

    private static final String UPDATE_EXPIRE_PUNISHMENT = """
            UPDATE punishments SET active = 0 WHERE id = ?
            """;

    private static final String UPDATE_REVOKE_PUNISHMENT = """
            UPDATE punishments SET active = 0, revoked_by = ?, revoked_at = ? WHERE id = ?
            """;

    private static final String UPSERT_IP_HISTORY = """
            INSERT INTO ip_history (uuid, player_name, ip, last_seen)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_seen = VALUES(last_seen)
            """;

    private static final String SELECT_IPS_FOR_UUID = """
            SELECT ip FROM ip_history WHERE uuid = ?
            """;

    private static final String SELECT_LATEST_IP_FOR_UUID = """
            SELECT ip FROM ip_history
            WHERE uuid = ?
            ORDER BY last_seen DESC
            LIMIT 1
            """;

    private static final String SELECT_UUIDS_FOR_IP = """
            SELECT DISTINCT uuid FROM ip_history WHERE ip = ?
            """;

    private static final String COUNT_ACTIVE_BY_TYPE = """
            SELECT COUNT(*) FROM punishments
            WHERE type = ? AND active = 1 AND (expires_at = -1 OR expires_at > ?)
            """;

    private static final String SELECT_EXPIRED_ACTIVE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE active = 1 AND expires_at != -1 AND expires_at <= ?
            """;

    private static final String SELECT_ACTIVE_WARNS_BEFORE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE type = 'WARN' AND active = 1 AND issued_at < ?
            """;

    private static final String SELECT_ALL_BY_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE type = ?
            ORDER BY issued_at DESC
            """;

    private static final String SELECT_ACTIVE_BY_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE type = ? AND active = 1
            ORDER BY issued_at DESC
            """;

    private static final String SELECT_INACTIVE_BY_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE type = ? AND active = 0
            ORDER BY issued_at DESC
            """;

    private static final String SELECT_ALL_ANY_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            ORDER BY issued_at DESC
            """;

    private static final String SELECT_ACTIVE_ANY_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE active = 1
            ORDER BY issued_at DESC
            """;

    private static final String SELECT_INACTIVE_ANY_TYPE = """
            SELECT id, uuid, player_name, ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at
            FROM punishments
            WHERE active = 0
            ORDER BY issued_at DESC
            """;

    private final MurphyBan plugin;
    private final Logger logger;
    private HikariDataSource dataSource;

    public MySQLDatabase(MurphyBan plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                ConfigManager cfg = plugin.getConfigManager();
                String jdbcUrl = "jdbc:mysql://" + cfg.getMySQLHost() + ":" + cfg.getMySQLPort()
                        + "/" + cfg.getMySQLName()
                        + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(cfg.getMySQLUser());
                config.setPassword(cfg.getMySQLPassword());
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30_000L);
                config.setIdleTimeout(600_000L);
                config.setMaxLifetime(1_800_000L);
                config.setPoolName("MurphyBan-MySQL");
                this.dataSource = new HikariDataSource(config);
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Failed to initialise MySQL connection pool", ex);
                throw ex;
            }
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> createTables() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_PUNISHMENTS_TABLE);
                stmt.execute(CREATE_IP_HISTORY_TABLE);
                migrateAddColumn(stmt, ALTER_ADD_REVOKED_BY, "revoked_by");
                migrateAddColumn(stmt, ALTER_ADD_REVOKED_AT, "revoked_at");
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to create MySQL tables", ex);
                throw new RuntimeException("Failed to create MySQL tables", ex);
            }
        });
    }

    private void migrateAddColumn(Statement stmt, String sql, String columnName) {
        try {
            stmt.execute(sql);
            logger.info("[MurphyBan] Migration: ensured column " + columnName + " on punishments.");
        } catch (SQLException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (msg.contains("duplicate") || msg.contains("already exists")) {
                logger.info("[MurphyBan] Migration: column " + columnName + " already exists, skipping.");
            } else {
                logger.log(Level.SEVERE, "Migration failed for column " + columnName, ex);
            }
        }
    }

    @Override
    public CompletableFuture<Void> savePunishment(Punishment punishment) {
        BanLogger.debug("MySQLDatabase.savePunishment type=" + punishment.type()
                + " uuid=" + punishment.uuid() + " expiresAt=" + punishment.expiresAt());
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_PUNISHMENT)) {
                ps.setString(1, punishment.uuid().toString());
                ps.setString(2, punishment.playerName());
                if (punishment.ip() != null) {
                    ps.setString(3, punishment.ip());
                } else {
                    ps.setNull(3, Types.VARCHAR);
                }
                ps.setString(4, punishment.type().name());
                ps.setString(5, punishment.reason());
                ps.setString(6, punishment.issuedBy());
                ps.setLong(7, punishment.issuedAt());
                ps.setLong(8, punishment.expiresAt());
                ps.setBoolean(9, punishment.active());
                if (punishment.revokedBy() != null) {
                    ps.setString(10, punishment.revokedBy());
                } else {
                    ps.setNull(10, Types.VARCHAR);
                }
                if (punishment.revokedAt() > 0L) {
                    ps.setLong(11, punishment.revokedAt());
                } else {
                    ps.setNull(11, Types.BIGINT);
                }
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE,
                        "savePunishment failed for uuid=" + punishment.uuid() + " type=" + punishment.type(), ex);
                throw new RuntimeException("Failed to save punishment", ex);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> getActivePunishment(UUID uuid, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_ACTIVE_BY_UUID_AND_TYPE)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rowToPunishment(rs));
                    }
                    return Optional.<Punishment>empty();
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE,
                        "getActivePunishment failed for uuid=" + uuid + " type=" + type, ex);
                throw new RuntimeException("Failed to query active punishment", ex);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> getActiveIPPunishment(String ip, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_ACTIVE_BY_IP_AND_TYPE)) {
                ps.setString(1, ip);
                ps.setString(2, type.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rowToPunishment(rs));
                    }
                    return Optional.<Punishment>empty();
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE,
                        "getActiveIPPunishment failed for ip=" + ip + " type=" + type, ex);
                throw new RuntimeException("Failed to query active IP punishment", ex);
            }
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getHistory(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_HISTORY_BY_UUID)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(rowToPunishment(rs));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getHistory failed for uuid=" + uuid, ex);
                throw new RuntimeException("Failed to query history", ex);
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getStaffHistory(String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_HISTORY_BY_STAFF)) {
                ps.setString(1, staffName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(rowToPunishment(rs));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getStaffHistory failed for staff=" + staffName, ex);
                throw new RuntimeException("Failed to query staff history", ex);
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<Void> expirePunishment(int id) {
        BanLogger.debug("MySQLDatabase.expirePunishment id=" + id);
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_EXPIRE_PUNISHMENT)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "expirePunishment failed for id=" + id, ex);
                throw new RuntimeException("Failed to expire punishment", ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> revokePunishment(int id, String revokedBy) {
        BanLogger.debug("MySQLDatabase.revokePunishment id=" + id + " revokedBy=" + revokedBy);
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_REVOKE_PUNISHMENT)) {
                ps.setString(1, revokedBy);
                ps.setLong(2, System.currentTimeMillis());
                ps.setInt(3, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "revokePunishment failed for id=" + id, ex);
                throw new RuntimeException("Failed to revoke punishment", ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveIPHistory(UUID uuid, String playerName, String ip) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_IP_HISTORY)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, ip);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE,
                        "saveIPHistory failed for uuid=" + uuid + " ip=" + ip, ex);
                throw new RuntimeException("Failed to save IP history", ex);
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> getKnownIPs(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> ips = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_IPS_FOR_UUID)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ips.add(rs.getString("ip"));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getKnownIPs failed for uuid=" + uuid, ex);
                throw new RuntimeException("Failed to query known IPs", ex);
            }
            return ips;
        });
    }

    @Override
    public CompletableFuture<Optional<String>> getLastKnownIP(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_LATEST_IP_FOR_UUID)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getString("ip"));
                    }
                    return Optional.<String>empty();
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getLastKnownIP failed for uuid=" + uuid, ex);
                throw new RuntimeException("Failed to query last known IP", ex);
            }
        });
    }

    @Override
    public CompletableFuture<List<UUID>> getAccountsOnIP(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> uuids = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_UUIDS_FOR_IP)) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        uuids.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getAccountsOnIP failed for ip=" + ip, ex);
                throw new RuntimeException("Failed to query accounts on IP", ex);
            }
            return uuids;
        });
    }

    @Override
    public CompletableFuture<Integer> countActivePunishments(PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(COUNT_ACTIVE_BY_TYPE)) {
                ps.setString(1, type.name());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "countActivePunishments failed for type=" + type, ex);
                throw new RuntimeException("Failed to count active punishments", ex);
            }
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getExpiredActivePunishments() {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_EXPIRED_ACTIVE)) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(rowToPunishment(rs));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getExpiredActivePunishments failed", ex);
                throw new RuntimeException("Failed to query expired active punishments", ex);
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getActiveWarnsBefore(long issuedAtCutoff) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_ACTIVE_WARNS_BEFORE)) {
                ps.setLong(1, issuedAtCutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(rowToPunishment(rs));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "getActiveWarnsBefore failed cutoff=" + issuedAtCutoff, ex);
                throw new RuntimeException("Failed to query age-expired warns", ex);
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getAllPunishments(PunishmentType type) {
        return queryByType(SELECT_ALL_BY_TYPE, type, "getAllPunishments");
    }

    @Override
    public CompletableFuture<List<Punishment>> getActivePunishments(PunishmentType type) {
        return queryByType(SELECT_ACTIVE_BY_TYPE, type, "getActivePunishments");
    }

    @Override
    public CompletableFuture<List<Punishment>> getInactivePunishments(PunishmentType type) {
        return queryByType(SELECT_INACTIVE_BY_TYPE, type, "getInactivePunishments");
    }

    @Override
    public CompletableFuture<List<Punishment>> getAllPunishmentsAllTypes() {
        return queryNoParam(SELECT_ALL_ANY_TYPE, "getAllPunishmentsAllTypes");
    }

    @Override
    public CompletableFuture<List<Punishment>> getActivePunishmentsAllTypes() {
        return queryNoParam(SELECT_ACTIVE_ANY_TYPE, "getActivePunishmentsAllTypes");
    }

    @Override
    public CompletableFuture<List<Punishment>> getInactivePunishmentsAllTypes() {
        return queryNoParam(SELECT_INACTIVE_ANY_TYPE, "getInactivePunishmentsAllTypes");
    }

    private CompletableFuture<List<Punishment>> queryByType(String sql, PunishmentType type, String label) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, type.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(rowToPunishment(rs));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, label + " failed for type=" + type, ex);
                throw new RuntimeException("Failed to query " + label, ex);
            }
            return results;
        });
    }

    private CompletableFuture<List<Punishment>> queryNoParam(String sql, String label) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowToPunishment(rs));
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, label + " failed", ex);
                throw new RuntimeException("Failed to query " + label, ex);
            }
            return results;
        });
    }

    private Punishment rowToPunishment(ResultSet rs) throws SQLException {
        String revokedBy = rs.getString("revoked_by");
        long revokedAt = rs.getLong("revoked_at");
        if (rs.wasNull()) {
            revokedAt = -1L;
        }
        return new Punishment(
                rs.getInt("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("ip"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getString("issued_by"),
                rs.getLong("issued_at"),
                rs.getLong("expires_at"),
                rs.getBoolean("active"),
                revokedBy,
                revokedAt
        );
    }
}