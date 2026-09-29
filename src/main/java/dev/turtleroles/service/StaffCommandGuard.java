package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.*;

/** Managed OP is constrained at the player-command boundary, including aliases. */
public final class StaffCommandGuard implements Listener {
    private final TurtleRolesPlugin plugin;
    private final RoleService roles;
    private static final Set<String> SAFE = Set.of(
        "nick","rtp","npc","afk","shards","leaderboard","setword","string","help","?","list","version","plugins","pl","about","me","msg","tell","w","reply","r",
        "role","tr","invsee","warn","warnings","unwarn","mute","tempmute","unmute","ban","tempban","unban","kick","history","case",
        "gamemode","tp","teleport","tphere","tpa","tpaccept","tpdeny","tpcancel","back","spawn","home","homes","sethome","delhome","enderchest",
        "smp","kit","race","roll","events","event","warlordevent","warlord","juggernaut","util","report","reports","staff","staffmode","staffchat","sc",
        "worldspawn","setworldspawn","dimensions","alt","altadmin","clientpolicy","grim","conquestac","ss","admit","heal","feed","fly","speed","clear","give","effect","enchant",
        "kill","damage","experience","xp","time","weather","difficulty","seed","locate","gamerule","worldborder","save-all","save-on","save-off",
        "spawnpoint","setblock","fill","clone","summon","particle","playsound","stopsound","title","tellraw","teammsg"
    );
    private static final Set<String> CHAT=Set.of("me","msg","tell","w","reply","r","report","staffchat","sc","teammsg");
    public StaffCommandGuard(TurtleRolesPlugin plugin, RoleService roles) {this.plugin=plugin;this.roles=roles;}
    public static boolean commandAllowed(String name, String body) {
        String lower=body.toLowerCase(Locale.ROOT);
        if(!SAFE.contains(name)) return false;
        // NBT, selector arguments and execution-bearing items can conceal targets/commands.
        if(lower.contains("{") || lower.contains("[") || lower.contains("command_block") || lower.contains("run_command"))return false;
        if(lower.matches(".*@[apern]\\b.*"))return false;
        if(name.equals("ss") && lower.matches(".*\\b(reload|blacklist|unblacklist)\\b.*"))return false;
        if(name.equals("util") && lower.matches(".*\\breload\\b.*"))return false;
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent e) {
        Player p=e.getPlayer();Role role=roles.roleOf(p.getUniqueId());
        String[] tokens=e.getMessage().substring(1).trim().split("\\s+");if(tokens.length==0)return;
        Command resolved=plugin.getServer().getCommandMap().getCommand(tokens[0]);
        String name=resolved==null?tokens[0]:resolved.getName();name=name.toLowerCase(Locale.ROOT);name=name.substring(name.lastIndexOf(':')+1);
        if(role==Role.OWNER) {
            if(name.equals("deop")&&tokens.length==2) {
                try {
                    var target=roles.resolveKnown(tokens[1]);
                    if(target.isPresent()&&(StaffAccess.managed(target.get().role())||target.get().role()==Role.OWNER)) {
                        e.setCancelled(true);
                        roles.setRole(target.get(),Role.MEMBER,roles.actor(p),"Owner revoked operator access using /deop");
                        Bukkit.getOfflinePlayer(target.get().uuid()).setOp(false);
                        p.sendMessage("Removed operator access and staff rank from "+target.get().lastName()+".");
                    }
                }catch(Exception ex){deny(e,"Could not revoke access: "+ex.getMessage());}
            }
            return;
        }
        if(Set.of("op","deop","lp","luckperms","permissions","perm","pex").contains(name)){deny(e,"Only Owner or server console can change operator access and permissions.");return;}
        if(!StaffAccess.managed(role))return;
        if(resolved instanceof FormattedCommandAlias || !commandAllowed(name,e.getMessage())) { deny(e,"That command requires the Owner or server console.");return; }
        if(CHAT.contains(name))return;
    }

    private void deny(PlayerCommandPreprocessEvent e,String reason){e.setCancelled(true);e.getPlayer().sendMessage(reason);}
    private boolean protectedHead(Player actor,ItemStack item) {
        if(item==null || !(item.getItemMeta() instanceof SkullMeta skull) || skull.getOwningPlayer()==null)return false;
        UUID target=skull.getOwningPlayer().getUniqueId();
        return false;
    }
    // ScreenShare 5.2 does not respect cancelled click events. Remove a blocked GUI icon
    // before its listener reads it, using that plugin's own title matcher to identify its UI.
    private boolean screenShareMenu(String title) {
        for(var registered:InventoryClickEvent.getHandlerList().getRegisteredListeners()) {
            Object listener=registered.getListener();
            if(!listener.getClass().getName().equals("cz.screenshare.SSListener"))continue;
            try {var method=listener.getClass().getDeclaredMethod("isSSInventory",String.class);method.setAccessible(true);return (boolean)method.invoke(listener,title);}
            catch(ReflectiveOperationException ex){plugin.getLogger().warning("ScreenShare menu adapter mismatch: "+ex.getMessage());return true;}
        }
        return false;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void open(InventoryOpenEvent e) {
        if(!(e.getPlayer() instanceof Player p) || !StaffAccess.managed(roles.roleOf(p.getUniqueId())) || !screenShareMenu(e.getView().getTitle()))return;
        for(int i=0;i<e.getInventory().getSize();i++) if(protectedHead(p,e.getInventory().getItem(i)))e.getInventory().setItem(i,null);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p) || !StaffAccess.managed(roles.roleOf(p.getUniqueId())) || !screenShareMenu(e.getView().getTitle()))return;
        var items=e.getView().getTopInventory().getContents();
        long heads=Arrays.stream(items).filter(i->i!=null && i.getItemMeta() instanceof SkullMeta).count();
        boolean protectedTarget=protectedHead(p,e.getCurrentItem()) || heads==1 && Arrays.stream(items).anyMatch(i->protectedHead(p,i));
        if(protectedTarget) {e.setCancelled(true);e.setCurrentItem(null);p.sendMessage("You can only screen-share players below your role.");Bukkit.getScheduler().runTask(plugin,()->p.closeInventory());}
    }
}
