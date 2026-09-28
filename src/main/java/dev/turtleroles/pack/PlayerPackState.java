package dev.turtleroles.pack;

import java.util.UUID;

public record PlayerPackState(UUID connectionId, String revision, PackStatus status, int attempts) {
    public boolean readyFor(UUID connectionId, String revision) {
        return status == PackStatus.LOADED && this.connectionId.equals(connectionId) && this.revision.equals(revision);
    }
}
