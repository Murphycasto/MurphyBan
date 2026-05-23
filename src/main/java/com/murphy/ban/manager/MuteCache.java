package com.murphy.ban.manager;

import com.murphy.ban.model.Punishment;
import com.murphy.ban.util.BanLogger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MuteCache {

    private final ConcurrentHashMap<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    public void put(UUID uuid, Punishment mute) {
        mutes.put(uuid, mute);
        BanLogger.debug("MuteCache.put uuid=" + uuid + " id=" + mute.id() + " expires=" + mute.expiresAt());
    }

    public void invalidate(UUID uuid) {
        Punishment removed = mutes.remove(uuid);
        BanLogger.debug("MuteCache.invalidate uuid=" + uuid + " hadEntry=" + (removed != null));
    }

    public Optional<Punishment> get(UUID uuid) {
        Punishment p = mutes.get(uuid);
        if (p == null) {
            BanLogger.debug("MuteCache.get uuid=" + uuid + " → miss");
            return Optional.empty();
        }
        if (p.isExpired()) {
            mutes.remove(uuid);
            BanLogger.debug("MuteCache.get uuid=" + uuid + " → expired, evicted");
            return Optional.empty();
        }
        BanLogger.debug("MuteCache.get uuid=" + uuid + " → hit id=" + p.id());
        return Optional.of(p);
    }

    public void clear() {
        mutes.clear();
    }
}