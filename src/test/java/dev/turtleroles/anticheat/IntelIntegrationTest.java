package dev.turtleroles.anticheat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.altdetector.events.*;
import com.altdetector.detection.*;

class IntelIntegrationTest {
    @TempDir Path dir;
    @Test void historyPersistsAndIsPlayerScopedAndBounded() throws Exception {
        var a=UUID.randomUUID();var b=UUID.randomUUID();long now=System.currentTimeMillis();
        try(var store=new IntelStore(dir.resolve("intel.sqlite"),s->fail(s))) {
            for(int i=0;i<20;i++)store.add(new IntelStore.Entry(now+i,a,"Alice","GRIM","check "+i)).get(5,TimeUnit.SECONDS);
            store.add(new IntelStore.Entry(now,b,"Bob","ALT","different account")).get(5,TimeUnit.SECONDS);
        }
        try(var store=new IntelStore(dir.resolve("intel.sqlite"),s->fail(s))) {
            var entries=store.recent(a).get(5,TimeUnit.SECONDS);
            assertEquals(12,entries.size());assertEquals("check 19",entries.getFirst().detail());
            assertTrue(entries.stream().allMatch(e->e.player().equals(a)));
            assertEquals(1,store.recent(b).get(5,TimeUnit.SECONDS).size());
        }
    }
    @Test void expiredEvidenceIsNotReturned() throws Exception {
        var a=UUID.randomUUID();
        try(var store=new IntelStore(dir.resolve("intel.sqlite"),s->fail(s))) {
            store.add(new IntelStore.Entry(1,a,"Alice","CLIENT","old")).get(5,TimeUnit.SECONDS);
            assertTrue(store.recent(a).get(5,TimeUnit.SECONDS).isEmpty());
        }
    }
    @Test void altAutoBanIsCancelledAndDetectionDoesNotExposeIpTags() {
        var plugin=mock(JavaPlugin.class);var server=mock(org.bukkit.Server.class);var pm=mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(pm);
        var intel=mock(ConquestIntel.class);var binding=new AltBinding(plugin,intel);
        UUID id=UUID.randomUUID();
        var result=DetectionResult.create(id,"Alice",List.of(UUID.randomUUID()),.8,RiskLevel.MEDIUM,List.of("ip:192.0.2.1"),true);
        var ban=new AltBanEvent(result);binding.ban(ban);assertTrue(ban.isCancelled());
        binding.detected(new AltDetectedEvent(result));
        var detail=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(intel).record(eq(id),eq("Alice"),eq("ALT"),detail.capture(),eq(true));
        assertFalse(detail.getValue().contains("192.0.2.1"));assertTrue(detail.getValue().contains("not proof"));
    }
    @Test void untrustedClientTextCannotInjectLegacyFormattingOrNewlines() {
        assertEquals("foo a bar",IntelStore.clean("foo§a\nbar",100));
        assertEquals(8,IntelStore.clean("x".repeat(2000),8).length());
    }
}
