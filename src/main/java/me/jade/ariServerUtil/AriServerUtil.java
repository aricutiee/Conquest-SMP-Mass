package me.jade.ariServerUtil;

import me.jade.ariServerUtil.announcements.AnnouncementService;
import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.chat.ChatControlService;
import me.jade.ariServerUtil.chatinput.ChatInputService;
import me.jade.ariServerUtil.commands.*;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.freeze.FreezeService;
import me.jade.ariServerUtil.grace.GracePeriodService;
import me.jade.ariServerUtil.gui.GuiController;
import me.jade.ariServerUtil.gui.GuiManager;
import me.jade.ariServerUtil.lockdown.LockdownService;
import me.jade.ariServerUtil.performance.PerformanceService;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.player.IdentityService;
import me.jade.ariServerUtil.punishments.PunishmentService;
import me.jade.ariServerUtil.reports.ReportService;
import me.jade.ariServerUtil.restart.RestartService;
import me.jade.ariServerUtil.rollback.RollbackService;
import me.jade.ariServerUtil.staffmode.StaffModeService;
import me.jade.ariServerUtil.vanish.VanishService;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class AriServerUtil extends JavaPlugin {
    private ServerUtilConfig configs;
    private Database database;
    private AuditService audit;
    private ChatInputService chatInput;
    private IdentityService identities;
    private PunishmentService punishments;
    private ReportService reports;
    private FreezeService freeze;
    private RollbackService rollbacks;
    private AnnouncementService announcements;
    private GracePeriodService grace;
    private VanishService vanish;
    private StaffModeService staffMode;
    private LockdownService lockdown;
    private ChatControlService chatControls;
    private RestartService restart;
    private PerformanceService performance;
    private GuiManager gui;
    private GuiController screens;

    @Override
    public void onEnable() {
        configs = new ServerUtilConfig(this);
        configs.load();
        database = new Database(this, configs.dataFolder());
        try {
            database.open();
        } catch (SQLException ex) {
            getLogger().severe("Could not open SQLite database: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        buildServices();
        registerCommands();
    }

    @Override
    public void onDisable() {
        if (chatInput != null) {
            chatInput.invalidateAll();
        }
        if (grace != null) grace.close();
        if (vanish != null) vanish.close();
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        if (database != null) {
            database.close();
        }
    }

    public void reloadServerUtil() {
        configs.load();
        chatInput.invalidateAll();
        chatControls.reloadState();
    }

    private void buildServices() {
        audit = new AuditService(database);
        chatInput = new ChatInputService(this, configs);
        identities = new IdentityService(database);
        punishments = new PunishmentService(this, configs, database, audit);
        reports = new ReportService(configs, database, audit);
        freeze = new FreezeService(this, configs, audit);
        announcements = new AnnouncementService(configs, audit);
        grace = new GracePeriodService(this, configs, database, audit, announcements);
        vanish = new VanishService(this, configs, database, audit);
        staffMode = new StaffModeService(this, configs, database, audit, vanish);
        rollbacks = new RollbackService(this, configs, database, audit);
        lockdown = new LockdownService(configs, database, audit);
        chatControls = new ChatControlService(this, configs, database, audit, chatInput);
        restart = new RestartService(this, configs, database, audit);
        performance = new PerformanceService(this, configs, audit);
        gui = new GuiManager(this, configs);
        screens = new GuiController(this, configs, database, gui, chatInput, identities, punishments, reports, freeze, rollbacks, announcements, grace, vanish, staffMode, lockdown, chatControls, restart, performance, audit);
        staffMode.setToolHandler(screens::openStaffTool);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(identities, this);
        getServer().getPluginManager().registerEvents(punishments, this);
        getServer().getPluginManager().registerEvents(freeze, this);
        getServer().getPluginManager().registerEvents(grace, this);
        getServer().getPluginManager().registerEvents(vanish, this);
        getServer().getPluginManager().registerEvents(staffMode, this);
        getServer().getPluginManager().registerEvents(rollbacks, this);
        getServer().getPluginManager().registerEvents(lockdown, this);
        getServer().getPluginManager().registerEvents(chatControls, this);
    }

    private void registerCommands() {
        getCommand("util").setExecutor(new UtilCommand(this));
        getCommand("util").setTabCompleter(new UtilCommand(this));
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("reports").setExecutor(new ReportsCommand(this));
        getCommand("staffmode").setExecutor(new StaffModeCommand(this));
        StaffChatCommand staffChat = new StaffChatCommand(this);
        getCommand("staffchat").setExecutor(staffChat);
        getCommand("sc").setExecutor(staffChat);
    }

    public ServerUtilConfig configs() {
        return configs;
    }

    public Database database() {
        return database;
    }

    public GuiController screens() {
        return screens;
    }

    public IdentityService identities() {
        return identities;
    }

    public ReportService reports() {
        return reports;
    }

    public StaffModeService staffMode() {
        return staffMode;
    }

    public ChatControlService chatControls() {
        return chatControls;
    }
}
