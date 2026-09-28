package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.nio.file.*;
import java.util.*;

public final class BoosterKits implements Listener, AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final KitLedger ledger;
    private ItemStack[] contents = new ItemStack[27];
    private UUID editor;
    private final Path template;
    private static final class Menu implements InventoryHolder {
        final UUID owner; final boolean edit; Inventory inventory;
        Menu(UUID owner, boolean edit) { this.owner=owner; this.edit=edit; }
        public Inventory getInventory() { return inventory; }
    }
    public BoosterKits(TurtleRolesPlugin plugin) throws Exception {
        this.plugin=plugin;
        template=plugin.getDataFolder().toPath().resolve("booster-kit.yml");
        if (Files.exists(template)) {
            var yaml=new YamlConfiguration(); yaml.load(template.toFile());
            for (int i=0;i<27;i++) contents[i]=yaml.getItemStack("slots."+i);
        }
        ledger=new KitLedger(plugin.getDataFolder().toPath().resolve("booster-kits.db"));
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("smp")).setExecutor((sender,command,label,args)->{
            if(!sender.hasPermission("conquestsmp.launch")){sender.sendMessage("You cannot start the SMP.");return true;}
            if(args.length!=1||!args[0].equalsIgnoreCase("start")){sender.sendMessage("Usage: /smp start");return true;}
            try {
                if(!ledger.start(System.currentTimeMillis())){sender.sendMessage("The SMP has already started. Its kit unlock timer has not been reset.");return true;}
                if(plugin.survival()!=null)plugin.survival().spawnArea().restoreBorder();
                var title=net.kyori.adventure.title.Title.title(
                    Component.text("THE CONQUEST SMP HAS STARTED",TextColor.color(0x9747D9)).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                    Component.text("⚔",TextColor.color(0xDBB0FF)),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500),java.time.Duration.ofSeconds(5),java.time.Duration.ofSeconds(1)));
                for(Player player:Bukkit.getOnlinePlayers()){
                    player.showTitle(title);player.playSound(player.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,0.7f,1f);
                }
                Bukkit.broadcast(Component.text("The Conquest SMP has started! Booster kits unlock in 24 hours.",TextColor.color(0xDBB0FF)));
                sender.sendMessage("Launch saved. Booster kits unlock in 24 hours.");
            }catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"SMP launch failed",ex);sender.sendMessage("The launch could not be saved. Contact an administrator.");}
            return true;
        });
        Objects.requireNonNull(plugin.getCommand("kit")).setExecutor((sender,command,label,args)-> {
            if (!(sender instanceof Player p)) { sender.sendMessage("Use this command in game."); return true; }
            boolean edit=args.length==1 && args[0].equalsIgnoreCase("edit");
            if (args.length>0 && !edit) { p.sendMessage("Usage: /kit [edit]"); return true; }
            if (edit && !p.isOp()) { p.sendMessage("Only operators can edit the Booster kit."); return true; }
            if (!edit && !eligible(p)) { p.sendMessage("This kit is for Boosters."); return true; }
            if (edit && editor!=null) { p.sendMessage("Someone is already editing the kit."); return true; }
            p.closeInventory();
            Menu menu=new Menu(p.getUniqueId(),edit);
            menu.inventory=Bukkit.createInventory(menu,edit?27:36,Component.text(edit?"Edit Booster shulker":"Booster kit",TextColor.color(0xB778F0)));
            for(int i=0;i<27;i++) menu.inventory.setItem(i,contents[i]==null?null:contents[i].clone());
            if(edit) editor=p.getUniqueId();
            else {
                ItemStack button=new ItemStack(Material.PURPLE_SHULKER_BOX);
                var meta=button.getItemMeta(); meta.displayName(Component.text("Claim contents (once every "+hours(p)+" hours)"));button.setItemMeta(meta);
                menu.inventory.setItem(31,button);
            }
            p.openInventory(menu.inventory); return true;
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void route(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String[] words=event.getMessage().substring(1).trim().split("\\s+");
        if(!Set.of("kit","conquestsmp:kit","essentials:kit").contains(words[0].toLowerCase(Locale.ROOT)))return;
        event.setCancelled(true);
        var command=Objects.requireNonNull(plugin.getCommand("kit"));
        command.getExecutor().onCommand(event.getPlayer(),command,"kit",Arrays.copyOfRange(words,1,words.length));
    }
    private boolean eligible(Player p) { return p.isOp() || GameplayBypass.allowed(plugin,p) || plugin.roleService().roleOf(p.getUniqueId())==Role.BOOSTER || plugin.roleService().roleOf(p.getUniqueId())==Role.BOOSTER_X2; }
    public boolean started(){
        try{return ledger.launchRemaining(System.currentTimeMillis())!=Long.MAX_VALUE;}
        catch(java.sql.SQLException ex){throw new IllegalStateException("Cannot read launch state",ex);}
    }
    public static long interval(Role role) {return role==Role.BOOSTER?3*KitLedger.DAY:KitLedger.DAY;}
    private long hours(Player p) {return interval(plugin.roleService().roleOf(p.getUniqueId()))/3_600_000L;}
    private long launchRemaining() {
        try{return ledger.launchRemaining(System.currentTimeMillis());}
        catch(java.sql.SQLException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot read kit launch",ex);return Long.MAX_VALUE;}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        if (!(e.getWhoClicked() instanceof Player p)) {e.setCancelled(true);return;}
        if(m.edit) { if(!p.isOp())e.setCancelled(true);return; }
        boolean alreadyCancelled=e.isCancelled(); e.setCancelled(true);
        if(alreadyCancelled || e.getRawSlot()!=31 || !m.owner.equals(p.getUniqueId()) || !eligible(p)) return;
        long launchWait=launchRemaining();
        if(launchWait>0&&!GameplayBypass.allowed(plugin,p)){p.sendMessage(launchWait==Long.MAX_VALUE?"Kits are locked until 24 hours after an administrator runs /smp start.":"Kits unlock 24 hours after the SMP launch. Remaining: "+((launchWait+3_599_999)/3_600_000)+" hours.");return;}
        if(Arrays.stream(contents).allMatch(i->i==null || i.getType().isAir())) {p.sendMessage("The kit has not been stocked yet.");return;}
        ItemStack[] plan=KitInventoryPlan.fit(p.getInventory().getStorageContents(),contents,p.getInventory().getMaxStackSize());
        if(plan==null) {p.sendMessage("Don't have enough space in your inventory to receive the kit.");return;}
        try {
            if(!GameplayBypass.allowed(plugin,p)&&!ledger.claim(p.getUniqueId(),System.currentTimeMillis(),hours(p)*3_600_000L)) {
                long seconds=(ledger.remaining(p.getUniqueId(),System.currentTimeMillis(),hours(p)*3_600_000L)+999)/1000;
                p.sendMessage("Next kit in "+seconds/3600+"h "+seconds%3600/60+"m "+seconds%60+"s.");return;
            }
            p.getInventory().setStorageContents(plan);p.sendMessage(GameplayBypass.allowed(plugin,p)?"Booster kit claimed with staff bypass.":"Booster kit claimed. Your next kit is available in "+hours(p)+" hours.");
            Bukkit.getScheduler().runTask(plugin,()->p.closeInventory());
        } catch(Exception ex) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Kit claim failed",ex);p.sendMessage("Kit storage is unavailable. Contact an administrator."); }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof Menu m && (!m.edit || !e.getWhoClicked().isOp())) e.setCancelled(true);
    }
    @EventHandler public void closeMenu(InventoryCloseEvent e) {
        if(!(e.getInventory().getHolder() instanceof Menu m) || !m.edit) return;
        editor=null;
        if(!e.getPlayer().isOp()) return;
        ItemStack[] proposed=e.getInventory().getContents();
        // The template uses a shulker-sized inventory; do not allow nested shulkers.
        if(Arrays.stream(proposed).anyMatch(i->i!=null && Tag.SHULKER_BOXES.isTagged(i.getType()))) { e.getPlayer().sendMessage("Kit not saved: remove nested shulker boxes.");return; }
        try {
            var yaml=new YamlConfiguration();for(int i=0;i<27;i++)yaml.set("slots."+i,proposed[i]);
            Path temp=template.resolveSibling("booster-kit.yml.tmp");yaml.save(temp.toFile());
            Files.move(temp,template,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            contents=Arrays.stream(proposed).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
            e.getPlayer().sendMessage("Booster kit saved.");
        } catch(Exception ex) {plugin.getLogger().log(java.util.logging.Level.SEVERE,"Kit template save failed",ex);e.getPlayer().sendMessage("Kit was not saved; the previous contents remain active.");}
    }
    @Override public void close() {
        for(Player p:Bukkit.getOnlinePlayers()) if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();
        try {ledger.close();}catch(Exception ex){plugin.getLogger().warning(ex.toString());}
        HandlerList.unregisterAll(this);
    }
}
