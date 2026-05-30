package com.murphy.ban.database;

import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseManager {

    CompletableFuture<Void> connect();

    CompletableFuture<Void> disconnect();

    CompletableFuture<Void> createTables();

    CompletableFuture<Void> savePunishment(Punishment punishment);

    CompletableFuture<Optional<Punishment>> getActivePunishment(UUID uuid, PunishmentType type);

    CompletableFuture<Optional<Punishment>> getActiveIPPunishment(String ip, PunishmentType type);

    CompletableFuture<List<Punishment>> getHistory(UUID uuid);

    CompletableFuture<List<Punishment>> getStaffHistory(String staffName);

    // Flips active=0 only; does not touch expires_at. Used by the natural-expiry sweeper.
    CompletableFuture<Void> expirePunishment(int id);

    // Flips active=0 and stamps revoked_by / revoked_at. Used by manual unban/unmute/unwarn.
    CompletableFuture<Void> revokePunishment(int id, String revokedBy);

    // Upserts on (uuid, ip); refreshes player_name + last_seen.
    CompletableFuture<Void> saveIPHistory(UUID uuid, String playerName, String ip);

    CompletableFuture<List<String>> getKnownIPs(UUID uuid);

    CompletableFuture<Optional<String>> getLastKnownIP(UUID uuid);

    CompletableFuture<List<UUID>> getAccountsOnIP(String ip);

    CompletableFuture<Integer> countActivePunishments(PunishmentType type);

    CompletableFuture<List<Punishment>> getExpiredActivePunishments();

    CompletableFuture<List<Punishment>> getActiveWarnsBefore(long issuedAtCutoff);

    CompletableFuture<List<Punishment>> getAllPunishments(PunishmentType type);

    CompletableFuture<List<Punishment>> getActivePunishments(PunishmentType type);

    CompletableFuture<List<Punishment>> getInactivePunishments(PunishmentType type);

    CompletableFuture<List<Punishment>> getAllPunishmentsAllTypes();

    CompletableFuture<List<Punishment>> getActivePunishmentsAllTypes();

    CompletableFuture<List<Punishment>> getInactivePunishmentsAllTypes();
}