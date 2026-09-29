package dev.turtleroles.anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class IntelVerdictTest {
    @TempDir Path directory;
    private final UUID id=UUID.randomUUID();
    private IntelStore.Entry entry(String source,long time,String detail){return new IntelStore.Entry(time,id,"Alice",source,detail);}
    @Test void noScanOrUnavailableCheckIsNeverDeclaredClean() {
        var lines=IntelVerdict.lines(List.of(),new ModDetectorReport.Result(false,0,List.of(),false),System.currentTimeMillis(),1,true,true,true);
        assertEquals("Verdict: CHECK INCOMPLETE",lines.getFirst());
        assertTrue(lines.contains("Suspicious mods: Not enough scan data"));
    }
    @Test void minimapPolicyMatchIsNotReportedAsProvenCheating() {
        long now=System.currentTimeMillis();
        var lines=IntelVerdict.lines(List.of(entry("CLIENT",now,"Brand=fabric; policy matches=xaeros-minimap; locale=en-US")),new ModDetectorReport.Result(true,now,List.of(),false),now,now-1000,true,true,true);
        assertEquals("Verdict: REVIEW NEEDED",lines.getFirst());
        assertTrue(lines.contains("Cheating: No recent flags detected"));
        assertTrue(lines.contains("Suspicious mods: xaeros-minimap"));
        assertFalse(String.join(" ",lines).contains("locale"));
    }
    @Test void historicalModsAreClearlyDatedAndNotCurrent() {
        long now=System.currentTimeMillis();
        var lines=IntelVerdict.lines(List.of(entry("CLIENT",now,"policy matches=none; locale=en-US")),new ModDetectorReport.Result(true,now-172800000,List.of("Freecam"),false),now,now-1000,true,true,true);
        assertTrue(lines.contains("Suspicious mods: None detected"));
        assertTrue(lines.stream().anyMatch(s->s.startsWith("Earlier mod signals (")&&s.endsWith("Freecam")));
    }
    @Test void grimFlagsDoNotEraseAltOrClientSummary() throws Exception {
        long now=System.currentTimeMillis();
        try(var store=new IntelStore(directory.resolve("db.sqlite"),s->fail(s))) {
            store.add(entry("ALT",now-1000,"match")).get(5,TimeUnit.SECONDS);
            store.add(entry("CLIENT",now-500,"policy matches=freecam;")).get(5,TimeUnit.SECONDS);
            for(int n=0;n<30;n++)store.add(entry("GRIM",now+n,"Check=Reach; VL=3")).get(5,TimeUnit.SECONDS);
            assertEquals(Set.of("ALT","CLIENT","GRIM"),store.summary(id).get(5,TimeUnit.SECONDS).stream().map(IntelStore.Entry::source).collect(java.util.stream.Collectors.toSet()));
        }
    }
    private String log(UUID target,long time,String category,String name) {
        return "{\"ts\":\""+Instant.ofEpochMilli(time)+"\",\"uuid\":\""+target+"\",\"category\":\""+category+"\",\"mod\":\"test\",\"mod_display\":\""+name+"\"}\n";
    }
    @Test void detectorReadsOnlyRequestedPlayerAndLatestScanWithoutExecutingAnything() throws Exception {
        long now=System.currentTimeMillis();Path file=directory.resolve("detections.jsonl");
        Files.writeString(file,log(id,now-100000,"CHEAT","OldMod")+log(UUID.randomUUID(),now,"CHEAT","OtherPlayerMod")+log(id,now,"UTILITY","Fabric")+log(id,now+1,"SUSPICIOUS","Freecam"));
        var report=ModDetectorReport.read(file,id);
        assertTrue(report.available());assertFalse(report.partial());assertEquals(List.of("Freecam"),report.suspicious());
    }
    @Test void malformedRecordsDoNotFabricateCleanScan() throws Exception {
        Path file=directory.resolve("detections.jsonl");Files.writeString(file,"broken\n"+"x".repeat(17000)+"\n");
        var result=ModDetectorReport.read(file,id);assertTrue(result.partial());assertEquals(0,result.time());
    }
}
