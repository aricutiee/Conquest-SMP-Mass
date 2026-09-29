package dev.turtleroles.service;

import java.util.UUID;

/** Optional client presentation only. Never changes Bukkit or database identities. */
interface NicknameIdentity extends AutoCloseable {
    void set(UUID real, UUID shown);
    void viewer(UUID id, boolean excluded);
    void forgetViewer(UUID id);
    @Override void close();
}
