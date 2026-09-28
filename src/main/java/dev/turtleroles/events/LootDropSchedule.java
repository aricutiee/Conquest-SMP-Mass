package dev.turtleroles.events;

/** Absolute deadlines survive restarts without catching up missed drops in a burst. */
final class LootDropSchedule {
    final long interval;
    long next;
    boolean automatic;
    LootDropSchedule(long now,long savedNext,boolean automatic,long interval) {
        this.interval=Math.max(60_000,interval);
        this.next=savedNext>0?savedNext:now+this.interval;
        this.automatic=automatic;
    }
    boolean due(long now) {return automatic && now>=next;}
    void reserve(long now) {next=now+interval;}
    void automatic(boolean value,long now) {automatic=value;if(value)reserve(now);}
}
