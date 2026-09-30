package dev.turtleroles.service;

import ac.grim.grimac.api.GrimAPIProvider;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Temporary, self-only exemption. Never grants exemptions to a rank or another player. */
public final class OwnerTrollMode implements Listener, CommandExecutor, TabCompleter, AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final Map<UUID,PermissionAttachment> active=new HashMap<>();
    private BukkitTask guard;
    public OwnerTrollMode(TurtleRolesPlugin plugin){this.plugin=plugin;}
    public static boolean eligible(Role role){return role==Role.OWNER;}
    private boolean owner(Player player){return eligible(plugin.roleService().roleOf(player.getUniqueId()));}
    public void start(){
        var command=Objects.requireNonNull(plugin.getCommand("troll"));
        command.setExecutor(this);command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        guard=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(UUID id:List.copyOf(active.keySet())){
                Player player=Bukkit.getPlayer(id);
                if(player!=null&&!owner(player)){disable(player);player.sendMessage("Troll mode disabled: Owner rank required.");}
            }
        },1,1);
    }
    private void refresh(Player player){
        if(!Bukkit.getPluginManager().isPluginEnabled("GrimAC"))return;
        var user=GrimAPIProvider.get().getGrimUser(player.getUniqueId());
        if(user!=null)user.updatePermissions();
    }
    private void disable(Player player){
        PermissionAttachment attachment=active.remove(player.getUniqueId());
        if(attachment==null)return;
        player.removeAttachment(attachment);refresh(player);
        plugin.getLogger().info("Owner troll mode OFF: "+player.getName()+" ("+player.getUniqueId()+")");
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)||!owner(player)){sender.sendMessage("Only the Owner rank can use /troll for themselves.");return true;}
        if(args.length!=1||!Set.of("on","off").contains(args[0].toLowerCase(Locale.ROOT))){sender.sendMessage("Usage: /troll on|off. Currently "+(active.containsKey(player.getUniqueId())?"ON":"OFF"));return true;}
        if(args[0].equalsIgnoreCase("off")){disable(player);sender.sendMessage("Troll mode OFF. Normal anti-cheat permissions restored.");return true;}
        if(active.containsKey(player.getUniqueId())){sender.sendMessage("Troll mode is already ON.");return true;}
        if(!Bukkit.getPluginManager().isPluginEnabled("GrimAC")){sender.sendMessage("GrimAC is unavailable; troll mode was not enabled.");return true;}
        PermissionAttachment attachment=player.addAttachment(plugin);
        active.put(player.getUniqueId(),attachment);
        try{
            attachment.setPermission("grim.disabled",true);
            attachment.setPermission("grim.nosetback",true);
            attachment.setPermission("grim.nomodifypacket",true);
            refresh(player);
            plugin.getLogger().info("Owner troll mode ON: "+player.getName()+" ("+player.getUniqueId()+")");
            sender.sendMessage("Troll mode ON for you only. Grim checks and corrections bypassed. Resets on disconnect.");
        }catch(RuntimeException|LinkageError e){disable(player);sender.sendMessage("Troll mode could not enable; exemption removed.");plugin.getLogger().warning(e.toString());}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        return sender instanceof Player p&&owner(p)&&args.length==1?List.of("on","off").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();
    }
    @EventHandler public void quit(PlayerQuitEvent event){disable(event.getPlayer());}
    @Override public void close(){
        if(guard!=null)guard.cancel();
        for(UUID id:List.copyOf(active.keySet())){Player p=Bukkit.getPlayer(id);if(p!=null)disable(p);}
        active.clear();HandlerList.unregisterAll(this);
    }
}
