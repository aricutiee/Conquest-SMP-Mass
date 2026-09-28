package dev.turtleroles.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.RoleService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared administration, lifecycle and prizes for Conquest SMP events. */
public final class EventSystem implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {
    public enum State { IDLE, PREPARING, RUNNING, WON, ENDING }
    private record Pending(long expiresAt) {}
    private static final String[] IDS = {"egg", "capture", "crown", "mace", "assassins", "warlord", "pvp"};
    private static final String[] TITLES = {"Egg Hunt", "Capture Points", "King's Crown Race", "Juggernaut Mace",
            "Assassins", "Warlord's Purge", "PvP Hour"};
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 21};
    private static final String ADMIN = "shocksmp.events.admin";
    private final JavaPlugin plugin;
    private final RoleService roles;
    private final File settingsFile;
    private final YamlConfiguration settings;
    private final File rewardsFile;
    private final YamlConfiguration rewards;
    private final File stateFile;
    private final YamlConfiguration stateData;
    private final WarlordEquipment warlord;
    private final ShockMace mace;
    private final JuggernautService kits;
    private final NetheriteArmorRule netheriteArmor;
    private final JuggernautMaces maceRewards;
    private final JuggernautCompetition competition;
    private final StrengthPotions strength;
    private final WarlordRelics relics;
    private final WarlordHunt purge;
    private final EggHunt eggs;
    private final Assassins assassins;
    private final PvpHour pvpHour;
    private final Map<UUID, Pending> pendingDurations = new HashMap<>();
    private State state = State.IDLE;
    private String active;
    private UUID runId;
    private boolean manualRewarded;

    private static final class Menu implements InventoryHolder {
        final String kind;
        final String event;
        Inventory inventory;
        Menu(String kind, String event) { this.kind = kind; this.event = event; }
        @Override public Inventory getInventory() { return inventory; }
    }
    static boolean isEventMenu(Inventory inventory){return inventory.getHolder() instanceof Menu;}

    public EventSystem(JavaPlugin plugin, RoleService roles) {
        this.plugin = plugin;
        this.roles = roles;
        settingsFile = new File(plugin.getDataFolder(), "events.yml");
        if (!settingsFile.exists()) plugin.saveResource("events.yml", false);
        settings = YamlConfiguration.loadConfiguration(settingsFile);
        if(!settings.getBoolean("armor-unbreaking-4-applied",false)){
            for(String piece:java.util.List.of("helmet","chestplate","leggings","boots")){
                settings.set("juggernaut.enchantments.armor-"+piece+".unbreaking",4);
                settings.set("warlord-purge.enchantments."+piece+".unbreaking",4);
            }
            settings.set("armor-unbreaking-4-applied",true);
            try{EventFiles.save(settingsFile,settings);}catch(java.io.IOException ex){throw new IllegalStateException("Could not save armor balance",ex);}
        }
        if(!settings.getBoolean("balance-3-17-applied",false)){
            for(String piece:List.of("helmet","boots","chestplate","leggings")){
                int protection=piece.equals("helmet")||piece.equals("boots")?6:5;
                settings.set("juggernaut.enchantments.armor-"+piece+".protection",protection);
                settings.set("warlord-purge.enchantments."+piece+".protection",protection);
            }
            settings.set("juggernaut.enchantments.sword.sharpness",5);
            settings.set("warlord-purge.enchantments.sword.sharpness",6);
            settings.set("balance-3-17-applied",true);
            try{EventFiles.save(settingsFile,settings);}catch(java.io.IOException ex){throw new IllegalStateException("Could not save event balance update",ex);}
        }

        rewardsFile = new File(plugin.getDataFolder(), "event-rewards.yml");
        rewards = YamlConfiguration.loadConfiguration(rewardsFile);
        stateFile = new File(plugin.getDataFolder(), "event-state.yml");
        stateData = YamlConfiguration.loadConfiguration(stateFile);
        warlord = new WarlordEquipment(plugin, settings);
        mace = new ShockMace(plugin);
        strength = new StrengthPotions(plugin);
        kits = new JuggernautService(plugin, settings, warlord, mace, strength);
        netheriteArmor = new NetheriteArmorRule(plugin,kits::isActive);
        maceRewards = new JuggernautMaces(plugin,mace,kits::activeMace);
        competition = new JuggernautCompetition(plugin,kits,maceRewards);
        kits.onDefeatReward(competition::defeated);
        relics = new WarlordRelics(plugin,settings);
        purge = new WarlordHunt(plugin,settings,relics);
        kits.onPurgeReward((location,items)->purge.begin(kits.eventId(),location,items));
        Bukkit.getPluginManager().registerEvents(relics,plugin);
        Bukkit.getPluginManager().registerEvents(new EventRewardGuard(plugin),plugin);
        kits.onWon(this::juggernautWon);
        eggs = new EggHunt(plugin, settings);
        assassins = new Assassins(plugin, settings);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(warlord, plugin);
        Bukkit.getPluginManager().registerEvents(strength, plugin);
        Bukkit.getPluginManager().registerEvents(kits, plugin);
        Bukkit.getPluginManager().registerEvents(eggs, plugin);
        Bukkit.getPluginManager().registerEvents(assassins, plugin);
        plugin.getCommand("events").setExecutor(this);
        plugin.getCommand("event").setExecutor(this);
        plugin.getCommand("juggernaut").setExecutor(this);
        plugin.getCommand("warlordevent").setExecutor(this);
        plugin.getCommand("warlord").setExecutor(this);
        plugin.getCommand("warlord").setTabCompleter(this);
        plugin.getCommand("juggernaut").setTabCompleter(this);
        recoverState();
        pvpHour = new PvpHour(plugin, settings);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expirePrompts, 20, 20);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equalsIgnoreCase("warlordevent")||command.getName().equalsIgnoreCase("warlord")) {
            if(!canAdmin(sender,ADMIN)){sender.sendMessage("Event administration requires permission.");return true;}
            if(args.length>0&&args[0].equalsIgnoreCase("start"))
                return juggernaut(sender,args.length>1?new String[]{"warlord",args[1]}:new String[]{"warlord"});
            if(args.length>0&&args[0].equalsIgnoreCase("retry")){purge.retry();sender.sendMessage("Retrying pending hunt locations.");return true;}
            if(!(sender instanceof Player player)){sender.sendMessage("Use the menu or animation preview in game.");return true;}
            if(args.length>0&&(args[0].equalsIgnoreCase("end")||args[0].equalsIgnoreCase("preview")||args[0].equalsIgnoreCase("animation")))purge.preview(player);
            else purge.open(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("juggernaut")) return juggernaut(sender, args);
        if (command.getName().equalsIgnoreCase("event")) {
            if (args.length == 0) {
                if (sender instanceof Player player) openMain(player);
                else sender.sendMessage("Use /event end or /events status from the console.");
                return true;
            }
            if (!args[0].equalsIgnoreCase("end")) { sender.sendMessage("Usage: /event end"); return true; }
            if (!canAdmin(sender, ADMIN)) { sender.sendMessage("Event administration requires permission."); return true; }
            end(sender); return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("assassins") && args[1].equalsIgnoreCase("join")) {
            if (sender instanceof Player player) assassins.join(player);
            else sender.sendMessage("Only players may enroll.");
            return true;
        }
        if (!canAdmin(sender, ADMIN)) { sender.sendMessage(Component.text("Event administration requires permission.", NamedTextColor.RED)); return true; }
        if (args.length >= 2 && args[0].equalsIgnoreCase("mace") && args[1].equalsIgnoreCase("give")) {
            if (sender instanceof Player player) mace.give(player);
            else sender.sendMessage("Use /events mace give in game.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) openMain(player);
            else sender.sendMessage("/events start <event>, stop, winner <player>, reward <event>, status");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "loot" -> {
                if (plugin instanceof dev.turtleroles.TurtleRolesPlugin combined && combined.lootDrops() != null)
                    combined.lootDrops().command(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            case "pvp" -> {
                String action = args.length > 1 ? args[1].toLowerCase() : "status";
                switch (action) {
                    case "start", "on" -> sender.sendMessage(pvpHour.start() ? "PvP Hour started." : "PvP Hour is already running.");
                    case "stop", "off" -> { pvpHour.stop(); sender.sendMessage("PvP Hour is off."); }
                    case "auto" -> sender.sendMessage("PvP Hour is manual-only. The four-hour schedule now belongs to loot drops.");
                    case "status" -> sender.sendMessage(pvpHour.status());
                    default -> sender.sendMessage("/events pvp <start|stop|status>");
                }
            }
            case "checkloadout" -> {
                try { sender.sendMessage(kits.checkLoadouts()); }
                catch (RuntimeException ex) { sender.sendMessage("Loadout check FAILED: " + ex.getMessage()); }
            }
            case "start" -> {
                if (args.length < 2) sender.sendMessage("Usage: /events start <egg|crown|mace|assassins|blade|armor>");
                else start(sender, args[1].toLowerCase());
            }
            case "stop", "end" -> end(sender);
            case "winner" -> {
                if (args.length < 2) sender.sendMessage("Usage: /events winner <player>");
                else winner(sender, args[1]);
            }
            case "reward" -> {
                if (!(sender instanceof Player player) || args.length < 2) sender.sendMessage("Usage: /events reward <event> (in game)");
                else openReward(player, args[1].toLowerCase());
            }
            case "status" -> sender.sendMessage("Event: " + (active == null ? "none" : active) + " | " + state + " | PvP Hour: " + pvpHour.status());
            default -> sender.sendMessage("/events, /events start <event>, /events stop, /events winner <player>, /events reward <event>");
        }
        return true;
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(!canAdmin(sender,ADMIN))return java.util.List.of();
        if(args.length==1)return (command.getName().equalsIgnoreCase("warlord")
                ?java.util.stream.Stream.of("animation","event","end","preview","start","retry")
                :java.util.stream.Stream.concat(java.util.stream.Stream.of("animation","warlord","restore"),Bukkit.getOnlinePlayers().stream().map(Player::getName)))
                .filter(v->v.toLowerCase(java.util.Locale.ROOT).startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        return java.util.List.of();
    }
    private boolean juggernaut(CommandSender sender, String[] args) {
        if(args.length>0&&args[0].equalsIgnoreCase("animation")) {
            if(!canAdmin(sender,ADMIN)){sender.sendMessage("Event administration requires permission.");return true;}
            if(sender instanceof Player player)maceRewards.preview(player);
            else sender.sendMessage("Use /juggernaut animation in game.");
            return true;
        }
        if (!canAdmin(sender, "shocksmp.events.kit")) { sender.sendMessage("You cannot designate a Juggernaut."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("restore")) {
            if (sender instanceof Player player) kits.restorePending(player);
            else sender.sendMessage("Use /juggernaut restore in game after clearing inventory space.");
            return true;
        }
        if (java.util.Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("kit"))) {
            sender.sendMessage("Kits are automatic. Use /juggernaut <player> or /juggernaut warlord <player>."); return true;
        }
        boolean requestedWarlord = args.length > 0 && args[0].equalsIgnoreCase("warlord");
        boolean variant = requestedWarlord || "warlord".equals(active);
        if(variant&&purge.outstanding()){sender.sendMessage("Finish finding the previous Warlord relics before another Purge.");return true;}
        String name = args.length > (requestedWarlord ? 1 : 0) ? args[requestedWarlord ? 1 : 0] : null;
        Player target = name == null ? sender instanceof Player player ? player : null : Bukkit.getPlayerExact(name);
        if (target == null) { sender.sendMessage("Target player must be online. Usage: /juggernaut [warlord] <player>"); return true; }
        if (sender instanceof Player staff && !dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) return true;
        try { kits.validateDesignation(target, variant); }
        catch (IllegalStateException ex) { sender.sendMessage("Could not build the automatic loadout: " + ex.getMessage()); return true; }
        if (state == State.IDLE) start(sender, variant ? "warlord" : "crown");
        if (state != State.RUNNING || !("crown".equals(active) || "blade".equals(active) || "armor".equals(active) || "mace".equals(active) || "warlord".equals(active))) {
            sender.sendMessage("A different event is active. End it before designating a Juggernaut."); return true;
        }
        try {
            if(variant){active="warlord";saveState();}
            if (!kits.designate(target, runId, variant)) sender.sendMessage("A Juggernaut is already designated or has possessions awaiting restoration.");
            else Bukkit.broadcast(Component.text(target.getName() + " is the " + (variant ? "Warlord" : "Juggernaut") + "!", NamedTextColor.RED));
        } catch (IllegalStateException ex) { sender.sendMessage("Could not build the automatic loadout: " + ex.getMessage()); }
        return true;
    }

    public void openMain(Player player) {
        if (!canAdmin(player, ADMIN)) { deny(player); return; }
        Menu holder = new Menu("main", null);
        Inventory gui = Bukkit.createInventory(holder, 54, "Â§8Conquest SMP Events");
        holder.inventory = gui;
        fill(gui);
        for (int i = 0; i < IDS.length; i++) {
            ItemStack icon = icon(IDS[i]);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(TITLES[i], NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(description(IDS[i]), NamedTextColor.GRAY));
            lore.add(Component.text("Status: " + status(IDS[i]), active != null && active.equals(IDS[i]) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            lore.add(Component.text(IDS[i].equals("capture") ? "Awaiting gameplay setup" : "Click for controls", NamedTextColor.AQUA));
            meta.lore(lore); icon.setItemMeta(meta);
            gui.setItem(SLOTS[i], icon);
        }
        gui.setItem(49, button(Material.BARRIER, "Close", "No event changes"));
        gui.setItem(23, button(Material.CHEST, "Loot Drops", "Four-hour schedule and manual spawn controls"));
        player.openInventory(gui);
    }

    private void openDetail(Player player, String id) {
        if (!canAdmin(player, ADMIN)) { deny(player); return; }
        Menu holder = new Menu("detail", id);
        Inventory gui = Bukkit.createInventory(holder, 27, "Â§8" + title(id));
        holder.inventory = gui; fill(gui);
        gui.setItem(11, button(Material.LIME_DYE, "Start", description(id)));
        gui.setItem(13, icon(id));
        gui.setItem(15, button(Material.RED_DYE, "Stop", "Current event: " + (active == null ? "none" : title(active))));
        gui.setItem(18, button(Material.ARROW, "Back", "Event list"));
        if (id.equals("pvp")) {
            gui.setItem(13, button(Material.GOLDEN_SWORD, "PvP Hour", pvpHour.status()));
            gui.setItem(15, button(Material.RED_DYE, "Stop PvP Hour", "Turn the locator bar off now"));
            gui.setItem(22, button(Material.LEVER, "Manual only", "Use Start or Stop; no automatic starts"));
        } else gui.setItem(22, button(Material.EMERALD, "Reward", "Click to edit this event's prize"));
        player.openInventory(gui);
    }

    private void start(CommandSender sender, String id) {
        if(id.equals("blade")||id.equals("armor"))id="warlord";
        if (!known(id)) { sender.sendMessage("Unknown event. Use /events to view the list."); return; }
        if (id.equals("pvp")) { sender.sendMessage(pvpHour.start() ? "PvP Hour started." : "PvP Hour is already running."); return; }
        if (id.equals("capture")) { sender.sendMessage("Capture Points is awaiting gameplay setup. No event has started."); return; }
        if (state != State.IDLE) { sender.sendMessage("Another competitive event is " + state + ": " + title(active) + ". Stop it first."); return; }
        active = id; state = State.PREPARING; runId = UUID.randomUUID(); manualRewarded = false;
        saveState();
        if (id.equals("egg")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Start Egg Hunt in game to enter its chat duration."); reset(); return; }
            pendingDurations.put(player.getUniqueId(), new Pending(System.currentTimeMillis() + 60000));
            player.closeInventory();
            player.sendMessage(Component.text("Enter the Egg Hunt duration privately in chat, for example 30s, 5 minutes, or 1h 30m. Type cancel to abort. Setup expires in 60 seconds.", NamedTextColor.GOLD));
            return;
        }
        if (id.equals("assassins")) {
            assassins.openJoinWindow(() -> { state = State.RUNNING; saveState(); },
                    this::completeGameplay, p -> award("assassins", p));
            sender.sendMessage("Assassins join window opened for " + settings.getLong("assassins.join-window-seconds", 60) + " seconds.");
            return;
        }
        state = State.RUNNING;
        saveState();
        String announcement = switch (id) {
            case "crown" -> "The King's Crown Race has begun! Designate a player with /juggernaut <player> or /juggernaut warlord <player>.";
            case "mace" -> "The Juggernaut Mace event has begun! Use /juggernaut <player>. The mace rises, shakes and drops publicly when the Juggernaut is defeated.";
            case "blade", "armor", "warlord" -> title(id) + " has begun! Use /juggernaut warlord <player> for an automatic loadout.";
            default -> title(id) + " has begun!";
        };
        Bukkit.broadcast(Component.text(announcement, NamedTextColor.GOLD));
    }

    private void end(CommandSender sender) {
        boolean endedPvp = pvpHour.stop();
        if (state == State.IDLE) { if (!endedPvp) sender.sendMessage("No event is active."); return; }
        UUID endingId = runId == null ? kits.eventId() : runId;
        state = State.ENDING;
        saveState();
        pendingDurations.clear();
        if ("egg".equals(active)) {
            if (eggs.preparingOrRunning()) eggs.stop(false);
            else Bukkit.broadcast(Component.text("Egg Hunt setup was cancelled.", NamedTextColor.YELLOW));
        }
        else if ("assassins".equals(active)) assassins.stop(false);
        if (endingId != null) kits.end(endingId);
        competition.clearBoards();
        reset();
        for (Player player : Bukkit.getOnlinePlayers()) player.showTitle(Title.title(
                Component.text("EVENT ENDED", NamedTextColor.RED), Component.text("Restrictions released", NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("EVENT ENDED. Mythic pickup limits for this event are released.", NamedTextColor.GOLD));
    }

    private void winner(CommandSender sender, String name) {
        if(kits.hasDesignation()||"mace".equals(active)||"warlord".equals(active)){sender.sendMessage("The Juggernaut mace rises into the sky and drops publicly when the boss dies.");return;}
        if (state != State.RUNNING || active == null || active.equals("egg") || active.equals("assassins")) {
            sender.sendMessage("Use /events winner only for an active manual race or trial."); return;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) { sender.sendMessage("The winner must be online so the prize can be delivered safely."); return; }
        if (manualRewarded) { sender.sendMessage("A winner has already been selected for this event."); return; }
        manualRewarded = true;
        Bukkit.broadcast(Component.text(title(active) + " winner: " + player.getName() + "!", NamedTextColor.GOLD));
        award(active, player);
        completeGameplay();
    }

    private void award(String id, Player player) {
        ItemStack prize = rewards.getItemStack("prizes." + id);
        if (prize == null || prize.getType().isAir() || dev.turtleroles.migration.RetiredShockCleanup.containsCrystal(prize)) {
            player.sendMessage(Component.text("No item prize is configured for this event.", NamedTextColor.YELLOW)); return;
        }
        ItemStack safePrize = prize.clone();
        if(safePrize.getType()==Material.MACE){player.sendMessage("Maces can only be earned by defeating the Juggernaut.");return;}
        player.getInventory().addItem(safePrize).values().forEach(extra ->
                player.getWorld().dropItemNaturally(player.getLocation(), extra));
        player.sendMessage(Component.text("You received the " + title(id) + " prize.", NamedTextColor.GOLD));
    }

    private void openReward(Player player, String id) {
        if (!known(id) || id.equals("capture") || id.equals("pvp")) { player.sendMessage("Unknown or unconfigured event."); return; }
        if (!canAdmin(player, "shocksmp.events.reward")) { deny(player); return; }
        Menu holder = new Menu("reward", id);
        Inventory gui = Bukkit.createInventory(holder, 9, "Â§8Reward: " + title(id));
        holder.inventory = gui;
        ItemStack current = rewards.getItemStack("prizes." + id);
        if (current != null) gui.setItem(4, current.clone());
        if (id.equals("mace")) gui.setItem(0, displayOnly(mace.create()));
        gui.setItem(8, button(Material.WRITABLE_BOOK, "Save on close", "Submit an item in the center; empty-hand click clears it"));
        player.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu holder)) return;
        if (holder.kind.equals("reward")) {
            int slot = event.getRawSlot();
            if (slot >= event.getView().getTopInventory().getSize()) {
                if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK
                        || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR)
                    event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            if (slot == 0 && holder.event.equals("mace") && canAdmin(event.getWhoClicked(), "shocksmp.events.reward")) {
                event.getWhoClicked().sendMessage("The Juggernaut mace drops publicly after its defeat animation.");
                return;
            }
            if (slot == 4 && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                ItemStack submitted = event.getCursor();
                if(submitted!=null&&submitted.getType()==Material.MACE){event.getWhoClicked().sendMessage("Maces are reserved for automatic Juggernaut rewards.");return;}
                if (dev.turtleroles.migration.RetiredShockCleanup.containsCrystal(submitted)) {
                    event.getWhoClicked().sendMessage("Retired Shock crystals cannot be event prizes.");
                    return;
                }
                event.getView().getTopInventory().setItem(4,
                        submitted == null || submitted.getType().isAir() ? null : submitted.clone());
                if (submitted != null && !submitted.getType().isAir()) event.setCursor(null);
            }
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.isShiftClick()
                || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.DOUBLE_CLICK) return;
        Player player = (Player) event.getWhoClicked();
        if (!canAdmin(player, ADMIN)) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (holder.kind.equals("main")) {
            if (slot == 23) { player.performCommand("conquestsmp:events loot"); return; }
            if (slot == 49) { player.closeInventory(); return; }
            for (int i = 0; i < SLOTS.length; i++) if (slot == SLOTS[i]) {
                if (IDS[i].equals("egg") && state == State.IDLE) start(player, "egg");
                else openDetail(player, IDS[i]);
                return;
            }
        } else if (holder.kind.equals("detail")) {
            switch (slot) {
                case 11 -> { player.closeInventory(); start(player, holder.event); }
                case 15 -> { player.closeInventory(); if (holder.event.equals("pvp")) pvpHour.stop(); else end(player); }
                case 18 -> openMain(player);
                case 22 -> { if (!holder.event.equals("pvp")) openReward(player, holder.event); }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu holder)) return;
        if (!holder.kind.equals("reward")) { event.setCancelled(true); return; }
        for (int slot : event.getRawSlots()) if (slot < 9) { event.setCancelled(true); return; }
    }

    @EventHandler public void onRewardClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu holder) || !holder.kind.equals("reward")) return;
        if (!(event.getPlayer() instanceof Player player) || !canAdmin(player, "shocksmp.events.reward")) return;
        ItemStack item = event.getInventory().getItem(4);
        ItemStack prize = item == null || item.getType().isAir() ? null : item.clone();
        rewards.set("prizes." + holder.event, prize);
        try { rewards.save(rewardsFile); player.sendMessage(Component.text("Saved " + title(holder.event) + " prize.", NamedTextColor.GREEN)); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save event prize: " + ex.getMessage()); }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!pendingDurations.containsKey(id)) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> handleDuration(event.getPlayer(), input));
    }

    private void handleDuration(Player admin, String input) {
        Pending pending = pendingDurations.get(admin.getUniqueId());
        if (pending == null) return;
        if (System.currentTimeMillis() > pending.expiresAt()) { pendingDurations.remove(admin.getUniqueId()); reset(); admin.sendMessage("Egg Hunt setup expired."); return; }
        if (input.equalsIgnoreCase("cancel")) { pendingDurations.remove(admin.getUniqueId()); reset(); admin.sendMessage("Egg Hunt setup cancelled."); return; }
        Duration duration;
        try { duration = EventRules.duration(input); }
        catch (IllegalArgumentException ex) { admin.sendMessage(Component.text(ex.getMessage() + " Type cancel to stop.", NamedTextColor.RED)); return; }
        pendingDurations.remove(admin.getUniqueId());
        eggs.prepare(admin, duration, () -> { state = State.RUNNING; saveState(); }, this::completeGameplay);
    }

    private void expirePrompts() {
        for (Map.Entry<UUID, Pending> entry : new ArrayList<>(pendingDurations.entrySet())) {
            if (System.currentTimeMillis() <= entry.getValue().expiresAt()) continue;
            pendingDurations.remove(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.sendMessage(Component.text("Egg Hunt setup timed out.", NamedTextColor.RED));
            reset();
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (pendingDurations.remove(event.getPlayer().getUniqueId()) != null) reset();
    }

    @EventHandler public void onAdminJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (state == State.WON && canAdmin(event.getPlayer(), ADMIN))
            event.getPlayer().sendMessage(Component.text(
                    "The event has been won. Mythic pickup limits are still active. Run /event end when loot distribution is finished.",
                    NamedTextColor.GOLD));
    }

    private ItemStack icon(String id) {
        return switch (id) {
            case "pvp" -> new ItemStack(Material.GOLDEN_SWORD);
            case "egg" -> new ItemStack(Material.DRAGON_EGG);
            case "capture" -> new ItemStack(Material.WHITE_BANNER);
            case "crown" -> crownIcon();
            case "mace" -> displayOnly(mace.create());
            case "assassins" -> new ItemStack(Material.IRON_SWORD);
            case "blade" -> displayOnly(warlord.blade());
            case "armor", "warlord" -> displayOnly(relics.create("helmet"));
            default -> new ItemStack(Material.BARRIER);
        };
    }

    private ItemStack crownIcon() {
        try {
            Plugin crown = Bukkit.getPluginManager().getPlugin("KingsCrown");
            if (crown != null && crown.isEnabled()) {
                Object manager = crown.getClass().getMethod("b").invoke(crown);
                Object created = manager.getClass().getMethod("c").invoke(manager);
                if (created instanceof ItemStack item) return displayOnly(item);
            }
        } catch (ReflectiveOperationException ex) { plugin.getLogger().warning("Crown menu icon fallback: " + ex.getMessage()); }
        ItemStack fallback = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = fallback.getItemMeta(); meta.setItemModel(NamespacedKey.fromString("kingscrown:crown")); fallback.setItemMeta(meta);
        return fallback;
    }

    private ItemStack displayOnly(ItemStack source) {
        ItemStack copy = source.clone();
        ItemMeta meta = copy.getItemMeta();
        for (String key : List.of("kingscrown:crownsmp_crown", "shocksmppowersystem:shock_type",
                "shocksmppowersystem:shock_instance", "shocksmp:warlord_piece", "shocksmp:warlord_blade",
                "shocksmp:shock_mace", "shocksmp:shock_mace_instance")) {
            NamespacedKey namespaced = NamespacedKey.fromString(key);
            if (namespaced != null) meta.getPersistentDataContainer().remove(namespaced);
        }
        copy.setItemMeta(meta);
        return copy;
    }

    private void fill(Inventory gui) {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta(); meta.displayName(Component.text(" ")); pane.setItemMeta(meta);
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, pane);
    }
    private ItemStack button(Material material, String name, String detail) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(detail, NamedTextColor.GRAY)));
        item.setItemMeta(meta); return item;
    }
    private String status(String id) { if (id.equals("pvp")) return pvpHour.status(); return id.equals(active) ? state.name().toLowerCase() : "idle"; }
    private boolean known(String id) { return java.util.Arrays.asList(IDS).contains(id); }
    private String title(String id) { for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return TITLES[i]; return id; }
    private String description(String id) {
        return switch (id) {
            case "pvp" -> "Manually enable the locator bar for up to one hour";
            case "egg" -> "Explore to find outdoor eggs for points";
            case "capture" -> "Awaiting gameplay setup";
            case "crown" -> "Fight the equipped Juggernaut for the crown";
            case "mace" -> "Defeat the Juggernaut and claim its fallen mace";
            case "assassins" -> "Enroll, hunt assigned targets, survive";
            case "blade", "armor", "warlord" -> "Defeat the Warlord, then hunt seven scattered relics";
            default -> "";
        };
    }
    private void deny(Player player) { player.sendMessage(Component.text("No event permission.", NamedTextColor.RED)); }
    private boolean canAdmin(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) return sender.hasPermission(permission);
        return player.hasPermission(permission)
                || roles.effectiveRoleOf(player.getUniqueId()).weight() >= Role.ADMIN.weight();
    }

    private void juggernautWon(UUID defeated) {
        if (state != State.RUNNING) return;
        String name = Bukkit.getOfflinePlayer(defeated).getName();
        Bukkit.broadcast(Component.text("The " + (kits.wasWarlord() ? "Warlord" : "Juggernaut")
                + " has fallen! " + (name == null ? defeated : name) + " was defeated.", NamedTextColor.GOLD));
        completeGameplay();
    }

    /** Completion hook for current events and the future Capture Points implementation. */
    public void completeGameplay() {
        if (state == State.IDLE || state == State.ENDING || state == State.WON) return;
        state = State.WON;
        saveState();
        String notice = "warlord".equals(active) ? "The Warlord hunt is starting. Use /warlordevent to reveal relic locations. /event end clears the event without deleting hunt chests."
                : "The event has been won. Mythic pickup limits are still active. Run /event end when loot distribution is finished.";
        for (Player player : Bukkit.getOnlinePlayers()) if (canAdmin(player, ADMIN))
            player.sendMessage(Component.text(notice, NamedTextColor.GOLD));
        plugin.getLogger().info(notice);
    }

    private void recoverState() {
        active = stateData.getString("active");
        if ("hunter".equals(active) || "time".equals(active)) {
            stateData.set("active", null); stateData.set("state", "IDLE"); active = null;
        }
        runId = parseUuid(stateData.getString("run-id"));
        try { state = State.valueOf(stateData.getString("state", "IDLE")); }
        catch (IllegalArgumentException ignored) { state = State.IDLE; }
        if (kits.lootRestricted() && kits.eventId() != null) {
            if (active == null) active = "crown";
            runId = kits.eventId(); state = State.WON;
        } else if (state == State.IDLE && kits.hasDesignation() && kits.eventId() != null) {
            active = "crown"; runId = kits.eventId(); state = State.RUNNING;
        } else if (state == State.PREPARING || state == State.ENDING
                || state == State.RUNNING && ("egg".equals(active) || "assassins".equals(active))) {
            state = State.WON;
        }
        if (state == State.WON) plugin.getLogger().warning(
                "Recovered an interrupted or won event. Admins should review loot, then run /event end.");
        saveState();
    }

    private UUID parseUuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private void saveState() {
        stateData.set("active", active);
        stateData.set("run-id", runId == null ? null : runId.toString());
        stateData.set("state", state.name());
        try { stateData.save(stateFile); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save event state: " + ex.getMessage()); }
    }

    private void reset() {
        active = null; state = State.IDLE; runId = null; manualRewarded = false;
        saveState();
    }

    public void shutdown() {
        netheriteArmor.close();
        purge.close();
        competition.close();
        maceRewards.close();
        pvpHour.shutdown();
        pendingDurations.clear();
        if (eggs.preparingOrRunning()) eggs.stop(false);
        if (assassins.active()) assassins.stop(false);
        warlord.shutdown();
        if (state == State.PREPARING) completeGameplay();
        saveState();
    }

}
