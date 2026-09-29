package dev.turtleroles.anticheat;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Local evidence only. No IP addresses, tokens, or external uploads. */
public final class IntelStore implements AutoCloseable {
    public record Entry(long time, UUID player, String name, String source, String detail) {}
    private final Path file;
    private final Consumer<String> error;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(2048), r -> { var t=new Thread(r,"Conquest-intel"); t.setDaemon(true); return t; });
    private Connection db;
    private long nextPrune;
    public IntelStore(Path file, Consumer<String> error) { this.file=file; this.error=error; }
    private Connection database() throws Exception {
        if(db==null) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
            try(var s=db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS evidence(time INTEGER NOT NULL, player TEXT NOT NULL, name TEXT NOT NULL, source TEXT NOT NULL, detail TEXT NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS evidence_player_time ON evidence(player,time)");
                s.execute("CREATE INDEX IF NOT EXISTS evidence_time ON evidence(time)");
            }
        }
        long now=System.currentTimeMillis();
        if(now>=nextPrune) {
            try(var p=db.prepareStatement("DELETE FROM evidence WHERE time < ?")) {
                p.setLong(1,now-Duration.ofDays(30).toMillis()); p.executeUpdate();
            }
            nextPrune=now+Duration.ofHours(1).toMillis();
        }
        return db;
    }
    private <T> CompletableFuture<T> submit(Callable<T> work) {
        var result=new CompletableFuture<T>();
        try { worker.execute(()-> {try {result.complete(work.call());}catch(Exception ex){error.accept("Intel storage: "+ex.getClass().getSimpleName());result.completeExceptionally(ex);}}); }
        catch(RejectedExecutionException ex) {error.accept("Intel queue unavailable; evidence request rejected.");result.completeExceptionally(ex);}
        return result;
    }
    public CompletableFuture<Void> add(Entry entry) {
        return submit(()-> {
            try(var p=database().prepareStatement("INSERT INTO evidence VALUES(?,?,?,?,?)")) {
                p.setLong(1,entry.time());p.setString(2,entry.player().toString());p.setString(3,clean(entry.name(),32));
                p.setString(4,clean(entry.source(),24));p.setString(5,clean(entry.detail(),1600));p.executeUpdate();
            } return null;
        });
    }
    public CompletableFuture<List<Entry>> recent(UUID player) {
        return submit(()-> {
            var result=new ArrayList<Entry>();
            try(var p=database().prepareStatement("SELECT time,name,source,detail FROM evidence WHERE player=? AND time>=? ORDER BY time DESC LIMIT 12")) {
                p.setString(1,player.toString()); p.setLong(2,System.currentTimeMillis()-Duration.ofDays(30).toMillis());
                try(var rs=p.executeQuery()) { while(rs.next())result.add(new Entry(rs.getLong(1),player,rs.getString(2),rs.getString(3),rs.getString(4))); }
            } return List.copyOf(result);
        });
    }
    /** Each source retains its own latest result, regardless of other sources' traffic. */
    public CompletableFuture<List<Entry>> summary(UUID player) {
        return submit(()-> {
            var result=new ArrayList<Entry>();
            for(String source:List.of("ALT","CLIENT","GRIM")) {
                try(var p=database().prepareStatement("SELECT time,name,detail FROM evidence WHERE player=? AND source=? AND time>=? ORDER BY time DESC LIMIT 1")) {
                    p.setString(1,player.toString());p.setString(2,source);p.setLong(3,System.currentTimeMillis()-Duration.ofDays(30).toMillis());
                    try(var rs=p.executeQuery()){if(rs.next())result.add(new Entry(rs.getLong(1),player,rs.getString(2),source,rs.getString(3)));}
                }
            }
            return List.copyOf(result);
        });
    }
    public CompletableFuture<ModDetectorReport.Result> mods(Path path,UUID player) {
        return submit(()->ModDetectorReport.read(path,player));
    }
    public static String clean(String s,int max) {
        if(s==null)return "unknown";
        s=s.replaceAll("[\\p{Cntrl}§]", " ");return s.substring(0,Math.min(max,s.length()));
    }
    @Override public void close() {
        // Queue the close after accepted writes, then drain without Bukkit calls.
        try {
            Runnable close=()->{try{if(db!=null)db.close();}catch(SQLException ex){error.accept("Intel database close failed");}};
            try{worker.execute(close);}catch(RejectedExecutionException full){if(!worker.isShutdown())worker.getQueue().put(close);}
        }
        catch(InterruptedException ex){Thread.currentThread().interrupt();}
        worker.shutdown();
        try {if(!worker.awaitTermination(5,TimeUnit.SECONDS))error.accept("Intel writer still draining during shutdown.");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();}
    }
}
