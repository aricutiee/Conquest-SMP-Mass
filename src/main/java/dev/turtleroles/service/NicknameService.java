package dev.turtleroles.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Session disguises. Authentication, storage and permissions always retain the real UUID. */
public final class NicknameService implements Listener, CommandExecutor, TabCompleter, AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final PresentationService presentation;
    private final Function<String, CompletableFuture<PlayerProfile>> lookup;
    private final Map<UUID, Original> originals = new HashMap<>();
    private final Map<UUID, Long> pending = new HashMap<>();
    private final Map<UUID, Long> nextLookup = new HashMap<>();
    private long sequence;
    private volatile boolean closed;
    private Command previousNick;
    private boolean ownsCommand;
    record Original(String name, PlayerProfile profile) {}

    public NicknameService(TurtleRolesPlugin plugin, PresentationService presentation) {
        this(plugin, presentation, name -> Bukkit.createProfile(null, name).update());
    }
    NicknameService(TurtleRolesPlugin plugin, PresentationService presentation,
                    Function<String, CompletableFuture<PlayerProfile>> lookup) {
        this.plugin=plugin; this.presentation=presentation; this.lookup=lookup;
    }
    public void start() {
        var command=Objects.requireNonNull(plugin.getCommand("nick"));
        command.setExecutor(this); command.setTabCompleter(this);
        // Essentials is also installed. Own the plain command for correct client suggestions.
        previousNick=Bukkit.getCommandMap().getKnownCommands().put("nick",command);
        ownsCommand=true;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // Role demotions take effect even if a nickname was chosen earlier.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for(UUID id:List.copyOf(originals.keySet())) {
                Player p=Bukkit.getPlayer(id);
                if(p!=null&&!allowed(p)) reset(p,"Your rank no longer has nickname access.");
            }
        },20,20);
    }
    public static boolean eligible(Role role, boolean op) { return op||role.weight()>=Role.BOOSTER.weight(); }
    public static boolean validName(String name) { return name!=null&&name.matches("[A-Za-z0-9_]{3,16}"); }
    private boolean allowed(Player p) { return eligible(plugin.roleService().roleOf(p.getUniqueId()),p.isOp()); }
    private boolean staff(CommandSender s) { return !(s instanceof Player p)||p.isOp()||plugin.roleService().roleOf(p.getUniqueId()).isStaff(); }
    String realName(Player p) { var original=originals.get(p.getUniqueId());return original==null?p.getName():original.name(); }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length>0&&args[0].equalsIgnoreCase("check")) {
            if(!staff(sender)){sender.sendMessage("Only staff can check nicknames.");return true;}
            boolean found=false;
            for(Player p:Bukkit.getOnlinePlayers()) {
                String real=realName(p);
                if(args.length>1&&!real.equalsIgnoreCase(args[1])&&!p.getName().equalsIgnoreCase(args[1])&&!p.getUniqueId().toString().equalsIgnoreCase(args[1]))continue;
                if(args.length==1&&!originals.containsKey(p.getUniqueId()))continue;
                sender.sendMessage(p.getName()+" -> "+real+" | real UUID: "+p.getUniqueId()); found=true;
            }
            if(!found)sender.sendMessage(args.length>1?"No matching online player.":"No players are using a nickname.");
            return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Use /nick check [player] from console.");return true;}
        if(plugin.combat()!=null&&plugin.combat().tagged(p)&&!p.isOp()){p.sendMessage("Commands are disabled while you are in combat.");return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("reset")){reset(p,"Your original name and skin have been restored.");return true;}
        if(!allowed(p)){p.sendMessage("Nicknames are available to Booster, Booster X2 and higher ranks.");return true;}
        if(args.length!=1||!validName(args[0])){p.sendMessage("Use /nick <Minecraft username>, /nick reset, or /nick check [player].");return true;}
        String target=args[0];
        if(target.equalsIgnoreCase(realName(p))){reset(p,"Your original name and skin have been restored.");return true;}
        if(occupied(p,target)){p.sendMessage("That username is already online or in use.");return true;}
        long now=System.currentTimeMillis();
        if(nextLookup.getOrDefault(p.getUniqueId(),0L)>now){p.sendMessage("Please wait before changing your nickname again.");return true;}
        nextLookup.put(p.getUniqueId(),now+Math.max(1,plugin.getConfig().getInt("nicknames.lookup-cooldown-seconds",10))*1000L);
        long token=++sequence; UUID id=p.getUniqueId();pending.put(id,token);
        p.sendMessage("Looking up "+target+"...");
        try {
            lookup.apply(target).orTimeout(15,TimeUnit.SECONDS).whenComplete((profile,error)-> {
                if(closed||!plugin.isEnabled())return;
                Bukkit.getScheduler().runTask(plugin,()->complete(p,id,target,token,profile,error));
            });
        } catch(RuntimeException ex){pending.remove(id);p.sendMessage("Profile lookup is unavailable. Try again later.");}
        return true;
    }
    void complete(Player p,UUID id,String target,long token,PlayerProfile profile,Throwable error) {
        if(closed||!Objects.equals(pending.get(id),token))return;
        pending.remove(id);
        if(!p.isOnline()||Bukkit.getPlayer(id)!=p||!allowed(p))return;
        if(plugin.combat()!=null&&plugin.combat().tagged(p)&&!p.isOp()){p.sendMessage("Nickname change cancelled because you entered combat.");return;}
        if(error!=null||profile==null||profile.getId()==null||!profile.hasTextures()||!target.equalsIgnoreCase(profile.getName())) {
            p.sendMessage("Could not find that Minecraft account and skin. Your identity was not changed.");return;
        }
        if(occupied(p,profile.getName())){p.sendMessage("That player joined or the nickname was taken. Your identity was not changed.");return;}
        originals.computeIfAbsent(id,ignored->new Original(p.getName(),p.getPlayerProfile().clone()));
        // Paper refreshes tab, entity tracking and the player's own skin without touching inventory.
        PlayerProfile cosmetic=profile.clone();cosmetic.setId(id);
        try {
            p.setPlayerProfile(cosmetic);presentation.refreshAll();
            plugin.getLogger().info("Nickname: "+realName(p)+" ("+id+") is now shown as "+profile.getName());
            p.sendMessage("You now appear as "+profile.getName()+". Use /nick reset to restore your name and skin.");
        } catch(RuntimeException ex) {
            plugin.getLogger().warning("Could not apply nickname for "+id+": "+ex.getMessage());
            reset(p,"The nickname could not be applied; your original profile was restored.");
        }
    }
    private boolean occupied(Player subject,String name) {
        for(Player other:Bukkit.getOnlinePlayers())if(other!=subject&&(name.equalsIgnoreCase(other.getName())||name.equalsIgnoreCase(realName(other))))return true;
        return false;
    }
    void reset(Player p,String message) {
        pending.remove(p.getUniqueId());
        Original original=originals.get(p.getUniqueId());
        if(original!=null) {
            PlayerProfile restored=original.profile().clone(); restored.setId(p.getUniqueId());
            p.setPlayerProfile(restored);originals.remove(p.getUniqueId());presentation.refreshAll();
        }
        if(message!=null)p.sendMessage(message);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void joined(PlayerJoinEvent e) {
        // Run before other join listeners can overwrite presentation or duplicate-name lookups.
        String name=e.getPlayer().getName();
        for(Player other:List.copyOf(Bukkit.getOnlinePlayers())) {
            if(other!=e.getPlayer()&&originals.containsKey(other.getUniqueId())&&other.getName().equalsIgnoreCase(name))
                reset(other,"Your nickname was reset because the real "+name+" joined.");
        }
    }
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e) {
        reset(e.getPlayer(),null);nextLookup.remove(e.getPlayer().getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e) {
        String[] parts=e.getMessage().substring(1).trim().split("\\s+");
        String root=parts[0].toLowerCase(Locale.ROOT);String bare=root.substring(root.lastIndexOf(':')+1);
        if(!bare.equals("nick")&&!bare.equals("nickname"))return;
        e.setCancelled(true);onCommand(e.getPlayer(),plugin.getCommand("nick"),"nick",Arrays.copyOfRange(parts,1,parts.length));
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args) {
        List<String> options=new ArrayList<>();
        if(args.length==1){options.add("reset");if(staff(s))options.add("check");}
        if(args.length==2&&args[0].equalsIgnoreCase("check")&&staff(s))for(Player p:Bukkit.getOnlinePlayers()){options.add(p.getName());options.add(realName(p));}
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().distinct().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
    @Override public void close() {
        closed=true;pending.clear();
        for(Player p:List.copyOf(Bukkit.getOnlinePlayers()))reset(p,null);
        originals.clear();nextLookup.clear();
        if(ownsCommand) {
            var commands=Bukkit.getCommandMap().getKnownCommands();
            if(commands.get("nick")==plugin.getCommand("nick")) {
                if(previousNick!=null&&previousNick!=plugin.getCommand("nick"))commands.put("nick",previousNick);
                else commands.remove("nick");
            }
        }
    }
}
