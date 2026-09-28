package dev.turtleroles.service;

import dev.turtleroles.role.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BoosterAndMotdTest {
    @TempDir Path temp;
    @Test void cooldownSurvivesRestartAndCannotBeRefilledEarly() throws Exception {
        UUID player=UUID.randomUUID(); long now=1_800_000_000_000L;
        try(var ledger=new KitLedger(temp.resolve("kits.db"))) {
            assertTrue(ledger.claim(player,now));
            assertFalse(ledger.claim(player,now));
            assertFalse(ledger.claim(player,now+KitLedger.DAY-1));
            assertTrue(ledger.claim(UUID.randomUUID(),now));
        }
        try(var ledger=new KitLedger(temp.resolve("kits.db"))) {
            assertEquals(1000,ledger.remaining(player,now+KitLedger.DAY-1000));
            assertFalse(ledger.claim(player,now+1000));
            assertTrue(ledger.claim(player,now+KitLedger.DAY));
            assertFalse(ledger.claim(player,now+KitLedger.DAY));
        }
    }
    @Test void boosterTiersShareHistoryAcrossRoleChangesAndRestarts() throws Exception {
        UUID player=UUID.randomUUID();long now=1_800_000_000_000L;
        assertEquals(3*KitLedger.DAY,BoosterKits.interval(Role.BOOSTER));
        assertEquals(KitLedger.DAY,BoosterKits.interval(Role.BOOSTER_X2));
        try(var ledger=new KitLedger(temp.resolve("tiers.db"))){
            assertTrue(ledger.claim(player,now,3*KitLedger.DAY));
            assertFalse(ledger.claim(player,now+KitLedger.DAY-1,KitLedger.DAY));
            assertTrue(ledger.claim(player,now+KitLedger.DAY,KitLedger.DAY));
        }
        try(var ledger=new KitLedger(temp.resolve("tiers.db"))){
            assertFalse(ledger.claim(player,now+3*KitLedger.DAY,3*KitLedger.DAY));
            assertTrue(ledger.claim(player,now+4*KitLedger.DAY,3*KitLedger.DAY));
        }
    }
    @Test void launchStartsOnceAndSurvivesRestart() throws Exception {
        long now=1_800_000_000_000L;
        try(var ledger=new KitLedger(temp.resolve("launch.db"))){
            assertEquals(Long.MAX_VALUE,ledger.launchRemaining(now));assertTrue(ledger.start(now));
            assertEquals(KitLedger.DAY,ledger.launchRemaining(now));assertFalse(ledger.start(now+5000));
        }
        try(var ledger=new KitLedger(temp.resolve("launch.db"))){
            assertEquals(1,ledger.launchRemaining(now+KitLedger.DAY-1));
            assertEquals(0,ledger.launchRemaining(now+KitLedger.DAY));assertFalse(ledger.start(now+2*KitLedger.DAY));
        }
    }
    @Test void migratesExistingClaimsWithoutResettingCooldown() throws Exception {
        var path=temp.resolve("legacy.db");var id=UUID.randomUUID();long now=1_800_000_000_000L;
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path);var stmt=db.createStatement()){
            stmt.execute("CREATE TABLE kit_claims(uuid TEXT PRIMARY KEY,next_claim INTEGER NOT NULL)");
            stmt.execute("INSERT INTO kit_claims VALUES ('"+id+"',"+(now+KitLedger.DAY)+")");
        }
        try(var ledger=new KitLedger(path)){
            assertEquals(3*KitLedger.DAY,ledger.remaining(id,now,3*KitLedger.DAY));
            assertFalse(ledger.claim(id,now,3*KitLedger.DAY));
        }
    }
    @Test void rolesKeepExpectedOrderAndBoosterIsNotStaff() {
        assertTrue(Role.ADMIN.outranks(Role.SSER));assertTrue(Role.SSER.outranks(Role.MODERATOR));
        assertEquals(Role.SSER,Role.parse("SSR").orElseThrow());
        assertTrue(Role.HELPER.outranks(Role.BOOSTER));assertTrue(Role.MEDIA.outranks(Role.BOOSTER));
        assertTrue(Role.BOOSTER.outranks(Role.MEMBER));assertFalse(Role.BOOSTER.isStaff());
        assertTrue(Role.BOOSTER_X2.outranks(Role.BOOSTER));assertTrue(Role.MEDIA.outranks(Role.BOOSTER_X2));
        assertEquals(Role.BOOSTER_X2,Role.parse("Booster X2").orElseThrow());
        assertArrayEquals(Role.BOOSTER.palette(),Role.BOOSTER_X2.palette());
        assertFalse(Role.BOOSTER_X2.isStaff());assertTrue(StaffAccess.permissions(Role.BOOSTER_X2).isEmpty());
        assertArrayEquals(Role.OWNER.palette(),Role.SSER.palette());
        assertTrue(StaffAccess.permissions(Role.BOOSTER).isEmpty());
    }
    @Test void motdUsesSmallCapsAndMirroredMargins() {
        assertEquals("ʟᴀᴡ ᴏꜰ ᴛʜᴇ ꜱᴛʀᴏɴɢᴇꜱᴛ.",ConquestMotd.smallCaps("Law of the strongest."));
        var line=ConquestMotd.line("conquest smp",270,0xA35BDF);
        String plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
        String middle=ConquestMotd.smallCaps("conquest smp");int start=plain.indexOf(middle);
        String left=plain.substring(0,start),right=plain.substring(start+middle.length());
        assertEquals(new StringBuilder(left).reverse().toString(),right);
        assertTrue(ConquestMotd.width(plain)<=270);
    }
    @Test void bothDefaultLinesFitWithoutWrappingTheTrailingDecoration(){
        for(String phrase:new String[]{"welcome to the conquest smp","law of the strongest."}){
            var line=ConquestMotd.line(phrase,270,0xA35BDF);
            String plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
            assertTrue(ConquestMotd.width(plain)<=250);
            String middle=ConquestMotd.smallCaps(phrase);int start=plain.indexOf(middle);
            assertEquals(new StringBuilder(plain.substring(0,start)).reverse().toString(),plain.substring(start+middle.length()));
            assertNotEquals(0,line.children().getFirst().color().value());
            assertNotEquals(0,line.children().getLast().color().value());
        }
        assertEquals(6,ConquestMotd.width("ᴡ"));
    }
    @Test void privilegedBypassesAndSelectorsAreDenied() {
        for(String name:new String[]{"op","deop","execute","sudo","lp","reload","function","schedule","plugman"})
            assertFalse(StaffCommandGuard.commandAllowed(name,"/"+name+" Player"),name);
        assertFalse(StaffCommandGuard.commandAllowed("kill","/kill @a"));
        assertFalse(StaffCommandGuard.commandAllowed("tp","/tp @p ~ ~ ~"));
        assertFalse(StaffCommandGuard.commandAllowed("give","/give @s command_block"));
        assertFalse(StaffCommandGuard.commandAllowed("give","/give @s stone[custom_data={}]"));
        assertTrue(StaffCommandGuard.commandAllowed("gamemode","/gamemode creative @s"));
        assertTrue(StaffCommandGuard.commandAllowed("ss","/ss freeze LowerStaff"));
    }
}
