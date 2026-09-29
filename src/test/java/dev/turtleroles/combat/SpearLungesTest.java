package dev.turtleroles.combat;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import net.kyori.adventure.bossbar.BossBar;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpearLungesTest {
    ServerMock server;JavaPlugin host;Player player;SpearLunges lunges;
    AtomicLong now=new AtomicLong(100_000);YamlConfiguration config=new YamlConfiguration();
    @BeforeEach void setup(){
        server=MockBukkit.mock();host=MockBukkit.createMockPlugin();
        var original=server.addPlayer("Lunger");player=mock(Player.class);
        when(player.getName()).thenReturn("Lunger");when(player.getUniqueId()).thenReturn(original.getUniqueId());
        when(player.getPersistentDataContainer()).thenReturn(original.getPersistentDataContainer());
        when(player.getInventory()).thenReturn(original.getInventory());
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SPEAR));
        lunges=new SpearLunges(host,config,now::get);lunges.restore(player);
    }
    @AfterEach void teardown(){lunges.close();MockBukkit.unmock();}
    int gate(){return server.getScoreboardManager().getMainScoreboard().getObjective("conq_lunge_ok").getScore(player.getName()).getScore();}
    void uses(int amount){server.getScoreboardManager().getMainScoreboard().getObjective("conq_lunges").getScore(player.getName()).setScore(amount);lunges.refresh(player);}
    @Test void thirdActualUseStartsExactlyFifteenSeconds(){
        assertEquals(1,gate());uses(1);assertEquals(1,gate());uses(2);assertEquals(1,gate());uses(3);assertEquals(0,gate());
        now.addAndGet(14_999);lunges.refresh(player);assertEquals(0,gate());
        now.incrementAndGet();lunges.refresh(player);assertEquals(1,gate());
        assertEquals(0,server.getScoreboardManager().getMainScoreboard().getObjective("conq_lunges").getScore(player.getName()).getScore());
    }
    @Test void switchingHandsMaterialsLogoutAndNewModuleCannotResetRecovery(){
        uses(3);lunges.quit(new PlayerQuitEvent(player,net.kyori.adventure.text.Component.empty()));
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInOffHand(new ItemStack(Material.WOODEN_SPEAR));
        lunges=new SpearLunges(host,config,now::get);now.addAndGet(5_000);lunges.restore(player);assertEquals(0,gate());
        now.addAndGet(10_000);lunges.refresh(player);assertEquals(1,gate());
    }
    @Test void partialCounterPersistsAndOrdinaryJabsDoNotCount(){
        uses(2);lunges.quit(new PlayerQuitEvent(player,net.kyori.adventure.text.Component.empty()));
        lunges=new SpearLunges(host,config,now::get);lunges.restore(player);
        for(int i=0;i<20;i++)lunges.refresh(player);
        assertEquals(2,server.getScoreboardManager().getMainScoreboard().getObjective("conq_lunges").getScore(player.getName()).getScore());
        uses(3);assertEquals(0,gate());verify(player,atLeastOnce()).saveData();
    }
    @Test void bossbarUpdatesWithoutCreatingNewBarsAndHidesAtExpiry(){
        uses(3);var capture=org.mockito.ArgumentCaptor.forClass(BossBar.class);verify(player).showBossBar(capture.capture());
        BossBar bar=capture.getValue();now.addAndGet(7500);lunges.refresh(player);assertEquals(.5f,bar.progress(),.001);
        now.addAndGet(7500);lunges.refresh(player);verify(player).hideBossBar(bar);
    }
    @Test void deadlineExpiresWhileOfflineRatherThanRestarting(){
        uses(3);now.addAndGet(30_000);lunges=new SpearLunges(host,config,now::get);lunges.restore(player);assertEquals(1,gate());
    }

    @Test void staffBypassesLungeGateButDemotionRestoresUnexpiredCooldown(){
        uses(3);assertEquals(0,gate());lunges.close();
        var plugin=mock(dev.turtleroles.TurtleRolesPlugin.class);var roles=mock(dev.turtleroles.service.RoleService.class);
        when(plugin.getServer()).thenReturn(server);when(plugin.roleService()).thenReturn(roles);
        when(roles.roleOf(player.getUniqueId())).thenReturn(dev.turtleroles.role.Role.ADMIN);
        lunges=new SpearLunges(plugin,config,now::get);lunges.refresh(player);assertEquals(1,gate());
        when(roles.roleOf(player.getUniqueId())).thenReturn(dev.turtleroles.role.Role.HELPER);lunges.refresh(player);assertEquals(0,gate());
    }

}
