package dev.turtleroles;

import dev.turtleroles.command.GameModeCommand;
import dev.turtleroles.events.ActionBarBus;
import dev.turtleroles.events.EventSystem;
import dev.turtleroles.command.ModerationCommand;
import dev.turtleroles.command.RoleCommand;
import dev.turtleroles.command.TurtleCommand;
import dev.turtleroles.config.TurtleConfig;
import dev.turtleroles.gui.RoleMenuService;
import dev.turtleroles.listener.ChatAndCommandListener;
import dev.turtleroles.listener.PlayerLifecycleListener;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.service.InventoryInspectService;
import dev.turtleroles.service.PresentationService;
import dev.turtleroles.service.PunishmentService;
import dev.turtleroles.service.ResourcePackService;
import dev.turtleroles.service.ResourcePackHttpServer;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.service.TabListService;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.storage.PunishmentRepository;
import dev.turtleroles.storage.SQLiteDatabase;
import org.bukkit.command.PluginCommand;
import me.jade.ariServerUtil.AriServerUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.Objects;
import java.util.logging.Level;

public final class TurtleRolesPlugin extends AriServerUtil {
    private dev.biomeraces.RaceModule races;
    private dev.turtleroles.survival.ConquestDragon dragons;
    private dev.turtleroles.survival.Leaderboards leaderboards;
    private dev.turtleroles.combat.ConquestCombat combat;
    private dev.turtleroles.survival.SurvivalModule survival;
    private dev.turtleroles.events.LootDrops lootDrops;
    public dev.turtleroles.events.LootDrops lootDrops() { return lootDrops; }
    private dev.turtleroles.survival.DeathChests deathChests;
    private dev.turtleroles.service.BedrockUpdater bedrockUpdater;
    private dev.turtleroles.survival.ExpandedEnderChests enderChests;
    public dev.turtleroles.combat.ConquestCombat combat() { return combat; }
    public boolean smpStarted(){return boosterKits!=null&&boosterKits.started();}
    public dev.turtleroles.survival.SurvivalModule survival() { return survival; }
    private dev.turtleroles.anticheat.ConquestGrimIntegration anticheat;
    private dev.turtleroles.service.BoosterKits boosterKits;
    private SQLiteDatabase database;
    private RoleService roleService;
    public RoleService roleService() { return roleService; }
    private InventoryInspectService invsee;
    private ResourcePackHttpServer packHttpServer;
    private ResourcePackService packs;
    private PresentationService presentation;
    public boolean hasLoadedResourcePack(Player player) {
        return presentation != null && !dev.turtleroles.service.ClientCompatibility.bedrock(player)
                && presentation.canUseGlyphFor(player);
    }
    private TabListService tabList;
    private EventSystem eventSystem;
    private ActionBarBus actionBars;

