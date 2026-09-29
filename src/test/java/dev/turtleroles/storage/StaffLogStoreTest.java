package dev.turtleroles.storage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffLogStoreTest {
    @TempDir Path folder;
    UUID staff=UUID.randomUUID(),other=UUID.randomUUID();
    Instant now=Instant.parse("2026-09-29T12:00:00Z");
    ZoneId zone=ZoneId.of("Africa/Casablanca");
    StaffLogStore store;
    @BeforeEach void setup() throws Exception {
        try(SQLiteDatabase db=new SQLiteDatabase(folder.resolve("roles.db"))) {
            db.open();var players=new PlayerRepository(db);players.upsertKnownPlayer(staff,"Staff");players.upsertKnownPlayer(other,"Member");
            players.setRole(staff,dev.turtleroles.role.Role.HELPER,dev.turtleroles.policy.Actor.systemConsole(),"test");
            try(Statement s=db.connection().createStatement()){s.execute("INSERT INTO punishments(type,target_uuid,target_name,issuer_uuid,issuer_name,issuer_role_id,reason,created_at,revoked) VALUES('KICK','"+other+"','Member','"+staff+"','Staff','helper','Test',"+now.minusSeconds(86400*8).toEpochMilli()+",1)");}
        }
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+folder.resolve("util.db"));Statement s=c.createStatement()){
            s.execute("CREATE TABLE punishments(id INTEGER PRIMARY KEY,staff_uuid TEXT,target_name TEXT,type TEXT,reason TEXT,created_at INTEGER,expires_at INTEGER,revoked_by_uuid TEXT)");
            s.execute("INSERT INTO punishments VALUES(1,'"+staff+"','Member','MUTE','Test',"+now.toEpochMilli()+",NULL,NULL)");
            s.execute("INSERT INTO punishments VALUES(2,'"+staff+"','Member','CLEAR_INVENTORY','Excluded',"+now.toEpochMilli()+",NULL,NULL)");
        }
        store=new StaffLogStore(folder.resolve("roles.db"),folder.resolve("util.db"));
    }
    @Test void aggregatesBothStoresIncludesOfflineStaffAndRevokedActions() throws Exception {
        var list=store.roster(now,zone);assertEquals(1,list.size());assertEquals(staff,list.getFirst().uuid());
        assertEquals(new StaffLogStore.Counts(2,1,1),list.getFirst().counts());
        var entries=store.entries(staff,StaffLogStore.Period.ALL,now,zone,0);assertEquals(2,entries.size());assertEquals("MUTE",entries.getFirst().type());assertTrue(entries.getLast().revoked());
        assertEquals(1,store.entries(staff,StaffLogStore.Period.WEEK,now,zone,0).size());assertTrue(store.entries(other,StaffLogStore.Period.ALL,now,zone,0).isEmpty());
    }
    @Test void todayUsesLocalMidnightAndReadOnlyQueriesNeverCreateMissingDatabase() throws Exception {
        assertEquals(Instant.parse("2026-09-28T23:00:00Z").toEpochMilli(),StaffLogStore.Period.TODAY.since(now,zone));
        var missing=new StaffLogStore(folder.resolve("missing.db"),folder.resolve("util.db"));assertThrows(SQLException.class,()->missing.roster(now,zone));assertFalse(Files.exists(folder.resolve("missing.db")));
    }
    @Test void paginationHasLookaheadWithoutRepeatingEntries() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+folder.resolve("util.db"));PreparedStatement s=c.prepareStatement("INSERT INTO punishments VALUES(? ,?,'Member','WARNING','Test',?,NULL,NULL)")){
            for(int i=3;i<103;i++){s.setInt(1,i);s.setString(2,staff.toString());s.setLong(3,now.minusSeconds(i).toEpochMilli());s.executeUpdate();}
        }
        var first=store.entries(staff,StaffLogStore.Period.ALL,now,zone,0);var second=store.entries(staff,StaffLogStore.Period.ALL,now,zone,1);
        assertEquals(46,first.size());assertEquals(first.get(45),second.getFirst());assertFalse(second.contains(first.getFirst()));
    }
}
