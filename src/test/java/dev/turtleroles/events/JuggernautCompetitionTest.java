package dev.turtleroles.events;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scoreboard.*;
import java.util.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JuggernautCompetitionTest {
    ServerMock server;JavaPlugin plugin;JuggernautMaces maces;JuggernautCompetition competition;
    final AtomicLong now=new AtomicLong(1_000_000);
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();}
    @AfterEach void cleanup(){if(competition!=null)competition.close();if(maces!=null)maces.close();MockBukkit.unmock();}
    private void rewards(){maces=new JuggernautMaces(plugin,new ShockMace(Map.of()),i->false,now::get);}
    private org.mockbukkit.mockbukkit.entity.PlayerMock player(String name) {
        var player=new org.mockbukkit.mockbukkit.entity.PlayerMock(server,name){@Override public void saveData(){}};
        server.addPlayer(player);return player;
    }
    private Scoreboard board() {
        Scoreboard board=mock(Scoreboard.class);Map<String,Objective> objectives=new HashMap<>();
        var current=new java.util.concurrent.atomic.AtomicReference<Objective>();
        when(board.getObjective(anyString())).thenAnswer(i->objectives.get(i.getArgument(0)));
        when(board.getObjective(DisplaySlot.SIDEBAR)).thenAnswer(i->current.get());
        when(board.registerNewObjective(anyString(),any(Criteria.class),any(net.kyori.adventure.text.Component.class))).thenAnswer(i->{
            String name=i.getArgument(0);Objective obj=mock(Objective.class);objectives.put(name,obj);
            when(obj.getScoreboard()).thenReturn(board);when(obj.getScore(anyString())).thenAnswer(j->mock(Score.class));
            doAnswer(j->{current.set(obj);return null;}).when(obj).setDisplaySlot(DisplaySlot.SIDEBAR);
            doAnswer(j->{objectives.remove(name);if(current.get()==obj)current.set(null);return null;}).when(obj).unregister();
            return obj;
        });
        when(board.registerNewTeam(anyString())).thenAnswer(i->mock(Team.class));return board;
    }
    @Test void ranksActualDamageCapsOverkillAndUsesFirstContributorForTies() {
        var ledger=new DamageRanking();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        ledger.add(a,"Alpha",100,8);ledger.add(b,"Beta",8,20);
        ledger.add(b,"Beta",0,20);ledger.add(b,"Beta",Double.NaN,20);
        assertEquals(a,ledger.top(10).getFirst().player());assertEquals(8,ledger.top(10).getFirst().damage());
        ledger.add(b,"Beta",2,20);assertEquals(b,ledger.top(1).getFirst().player());
        for(int i=0;i<20;i++)ledger.add(UUID.randomUUID(),"Extra",1,20);
        assertEquals(10,ledger.top(10).size());
    }
    @Test void ordinaryMacesAreArchivedButActiveKitExceptionIsAllowed() {
        rewards();ItemStack ordinary=new ItemStack(Material.MACE);
        assertFalse(maces.permitted(ordinary));assertNull(maces.cleanItem(ordinary,"test inventory",0));
        assertEquals(1,Objects.requireNonNull(new File(plugin.getDataFolder(),"mace-quarantine").list()).length);
        assertEquals(Material.DIAMOND,maces.cleanItem(new ItemStack(Material.DIAMOND),"test",0).getType());
    }
    @Test void oneRewardPerEventAndTransferKeepsDeadlineAcrossRestart() {
        rewards();var a=player("Alpha");var b=player("Beta");
        UUID event=UUID.randomUUID();var winner=new DamageRanking.Entry(a.getUniqueId(),a.getName(),30);
        maces.award(event,winner,a.getLocation());maces.award(event,winner,a.getLocation());
        assertEquals(1,Arrays.stream(a.getInventory().getContents()).filter(i->i!=null&&i.getType()==Material.MACE).count());
        ItemStack prize=a.getInventory().getItem(0);assertTrue(maces.earned(prize));
        a.getInventory().clear();b.getInventory().setItem(0,prize);now.addAndGet(60_000);
        server.getScheduler().performTicks(5);
        var file=new File(plugin.getDataFolder(),"juggernaut-maces.yml");var state=YamlConfiguration.loadConfiguration(file);
        assertEquals(1_180_000,state.getLong("glow."+a.getUniqueId()));assertEquals(1_180_000,state.getLong("glow."+b.getUniqueId()));
        assertNull(a.getPotionEffect(PotionEffectType.GLOWING));assertNotNull(b.getPotionEffect(PotionEffectType.GLOWING));
        maces.close();maces=null;rewards();now.set(1_180_001);server.getScheduler().performTicks(20);
        assertNull(b.getPotionEffect(PotionEffectType.GLOWING));assertTrue(maces.earned(prize));
    }
    @Test void strongerExternalGlowIsNotShortened() {
        rewards();var a=player("Alpha");a.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,1200,0));
        maces.award(UUID.randomUUID(),new DamageRanking.Entry(a.getUniqueId(),a.getName(),20),a.getLocation());
        assertEquals(1200,a.getPotionEffect(PotionEffectType.GLOWING).getDuration());
    }
    @Test void cancelledCraftingAndOfflineWinnerDelivery() {
        rewards();
        var recipe=mock(org.bukkit.inventory.Recipe.class);when(recipe.getResult()).thenReturn(new ItemStack(Material.MACE));
        var craft=mock(org.bukkit.event.inventory.CraftItemEvent.class);when(craft.getRecipe()).thenReturn(recipe);
        maces.crafted(craft);verify(craft).setCancelled(true);
        var future=player("Future");UUID id=future.getUniqueId();future.disconnect();
        maces.award(UUID.randomUUID(),new DamageRanking.Entry(id,"Future",14),server.getWorlds().getFirst().getSpawnLocation());
        var state=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"juggernaut-maces.yml"));
        String token=state.getConfigurationSection("maces").getKeys(false).iterator().next();
        assertEquals(id.toString(),state.getString("maces."+token+".pending"));
        now.addAndGet(30_000);future.reconnect();maces.join(new org.bukkit.event.player.PlayerJoinEvent(future,net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performTicks(2);
        assertTrue(Arrays.stream(future.getInventory().getContents()).anyMatch(i->i!=null&&i.getType()==Material.MACE));
        assertEquals(150_000,JuggernautMaces.remaining(state.getLong("maces."+token+".expires"),now.get()));
    }
    @Test void fullInventoryDropsAnEarnedGlowingMace() {
        rewards();var a=player("Alpha");
        for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        maces.award(UUID.randomUUID(),new DamageRanking.Entry(a.getUniqueId(),a.getName(),12),a.getLocation());
        var dropped=a.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).stream().filter(i->i.getItemStack().getType()==Material.MACE).findFirst().orElseThrow();
        assertTrue(dropped.isGlowing());assertTrue(maces.earned(dropped.getItemStack()));
    }
    @Test void cancelledAndZeroDamageDoNotScoreAndSidebarRestores() {
        rewards();var boss=player("Boss");var a=player("Alpha");
        var kits=mock(JuggernautService.class);when(kits.isActive(boss)).thenReturn(true);when(kits.hasDesignation()).thenReturn(true);when(kits.eventId()).thenReturn(UUID.randomUUID());
        competition=new JuggernautCompetition(plugin,kits,maces);
        Scoreboard board=board();a.setScoreboard(board);boss.setScoreboard(board);
        Objective normal=board.registerNewObjective("cq_survival",Criteria.DUMMY,net.kyori.adventure.text.Component.text("CONQUEST SMP"));normal.setDisplaySlot(DisplaySlot.SIDEBAR);
        DamageSource source=DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(a).withDirectEntity(a).build();
        var cancelled=new EntityDamageByEntityEvent(a,boss,DamageCause.ENTITY_ATTACK,source,10);cancelled.setCancelled(true);server.getPluginManager().callEvent(cancelled);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(a,boss,DamageCause.ENTITY_ATTACK,source,0));
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(a,boss,DamageCause.ENTITY_ATTACK,source,4));
        server.getScheduler().performTicks(1);
        assertNotEquals(normal,board.getObjective(DisplaySlot.SIDEBAR));
        competition.close();competition=null;
        assertEquals(normal,board.getObjective(DisplaySlot.SIDEBAR));
        var saved=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"juggernaut-damage.yml"));
        assertEquals(4.0,((Number)saved.getMapList("scores").getFirst().get("damage")).doubleValue());
    }
}