    @Override
    public void onEnable() {
        try {
            migrateLegacyRoleData();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not copy existing TurtleRoles data into the ConquestSMP folder.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("ServerUtil") != null) {
            getLogger().severe("Remove the separate ServerUtil JAR before using Conquest SMP. Keep both data folders.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("ShockSmpPowerSystem") != null) {
            getLogger().severe("Remove the separate ShockSmpPowerSystem JAR before using this combined plugin. Keep its data folder.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("shockSMP") != null) {
            getLogger().severe("Disable the old shockSMP JAR before enabling ConquestSMP.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("BiomeRaces") != null) {
            getLogger().severe("Remove the standalone BiomeRaces JAR: races are now included in ConquestSMP. Keep its data folder.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        actionBars = new ActionBarBus(this);
        super.onEnable();
        if (!isEnabled()) {
            return;
        }
        saveDefaultConfig();
        getServer().setMaxPlayers(Math.clamp(getConfig().getInt("server.player-limit",499),1,10000));
        updateHostedPack();
        if (!new java.io.File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        if (!new java.io.File(getDataFolder(), "roles.yml").exists()) saveResource("roles.yml", false);

        TurtleConfig config = TurtleConfig.from(getConfig());
        try {
            database = new SQLiteDatabase(Path.of(getDataFolder().getAbsolutePath(), "turtleroles.db"));
            database.open();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "TurtleRoles storage failed to open; disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerRepository players = new PlayerRepository(database);
        PunishmentRepository punishmentRepository = new PunishmentRepository(database);
        PolicyService policy = new PolicyService(config.helperMuteLimit(), config.moderatorMuteLimit(), config.moderatorBanLimit());
        RoleService roles = new RoleService(this, players);
        roleService = roles;
        getServer().getPluginManager().registerEvents(new dev.turtleroles.service.AlertPing(this), this);
        getServer().getPluginManager().registerEvents(new dev.turtleroles.service.CustomItemNames(this), this);
        getServer().getPluginManager().registerEvents(new dev.turtleroles.service.ConquestMotd(this), this);
        getServer().getPluginManager().registerEvents(new dev.turtleroles.service.StaffCommandGuard(this, roles), this);
        try { boosterKits = new dev.turtleroles.service.BoosterKits(this); }
        catch (Exception ex) { getLogger().log(Level.SEVERE, "Booster kit storage failed", ex); getServer().getPluginManager().disablePlugin(this); return; }
        PunishmentService punishments = new PunishmentService(policy, roles, players, punishmentRepository);
        presentation = new PresentationService(roles);
        tabList = new TabListService(this, presentation);
        if (config.resourcePack().selfHostEnabled()) {
            try {
                packHttpServer = new ResourcePackHttpServer(this, config.resourcePack().selfHostPort());
                packHttpServer.start();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Could not start the TurtleRoles resource-pack web server.", e);
            }
        }
        packs = new ResourcePackService(this, presentation, config.resourcePack());
        RoleMenuService menus = new RoleMenuService(this, roles, players, policy, presentation);
        invsee = new InventoryInspectService(this, roles, policy, config.invseeRefreshTicks());
        invsee.start();
        tabList.start();
        eventSystem = new EventSystem(this, roles);
        new dev.turtleroles.migration.RetiredShockCleanup(this).start();

        RoleCommand roleCommand = new RoleCommand(roles, players, policy, menus, presentation, config);
        setExecutor("role", roleCommand);
        setTabCompleter("role", roleCommand);
        setExecutor("warn", new ModerationCommand("warn", roles, players, punishments, config));
        setExecutor("tempmute", new ModerationCommand("tempmute", roles, players, punishments, config));
        setExecutor("mute", new ModerationCommand("mute", roles, players, punishments, config));
        setExecutor("tempban", new ModerationCommand("tempban", roles, players, punishments, config));
        setExecutor("ban", new ModerationCommand("ban", roles, players, punishments, config));
        setExecutor("kick", new ModerationCommand("kick", roles, players, punishments, config));
        setExecutor("unwarn", new ModerationCommand("unwarn", roles, players, punishments, config));
        setExecutor("unmute", new ModerationCommand("unmute", roles, players, punishments, config));
        setExecutor("unban", new ModerationCommand("unban", roles, players, punishments, config));
        setExecutor("warnings", new ModerationCommand("warnings", roles, players, punishments, config));
        setExecutor("history", new ModerationCommand("history", roles, players, punishments, config));
        setExecutor("case", new ModerationCommand("case", roles, players, punishments, config));
        setExecutor("gamemode", new GameModeCommand(roles, players, policy));
        TurtleCommand utilities = new TurtleCommand(this, roles, players, policy, invsee, presentation, packs, config);
        setExecutor("tr", utilities);
        setExecutor("invsee", (sender, command, label, args) -> {
            String[] forwarded = new String[args.length + 1];
            forwarded[0] = "invsee";
            System.arraycopy(args, 0, forwarded, 1, args.length);
            return utilities.onCommand(sender, command, label, forwarded);
        });

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(invsee, this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, roles, players, punishments, packs), this);
        getServer().getPluginManager().registerEvents(new ChatAndCommandListener(this, players, punishments, presentation, config), this);

        combat = new dev.turtleroles.combat.ConquestCombat(this);
        combat.start();
        survival = new dev.turtleroles.survival.SurvivalModule(this);
        survival.start();
        dragons=new dev.turtleroles.survival.ConquestDragon(this);
        dragons.start();
        leaderboards=new dev.turtleroles.survival.Leaderboards(this);
        leaderboards.start();
        lootDrops = new dev.turtleroles.events.LootDrops(this);
        lootDrops.start();
        deathChests = new dev.turtleroles.survival.DeathChests(this);
        deathChests.start();
        enderChests = new dev.turtleroles.survival.ExpandedEnderChests(this);
        enderChests.start();
        bedrockUpdater = new dev.turtleroles.service.BedrockUpdater(this);
        bedrockUpdater.start();
        races = new dev.biomeraces.RaceModule(this);
        if (!races.enable()) return;
        anticheat = new dev.turtleroles.anticheat.ConquestGrimIntegration(this);
        anticheat.start();
        getServer().getPluginManager().registerEvents(new dev.turtleroles.combat.WeaponDamageCaps(this),this);
        roles.reconcileOnlineOps();
        getServer().getOnlinePlayers().forEach(packs::sendOnJoin);
        getLogger().info("Conquest SMP enabled: roles, badges, moderation and ServerUtil administration are running in one plugin.");
        if (config.resourcePack().url().isBlank() || config.resourcePack().sha1().isBlank()) {
            getLogger().warning("Resource-pack URL/SHA-1 is not configured. Automatic delivery is enabled, but players will see colored text fallback until the generated ZIP is hosted and config.yml is updated.");
        }
    }

    private void migrateLegacyRoleData() throws IOException {
        dev.turtleroles.migration.LegacyDataMigration.copy(getDataFolder().toPath());
    }

    private void updateHostedPack() {
        if (!getConfig().getBoolean("resource-pack.self-host.enabled", false)) return;
        try (var input = getResource("generated-resource-pack/ConquestSMP-resource-pack.zip")) {
            if (input == null) throw new IOException("Missing embedded resource pack");
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(input.readAllBytes()));
            String url = getConfig().getString("resource-pack.url", "");
            if (!url.isBlank()) {
                String base = url.split("\\?", 2)[0];
                getConfig().set("resource-pack.url", base + "?v=" + hash);
                getConfig().set("resource-pack.sha1", hash);
                getConfig().set("resource-pack.prompt", "Conquest SMP tab logo, role badges and the 3D King's Crown.");
                saveConfig();
            }
        } catch (IOException | java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not configure the hosted pack", ex);
        }
    }

    @Override
    public void onDisable() {
        if (boosterKits != null) boosterKits.close();
        if (packs != null) packs.close();
        if (combat != null) combat.close();
        if (leaderboards != null) leaderboards.close();
        if (dragons != null) dragons.close();
        if (lootDrops != null) lootDrops.close();
        if (survival != null) survival.close();
        if (deathChests != null) deathChests.close();
        if (enderChests != null) enderChests.close();
        if (bedrockUpdater != null) bedrockUpdater.close();
        if (anticheat != null) anticheat.close();
        if (races != null) races.close();
        if (roleService != null) roleService.closePermissions();
        if (eventSystem != null) eventSystem.shutdown();
        if (actionBars != null) actionBars.shutdown();
        super.onDisable();
        if (packHttpServer != null) {
            packHttpServer.stop();
        }
        if (invsee != null) {
            invsee.stop();
        }
        if (tabList != null) {
            tabList.stop();
        }
        if (database != null) {
            try {
                database.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed closing TurtleRoles database.", e);
            }
        }
    }

    private void setExecutor(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command " + name + " in plugin.yml");
        command.setExecutor(executor);
    }

    private void setTabCompleter(String name, org.bukkit.command.TabCompleter completer) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command " + name + " in plugin.yml");
        command.setTabCompleter(completer);
    }

}
