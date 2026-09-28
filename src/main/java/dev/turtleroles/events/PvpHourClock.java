package dev.turtleroles.events;

/** Wall-clock schedule: four hours between starts, with no catch-up bursts. */
final class PvpHourClock {
    long endsAt;
    long nextStart;
    final long duration;
    final long interval;
    PvpHourClock(long now, long endsAt, long nextStart, long duration, long interval) {
        this.duration = Math.max(1000, duration);
        this.interval = Math.max(this.duration, interval);
        this.endsAt = endsAt > now ? endsAt : 0;
        this.nextStart = nextStart > 0 ? nextStart : now + this.interval;
        if (this.endsAt == 0 && this.nextStart <= now) this.nextStart = now + this.interval;
    }
    boolean active() { return endsAt > 0; }
    boolean start(long now) {
        if (active()) return false;
        endsAt = now + duration;
        nextStart = now + interval;
        return true;
    }
    boolean stop() {
        if (!active()) return false;
        endsAt = 0;
        return true;
    }
    boolean expired(long now) { return active() && now >= endsAt; }
    boolean due(long now) { return !active() && now >= nextStart; }
}
