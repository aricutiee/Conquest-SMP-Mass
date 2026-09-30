package dev.turtleroles.combat;

import dev.turtleroles.service.GameplayBypass;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import java.util.function.LongSupplier;

/** The enchantment datapack counts actual impulses and closes the gate synchronously on use three. */
public final class SpearLunges implements Listener,AutoCloseable {
    static final NamespacedKey COUNT=new NamespacedKey("conquestsmp","lunge_count");
    static final NamespacedKey UNTIL=new NamespacedKey("conquestsmp","lunge_until");
    private final JavaPlugin plugin;
    private final LongSupplier clock;
    private final int limit;
    private final long duration;
    private final Objective count,gate,system;
    private final CooldownBars bars=new CooldownBars();
    private BukkitTask task;
    SpearLunges(JavaPlugin plugin,YamlConfiguration config,LongSupplier clock){
        this.plugin=plugin;this.clock=clock;
        limit=Math.clamp(config.getInt("spear.lunges-before-cooldown",3),1,100);
        duration=Math.clamp(config.getLong("spear.cooldown-seconds",15),1,3600)*1000;
        Scoreboard board=plugin.getServer().getScoreboardManager().getMainScoreboard();
        count=objective(board,"conq_lunges");gate=objective(board,"conq_lunge_ok");system=objective(board,"conq_lunge_sys");
        system.getScore("#limit").setScore(limit);
    }
    private static Objective objective(Scoreboard board,String name){
        Objective found=board.getObjective(name);
        return found==null?board.registerNewObjective(name,Criteria.DUMMY,net.kyori.adventure.text.Component.text(name)):found;
    }
    void start(){
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::restore);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,()->plugin.getServer().getOnlinePlayers().forEach(this::refresh),1,1);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if(system.getScore("#version").getScore()!=1)plugin.getLogger().severe("Install conquest-lunge.zip in the primary world's datapacks folder and restart: spear lunge bridge is missing.");
        }, 20);
    }
    void restore(Player player){
        count.getScore(player.getName()).setScore(player.getPersistentDataContainer().getOrDefault(COUNT,PersistentDataType.INTEGER,0));
        refresh(player);
    }
    void refresh(Player player){
        if(GameplayBypass.allowed(plugin,player)||(plugin instanceof dev.turtleroles.TurtleRolesPlugin conquest && conquest.isJuggernaut(player))){if(plugin instanceof dev.turtleroles.TurtleRolesPlugin c && c.isJuggernaut(player)){player.getPersistentDataContainer().remove(COUNT);player.getPersistentDataContainer().remove(UNTIL);}count.getScore(player.getName()).setScore(0);gate.getScore(player.getName()).setScore(player.isDead()?0:1);bars.hideAll(player);return;}
        String name=player.getName();long now=clock.getAsLong();
        int uses=Math.clamp(count.getScore(name).getScore(),0,limit);
        long until=player.getPersistentDataContainer().getOrDefault(UNTIL,PersistentDataType.LONG,0L);
        int saved=player.getPersistentDataContainer().getOrDefault(COUNT,PersistentDataType.INTEGER,0);
        long oldUntil=until;
        if(until>0&&until<=now){until=0;uses=0;count.getScore(name).setScore(0);}
        else if(until==0&&uses>=limit)until=now+duration;
        gate.getScore(name).setScore(until==0&&!player.isDead()?1:0);
        if(saved!=uses||until!=oldUntil){
            player.getPersistentDataContainer().set(COUNT,PersistentDataType.INTEGER,uses);
            player.getPersistentDataContainer().set(UNTIL,PersistentDataType.LONG,until);
            player.saveData();
        }
        if(until>now)bars.timer(player,"spear","Spear lunge recovery",until-now,duration);
        else if(uses>0&&(isSpear(player.getInventory().getItemInMainHand().getType())||isSpear(player.getInventory().getItemInOffHand().getType())))
            bars.show(player,"spear","Spear lunges: "+uses+"/"+limit,(float)(limit-uses)/limit);
        else bars.hide(player,"spear");
    }
    static boolean isSpear(Material material){return material.name().endsWith("_SPEAR");}
    @EventHandler public void join(PlayerJoinEvent event){restore(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){refresh(event.getPlayer());gate.getScore(event.getPlayer().getName()).setScore(0);bars.hideAll(event.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent event){refresh(event.getEntity());gate.getScore(event.getEntity().getName()).setScore(0);bars.hideAll(event.getEntity());}
    public void close(){
        if(task!=null)task.cancel();
        for(Player player:plugin.getServer().getOnlinePlayers()){refresh(player);gate.getScore(player.getName()).setScore(0);}
        bars.close();HandlerList.unregisterAll(this);
    }
}
