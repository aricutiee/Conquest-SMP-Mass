package dev.turtleroles.gui;

import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.StaffLogStore;
import dev.turtleroles.storage.StaffLogStore.*;
import dev.turtleroles.storage.StaffLogStore.Period;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

public final class StaffLogs implements Listener,CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;
    private final RoleService roles;
    private final StaffLogStore store;
    private final ZoneId zone;
    private final DateTimeFormatter date;
    private static final TextColor PURPLE=TextColor.color(0xB786F9);
    public StaffLogs(JavaPlugin plugin,RoleService roles) {
        this.plugin=plugin;this.roles=roles;
        store=new StaffLogStore(plugin.getDataFolder().toPath().resolve("turtleroles.db"),plugin.getDataFolder().toPath().resolveSibling("ServerUtil").resolve("serverutil.db"));
        ZoneId selected;
        try { selected=ZoneId.of(plugin.getConfig().getString("staff-logs.timezone","Africa/Casablanca")); }
        catch(DateTimeException e) { selected=ZoneId.of("Africa/Casablanca");plugin.getLogger().warning("Invalid staff-logs.timezone; using Africa/Casablanca"); }
        zone=selected;date=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(zone);
        var command=Objects.requireNonNull(plugin.getCommand("staff"));command.setExecutor(this);command.setTabCompleter(this);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    boolean allowed(Player p) { return p.isOp()||roles.roleOf(p.getUniqueId()).isStaff(); }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)){sender.sendMessage("Use /staff logs in game to open staff history.");return true;}
        if(!allowed(p)){p.sendMessage(Component.text("Staff only.",NamedTextColor.RED));return true;}
        if(args.length!=1||!args[0].equalsIgnoreCase("logs")){p.sendMessage("Usage: /staff logs");return true;}
        roster(p,0);return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args) {
        return sender instanceof Player p&&allowed(p)&&args.length==1&&"logs".startsWith(args[0].toLowerCase(Locale.ROOT))?List.of("logs"):List.of();
    }
    static final class Menu implements InventoryHolder {
        final UUID viewer;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        Menu(UUID viewer){this.viewer=viewer;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private Menu menu(Player p,String title) {
        Menu m=new Menu(p.getUniqueId());m.inventory=Bukkit.createInventory(m,54,Component.text(title,PURPLE));
        for(int i=45;i<54;i++)m.inventory.setItem(i,item(Material.BLACK_STAINED_GLASS_PANE," "));
        return m;
    }
    private static ItemStack item(Material material,String name,String... lore) {
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();
        meta.displayName(Component.text(name,PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(Arrays.stream(lore).map(s->Component.text(s,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)).toList());
        item.setItemMeta(meta);return item;
    }
    private void button(Menu m,int slot,Material material,String text,Runnable action) {m.inventory.setItem(slot,item(material,text));m.actions.put(slot,action);}
    private interface Query<T> {T get() throws Exception;}
    private <T> void load(Player p,Query<T> query,Consumer<T> show) {
        Menu loading=menu(p,"Staff logs | Loading");p.openInventory(loading.inventory);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try { T value=query.get();if(!plugin.isEnabled())return;
                Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&allowed(p)&&p.getOpenInventory().getTopInventory().getHolder()==loading)show.accept(value);});
            } catch(Exception e) { plugin.getLogger().warning("Staff log lookup failed: "+e.getMessage());if(!plugin.isEnabled())return;
                Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==loading){p.closeInventory();p.sendMessage(Component.text("Staff logs could not be loaded. Please try again.",NamedTextColor.RED));}});
            }
        });
    }
    private void roster(Player p,int page) {
        load(p,()->store.roster(Instant.now(),zone),staff->{
            Menu m=menu(p,"Staff logs | Page "+(page+1));int first=page*45;
            for(int i=first;i<Math.min(first+45,staff.size());i++) {
                Staff s=staff.get(i);ItemStack head=item(Material.PLAYER_HEAD,s.name()+" | "+s.role().label(),"All actions: "+s.counts().all(),"Past 7 days: "+s.counts().week(),"Today: "+s.counts().today(),"Warnings, mutes, bans and kicks","Click to view logs");
                SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(Bukkit.getOfflinePlayer(s.uuid()));head.setItemMeta(meta);
                m.inventory.setItem(i-first,head);m.actions.put(i-first,()->filters(p,s,page));
            }
            if(staff.isEmpty())m.inventory.setItem(22,item(Material.PAPER,"No staff records yet"));
            if(page>0)button(m,45,Material.ARROW,"Previous",()->roster(p,page-1));
            button(m,49,Material.CLOCK,"Refresh",()->roster(p,page));
            if(first+45<staff.size())button(m,53,Material.ARROW,"Next",()->roster(p,page+1));p.openInventory(m.inventory);
        });
    }
    private void filters(Player p,Staff staff,int rosterPage) {
        load(p,()->store.roster(Instant.now(),zone),list->{
            Staff s=list.stream().filter(v->v.uuid().equals(staff.uuid())).findFirst().orElse(null);
            if(s==null){roster(p,rosterPage);return;}
            Menu m=menu(p,s.name()+" | Logs");Period[] periods=Period.values();long[] counts={s.counts().all(),s.counts().week(),s.counts().today()};
            for(int i=0;i<3;i++){Period period=periods[i];int slot=20+i*2;m.inventory.setItem(slot,item(Material.BOOK,period.label,"Actions: "+counts[i],"Click to browse","Today uses "+zone));m.actions.put(slot,()->entries(p,s,period,0,rosterPage));}
            button(m,49,Material.ARROW,"Back to staff",()->roster(p,rosterPage));p.openInventory(m.inventory);
        });
    }
    private void entries(Player p,Staff staff,Period period,int page,int rosterPage) {
        load(p,()->store.entries(staff.uuid(),period,Instant.now(),zone,page),entries->{
            Menu m=menu(p,period.label+" | "+staff.name());
            for(int i=0;i<Math.min(45,entries.size());i++) {
                Entry e=entries.get(i);List<String> lore=new ArrayList<>();lore.add("Target: "+e.target());lore.add("Issued: "+date.format(Instant.ofEpochMilli(e.time())));
                if(e.expiry()!=null)lore.add("Expires: "+date.format(Instant.ofEpochMilli(e.expiry())));
                lore.add(e.revoked()?"Status: Revoked":"Status: Recorded");lore.add("Source: "+e.source());lore.add("Reason:");
                String reason=Objects.toString(e.reason(),"No reason").replaceAll("[\\r\\n]"," ");
                for(int start=0;start<Math.min(reason.length(),2000);start+=48)lore.add(reason.substring(start,Math.min(start+48,reason.length())));
                m.inventory.setItem(i,item(Material.PAPER,"#"+e.id()+" | "+e.type().replace('_',' '),lore.toArray(String[]::new)));
            }
            if(entries.isEmpty())m.inventory.setItem(22,item(Material.PAPER,"No actions in this period"));
            if(page>0)button(m,45,Material.ARROW,"Previous",()->entries(p,staff,period,page-1,rosterPage));
            button(m,49,Material.ARROW,"Back to filters",()->filters(p,staff,rosterPage));
            if(entries.size()>45)button(m,53,Material.ARROW,"Next",()->entries(p,staff,period,page+1,rosterPage));p.openInventory(m.inventory);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!m.viewer.equals(p.getUniqueId())||!allowed(p))return;
        Runnable action=m.actions.get(e.getRawSlot());if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{if(allowed(p)&&p.getOpenInventory().getTopInventory().getHolder()==m)action.run();});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) {if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
}
