package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import dev.turtleroles.combat.CooldownBars;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.Path;
import java.util.*;

/** Keeps /string's empty-slot filling, replacing its hard-coded 30-second timer. */
public final class RankStringCommand implements CommandExecutor,Listener,AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final Path file;
    private final YamlConfiguration data;
    private final CooldownBars bars=new CooldownBars();
    private BukkitTask tick;
    public RankStringCommand(TurtleRolesPlugin plugin){this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("string-cooldowns.yml");data=YamlConfiguration.loadConfiguration(file.toFile());}
    public static int seconds(Role role,int boosterTier){
        if(GameplayBypass.role(role))return 0;
        int base=switch(role){case COAL,BOOSTER->50;case IRON->45;case REDSTONE->40;case DIAMOND,BOOSTER_X2->30;case NETHERITE->15;default->60;};
        return Math.min(base,boosterTier>=2?30:boosterTier==1?50:60);
    }
    static long remaining(long until,long previousDuration,int seconds,long now){
        if(until<=0||seconds<=0)return 0;
        long usedAt=until-Math.max(0,previousDuration);
        return Math.max(0,usedAt+seconds*1000L-now);
    }
    private int seconds(Player p){return seconds(plugin.roleService().roleOf(p.getUniqueId()),plugin.roleService().boosterTier(p.getUniqueId()));}
    public void start(){Objects.requireNonNull(plugin.getCommand("string")).setExecutor(this);Bukkit.getPluginManager().registerEvents(this,plugin);tick=Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers()){String k=p.getUniqueId().toString();int seconds=seconds(p);long left=remaining(data.getLong(k+".until"),data.getLong(k+".duration",60000),seconds,System.currentTimeMillis());bars.timer(p,"string","String cooldown",left,seconds*1000L);}},20,20);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(args.length!=0)return false;
        if(!p.hasPermission("stringplugin.use"))return true;
        if(!ClientCompatibility.authenticated(p)||p.isDead()||plugin.combat().tagged(p))return true;
        String k=p.getUniqueId().toString();int seconds=seconds(p);
        long now=System.currentTimeMillis(),left=remaining(data.getLong(k+".until"),data.getLong(k+".duration",60000),seconds,now);
        if(seconds>0&&left>0){p.sendMessage(VirtualSpawners.text("String is ready in "+((left+999)/1000)+" seconds."));return true;}
        ItemStack[] contents=p.getInventory().getStorageContents();int amount=0;
        for(int i=0;i<contents.length;i++)if(contents[i]==null||contents[i].getType().isAir()){contents[i]=new ItemStack(Material.STRING,64);amount+=64;}
        if(amount==0){p.sendMessage(VirtualSpawners.text("No empty inventory slots. No cooldown started."));return true;}
        long old=data.getLong(k+".until"),oldDuration=data.getLong(k+".duration");data.set(k+".until",now+seconds*1000L);data.set(k+".duration",seconds*1000L);
        try{AtomicYaml.save(data,file);}catch(Exception ex){data.set(k+".until",old);data.set(k+".duration",oldDuration);p.sendMessage("Could not save cooldown. Try again later.");return true;}
        p.getInventory().setStorageContents(contents);p.saveData();p.sendMessage(VirtualSpawners.text("Received "+amount+" string. Cooldown: "+seconds+"s."));bars.timer(p,"string","String cooldown",seconds*1000L,seconds*1000L);return true;
    }
    @EventHandler public void quit(PlayerQuitEvent e){bars.hideAll(e.getPlayer());}
    @Override public void close(){if(tick!=null)tick.cancel();bars.close();HandlerList.unregisterAll(this);}
}
