package dev.turtleroles.storage;

import dev.turtleroles.role.Role;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Independent read-only connections keep GUI history queries off the tick thread. */
public final class StaffLogStore {
    private final Path roles, util;
    public StaffLogStore(Path roles, Path util) { this.roles=roles; this.util=util; }
    public record Counts(long all, long week, long today) {
        public Counts plus(Counts b) { return new Counts(all+b.all,week+b.week,today+b.today); }
    }
    public record Staff(UUID uuid,String name,Role role,Counts counts) {}
    public record Entry(String source,long id,String target,String type,String reason,long time,Long expiry,boolean revoked) {}
    public enum Period {
        ALL("All logs"), WEEK("Past 7 days"), TODAY("Today");
        public final String label;
        Period(String label) { this.label=label; }
        public long since(Instant now,ZoneId zone) {
            return switch(this) { case ALL -> 0; case WEEK -> now.minus(Duration.ofDays(7)).toEpochMilli();
                case TODAY -> now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(); };
        }
    }
    private Connection open(Path path) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:"+path.toUri()+"?mode=ro&busy_timeout=3000");
    }
    private static final String TYPES="('WARNING','TEMP_MUTE','PERM_MUTE','MUTE','TEMP_BAN','PERM_BAN','BAN','KICK')";
    public List<Staff> roster(Instant now,ZoneId zone) throws SQLException {
        Map<UUID,Counts> counts=new HashMap<>();
        for(boolean utility:new boolean[]{false,true}) {
            Path path=utility?util:roles;
            if(utility&&!Files.exists(path))continue;
            try(Connection c=open(path);PreparedStatement s=c.prepareStatement("SELECT "+(utility?"staff_uuid":"issuer_uuid")+" AS actor, COUNT(*) AS total, SUM(CASE WHEN created_at>=? THEN 1 ELSE 0 END) AS week, SUM(CASE WHEN created_at>=? THEN 1 ELSE 0 END) AS today FROM punishments WHERE type IN "+TYPES+" AND created_at<=? GROUP BY actor")) {
                s.setLong(1,Period.WEEK.since(now,zone));s.setLong(2,Period.TODAY.since(now,zone));s.setLong(3,now.toEpochMilli());
                try(ResultSet r=s.executeQuery()) { while(r.next()) {
                    String actor=r.getString("actor");if(actor==null)continue;
                    try { counts.merge(UUID.fromString(actor),new Counts(r.getLong("total"),r.getLong("week"),r.getLong("today")),Counts::plus); }
                    catch(IllegalArgumentException ignored) { /* Console/system actors are not player staff. */ }
                }}
            }
        }
        List<Staff> result=new ArrayList<>();
        try(Connection c=open(roles);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT uuid,last_name,role_id FROM players")) {
            while(r.next()) { Role role=Role.byId(r.getString("role_id")).orElse(Role.MEMBER);if(!role.isStaff())continue;
                UUID id=UUID.fromString(r.getString("uuid"));result.add(new Staff(id,r.getString("last_name"),role,counts.getOrDefault(id,new Counts(0,0,0)))); }
        }
        result.sort(Comparator.comparingInt((Staff s)->s.role.weight()).reversed().thenComparing(Staff::name,String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }
    public List<Entry> entries(UUID issuer,Period period,Instant now,ZoneId zone,int page) throws SQLException {
        if(page<0||page>100000)throw new IllegalArgumentException("Invalid page");
        int offset=page*45,limit=offset+46;
        List<Entry> result=new ArrayList<>();
        for(boolean utility:new boolean[]{false,true}) {
            Path path=utility?util:roles;if(utility&&!Files.exists(path))continue;
            String revoked=utility?"revoked_by_uuid IS NOT NULL":"revoked";
            try(Connection c=open(path);PreparedStatement s=c.prepareStatement("SELECT id,target_name,type,reason,created_at,expires_at,"+revoked+" AS reversed FROM punishments WHERE "+(utility?"staff_uuid":"issuer_uuid")+"=? AND type IN "+TYPES+" AND created_at>=? AND created_at<=? ORDER BY created_at DESC,id DESC LIMIT ?")) {
                s.setString(1,issuer.toString());s.setLong(2,period.since(now,zone));s.setLong(3,now.toEpochMilli());s.setInt(4,limit);
                try(ResultSet r=s.executeQuery()) { while(r.next()) {
                    long expiry=r.getLong("expires_at");Long expires=r.wasNull()?null:expiry;
                    result.add(new Entry(utility?"Utilities":"Commands",r.getLong("id"),r.getString("target_name"),r.getString("type"),r.getString("reason"),r.getLong("created_at"),expires,r.getBoolean("reversed")));
                }}
            }
        }
        result.sort(Comparator.comparingLong(Entry::time).reversed().thenComparing(Entry::source).thenComparing(Comparator.comparingLong(Entry::id).reversed()));
        return List.copyOf(result.subList(Math.min(offset,result.size()),Math.min(offset+46,result.size())));
    }
}
