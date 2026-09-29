package dev.turtleroles.service;

import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import java.util.*;

/** Public descriptions of custom modules, not a list of separate installed JARs. */
public final class ConquestPluginList implements Listener {
    public record Module(String id,String name,String description) {}
    public static final List<Module> MODULES=List.of(
        new Module("core","Conquest SMP Core","The central Conquest SMP system connecting custom gameplay, player data and server features."),
        new Module("integrations","Conquest SMP Integrations","Connects Conquest features with supported server plugins. Author Ari refers to the custom integration, not third-party software."),
        new Module("security","Conquest SMP Security Engine","Conquest AC combines GrimAC flags, alt-account signals and client-policy reports for staff review. Signals are not proof of cheating."),
        new Module("client","Conquest SMP Client Intelligence","Displays reported client context and suspicious mod signals, including existing ModDetector records, without adding mod-based join blocks."),
        new Module("alts","Conquest SMP Identity Insights","Presents possible alternate-account links from AltDetector for staff investigation."),
        new Module("crown","Conquest SMP Crown Integration","Connects the King's Crown event with Conquest reward protection and gameplay rules. King's Crown remains a separate integration."),
        new Module("events","Conquest SMP Event Director","Coordinates custom events, event scoreboards, announcements and rewards."),
        new Module("warlord","Conquest SMP Warlord's Purge","Warlord combat, floating gear and shooting-star finale, followed by separate reward hunts with manual coordinate reveals."),
        new Module("juggernaut","Conquest SMP Juggernaut","Juggernaut battles with damage rankings and an animated enchanted mace reward."),
        new Module("loot","Conquest SMP Supply Drops","Randomized public loot drops, manual launches and the optional four-hour schedule in /util."),
        new Module("rewards","Conquest SMP Mythic Relics","Custom event-item identities, container restrictions, rename protection and destruction rules."),
        new Module("combat","Conquest SMP Combat Guard","Combat tags, boss-bar timers, movement restrictions, mutual public-chat truces and Happy Ghast combat rules."),
        new Module("balance","Conquest SMP Combat Balance","Weapon damage caps, explosive damage rules, totem limits and enchanted golden apple cooldowns."),
        new Module("races","Conquest SMP Origins","Biome races, race abilities, first-join rolls and random shard-funded rerolls."),
        new Module("shards","Conquest SMP Shard Economy","Persistent shards earned through AFK time and eligible kills, with repeat-kill farming limits."),
        new Module("afk","Conquest SMP AFK Rewards","Selected AFK zones, rank-based earning intervals and on-screen session progress."),
        new Module("npcs","Conquest SMP Citizens","Custom villager service NPCs, animated orbiting items, Discord links and race guidance."),
        new Module("shop","Conquest SMP Utilities Market","An editable shard shop with custom stock, pricing and purple presentation."),
        new Module("spawners","Conquest SMP Virtual Spawners","Stackable purchased spawners that generate stored drops and experience without spawning live mobs."),
        new Module("roles","Conquest SMP Rank Authority","Custom staff and supporter ranks, chat badges, rank benefits and protected ownership controls."),
        new Module("kits","Conquest SMP Supporter Kits","Editable Booster and Booster X2 kits, inventory-space checks and persistent claim timers."),
        new Module("nick","Conquest SMP Persona","Rank-based nicknames and cosmetic skins, collision resets and staff identity checks while preserving player data."),
        new Module("homes","Conquest SMP Waypoints","Rank-based home limits, saved home locations and spawn teleportation."),
        new Module("rtp","Conquest SMP Wilderness Travel","Safe random teleportation inside the Terralith overworld border."),
        new Module("spawn","Conquest SMP Spawn Sentinel","Selected spawn protection, interaction restrictions, combat-entry barriers and collision rules."),
        new Module("launch","Conquest SMP Launch Control","SMP launch announcements, kit-unlock timing and temporary spawn-border restoration."),
        new Module("dimensions","Conquest SMP Dimension Gates","Nether and End access controls, evacuation to spawn and title announcements."),
        new Module("dragon","Conquest SMP Dragon Encounter","Enhanced Ender Dragon health, presentation and combat restrictions."),
        new Module("death","Conquest SMP Death Recovery","Public death chests preserve dropped inventories until emptied or timed out."),
        new Module("ender","Conquest SMP Ender Vaults","Personal 54-slot ender chests with combat access and event-item restrictions."),
        new Module("leaderboards","Conquest SMP Hall of Champions","Persistent floating rankings for kills, deaths, streaks and playtime, with personal statistics."),
        new Module("display","Conquest SMP Visual Identity","Custom resource-pack badges, tab presentation, sidebar statistics and server-list branding."),
        new Module("sky","Conquest SMP Sky Typography","Editable floating small-caps text with custom colors, placement and size."),
        new Module("string","Conquest SMP String Supply","Rank-based /string refill cooldowns."),
        new Module("bedrock","Conquest SMP Crossplay Bridge","Conquest support and update management for Geyser and Floodgate. Those projects retain their original authors."),
        new Module("util","Conquest SMP Administration Suite","The /util control center for Conquest's custom administration tools."),
        new Module("moderation","Conquest SMP Moderation Desk","Recorded warnings, mutes, bans, kicks and punishment history."),
        new Module("players","Conquest SMP Player Operations","Online-player management, inspection and staff freeze tools."),
        new Module("reports","Conquest SMP Report Center","Receive, review and resolve player reports."),
        new Module("rollback","Conquest SMP Inventory Recovery","Review and restore saved death-inventory snapshots through /util."),
        new Module("announce","Conquest SMP Broadcast Studio","Send server announcements and title messages."),
        new Module("grace","Conquest SMP Grace Manager","Control temporary PvP grace periods and their countdowns."),
        new Module("vanish","Conquest SMP Staff Veil","Staff vanish controls and visibility management."),
        new Module("staffmode","Conquest SMP Staff Workspace","Staff-mode tools for moderation, inspection and navigation."),
        new Module("keyall","Conquest SMP Reward Dispatch","Distribute copies of deposited reward items through Key All."),
        new Module("lockdown","Conquest SMP Access Control","Manage server lockdown and its displayed reason."),
        new Module("chat","Conquest SMP Chat Control","Chat clearing, locking, slow mode, filtering and private staff chat."),
        new Module("restart","Conquest SMP Restart Planner","Manage announced server-restart countdowns."),
        new Module("performance","Conquest SMP Performance Monitor","View cached server metrics and authorized cleanup tools."),
        new Module("audit","Conquest SMP Audit Trail","Review and export recorded administrative actions through /util."),
        new Module("stafflogs","Conquest SMP Staff Activity","Browse staff heads, ranks and moderation-action counts across all time, seven days or today.")
    );
    private static final TextColor PURPLE=TextColor.color(0xB477FF),LIGHT=TextColor.color(0xD7BBF5);
    private static final int PAGE_SIZE=8;
    public static boolean matches(String input) {
        String root=input.strip().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        while(root.startsWith("/"))root=root.substring(1);
        root=root.substring(root.lastIndexOf(':')+1);
        return root.equals("plugins")||root.equals("pl");
    }
    public static Component link(Module module) {
        return Component.text(module.name(),LIGHT).clickEvent(ClickEvent.runCommand("/plugins info "+module.id()))
            .hoverEvent(HoverEvent.showText(Component.text(module.description()+"\nAuthor: Ari\nClick for details",LIGHT)));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void command(PlayerCommandPreprocessEvent event) {
        if(!matches(event.getMessage()))return;
        event.setCancelled(true);Player player=event.getPlayer();String[] args=event.getMessage().strip().split("\\s+");
        if(args.length>=3&&args[1].equalsIgnoreCase("info")) {
            Module module=MODULES.stream().filter(m->m.id().equalsIgnoreCase(args[2])).findFirst().orElse(null);
            if(module!=null){
                player.sendMessage(Component.text(module.name(),PURPLE));
                player.sendMessage(Component.text(module.description(),LIGHT));
                player.sendMessage(Component.text("Author: Ari | Custom Conquest module",LIGHT));
                int page=MODULES.indexOf(module)/PAGE_SIZE+1;
                player.sendMessage(Component.text("[Back to systems]",PURPLE).clickEvent(ClickEvent.runCommand("/plugins "+page)));return;
            }
        }
        int pages=(MODULES.size()+PAGE_SIZE-1)/PAGE_SIZE,page=1;
        if(args.length==2)try{page=Integer.parseInt(args[1]);}catch(NumberFormatException ignored){}
        page=Math.max(1,Math.min(pages,page));
        player.sendMessage(Component.text("Conquest SMP Systems | "+page+"/"+pages,PURPLE));
        player.sendMessage(Component.text("Custom features and integrations. Click a name for details.",LIGHT));
        for(int i=(page-1)*PAGE_SIZE;i<Math.min(page*PAGE_SIZE,MODULES.size());i++)player.sendMessage(Component.text("  ").append(link(MODULES.get(i))));
        Component navigation=Component.empty();
        if(page>1)navigation=navigation.append(Component.text("[Previous] ",PURPLE).clickEvent(ClickEvent.runCommand("/plugins "+(page-1))));
        if(page<pages)navigation=navigation.append(Component.text("[Next]",PURPLE).clickEvent(ClickEvent.runCommand("/plugins "+(page+1))));
        player.sendMessage(navigation);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void complete(TabCompleteEvent event) {
        if(event.getSender() instanceof Player && matches(event.getBuffer()))event.setCompletions(List.of());
    }
}
