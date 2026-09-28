package dev.turtleroles.service;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
/** Durable last-claim times share one cooldown across both Booster tiers. */
public final class KitLedger implements AutoCloseable {
    public static final long DAY=86_400_000L;
    private final Connection db;
    public KitLedger(Path path) throws SQLException {
        db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(var s=db.createStatement()) {
            s.execute("PRAGMA synchronous=FULL");
            s.execute("CREATE TABLE IF NOT EXISTS kit_launch (id INTEGER PRIMARY KEY CHECK(id=1), launched_at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS kit_claims (uuid TEXT PRIMARY KEY, next_claim INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS kit_last_claims (uuid TEXT PRIMARY KEY, claimed_at INTEGER NOT NULL)");
            s.execute("INSERT OR IGNORE INTO kit_last_claims SELECT uuid,next_claim-86400000 FROM kit_claims");
        }
    }
    public synchronized long remaining(UUID id,long now) throws SQLException {return remaining(id,now,DAY);}
    public synchronized long remaining(UUID id,long now,long interval) throws SQLException {
        try(var s=db.prepareStatement("SELECT claimed_at FROM kit_last_claims WHERE uuid=?")) {
            s.setString(1,id.toString());
            try(var result=s.executeQuery()){return result.next()?Math.max(0,result.getLong(1)+interval-now):0;}
        }
    }
    public synchronized boolean claim(UUID id,long now) throws SQLException {return claim(id,now,DAY);}
    public synchronized boolean claim(UUID id,long now,long interval) throws SQLException {
        if(interval<=0)throw new IllegalArgumentException("Positive interval required");
        try(var s=db.prepareStatement("INSERT INTO kit_last_claims(uuid,claimed_at) VALUES (?,?) ON CONFLICT(uuid) DO UPDATE SET claimed_at=excluded.claimed_at WHERE kit_last_claims.claimed_at<=?")) {
            s.setString(1,id.toString());s.setLong(2,now);s.setLong(3,now-interval);return s.executeUpdate()==1;
        }
    }
    public synchronized boolean start(long now) throws SQLException {
        try(var s=db.prepareStatement("INSERT OR IGNORE INTO kit_launch(id,launched_at) VALUES (1,?)")) {
            s.setLong(1,now);return s.executeUpdate()==1;
        }
    }
    public synchronized long launchRemaining(long now) throws SQLException {
        try(var s=db.createStatement();var result=s.executeQuery("SELECT launched_at FROM kit_launch WHERE id=1")) {
            return result.next()?Math.max(0,result.getLong(1)+DAY-now):Long.MAX_VALUE;
        }
    }
    @Override public void close() throws SQLException {db.close();}
}
