package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import java.io.*;
import java.util.*;

/** Scores only permitted damage and temporarily occupies each player's sidebar. */
final class JuggernautCompetition implements Listener,AutoCloseable {
    private record Board(Objective objective,Objective previous,List<Team> rows) {}
    private final JavaPlugin plugin;
    private final JuggernautService kits;
    private final JuggernautMaces maces;
    private final DamageRanking ranking=new DamageRanking();
    private final Map<Scoreboard,Board> boards=new IdentityHashMap<>();
    private final File file;
    private UUID run;
    private boolean dirty;
    private int ticks;
    private final BukkitTask task;
    JuggernautCompetition(JavaPlugin plugin,JuggernautService kits,JuggernautMaces maces) {
        this.plugin=plugin;this.kits=kits;this.maces=maces;
        file=new File(plugin.getDataFolder(),"juggernaut-damage.yml");
        var data=YamlConfiguration.loadConfiguration(file);
        try {run=UUID.fromString(data.getString("event",""));}catch(IllegalArgumentException ignored){}
        for(Map<?,?> row:data.getMapList("scores"))try {
            ranking.restore(UUID.fromString(row.get("id").toString()),row.get("name").toString(),((Number)row.get("damage")).doubleValue());
        }catch(RuntimeException ignored){}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,20);
    }
    private void ensureRun() {
        if(!Objects.equals(run,kits.eventId())){run=kits.eventId();ranking.clear();dirty=true;save();}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof Player target)||!kits.isActive(target)||event.getFinalDamage()<=0)return;
        Entity cause=event.getDamageSource().getCausingEntity();
        if(!(cause instanceof Player) && event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player owner)cause=owner;
        if(!(cause instanceof Player attacker) && event.getDamager() instanceof Player)cause=event.getDamager();
        if(!(cause instanceof Player attacker)||attacker.getUniqueId().equals(target.getUniqueId()))return;
        ensureRun();ranking.add(attacker.getUniqueId(),attacker.getName(),event.getFinalDamage(),target.getHealth());dirty=true;
    }
    void defeated(Location location) {
        ensureRun();
        maces.dropAnimated(run,location);
        save();clearBoards();
    }
    private void tick() {
        if(kits.hasDesignation()) {
            ensureRun();
            Set<Scoreboard> used=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Player player:Bukkit.getOnlinePlayers()) {
                if(!dev.turtleroles.service.ClientCompatibility.authenticated(player))continue;
                Scoreboard board=player.getScoreboard();if(!used.add(board))continue;
                Board view=boards.computeIfAbsent(board,this::create);
                List<DamageRanking.Entry> leaders=ranking.top(10);
                for(int i=0;i<11;i++) {
                    Component text=i==0?Component.text("TOP 10 DAMAGE",TextColor.color(0xE1CBFF)).decorate(TextDecoration.BOLD)
                        : i<=leaders.size()?Component.text(i+". "+leaders.get(i-1).name()+"  ",TextColor.color(0xE1CBFF))
                            .append(Component.text(String.format(Locale.ROOT,"%.1f",leaders.get(i-1).damage()),NamedTextColor.WHITE))
                        : Component.text(i+". ---",NamedTextColor.DARK_GRAY);
                    view.rows().get(i).prefix(text);
                }
                view.objective().setDisplaySlot(DisplaySlot.SIDEBAR);
            }
        }else clearBoards();
        if(++ticks%5==0&&dirty)save();
    }
    private Board create(Scoreboard board) {
        Objective previous=board.getObjective(DisplaySlot.SIDEBAR);
        String name="cq_juggernaut";int suffix=0;while(board.getObjective(name)!=null)name="cq_jugg"+(++suffix);
        Objective objective=board.registerNewObjective(name,Criteria.DUMMY,Component.text(kits.wasWarlord()?"WARLORD'S PURGE":"JUGGERNAUT",TextColor.color(0xB78AFF)).decorate(TextDecoration.BOLD));
        objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        List<Team> rows=new ArrayList<>();
        for(int i=0;i<11;i++) {
            String teamName=name+"_"+i;
            Team team=board.registerNewTeam(teamName);
            String entry="§"+Integer.toHexString(i)+"§0§r";
            team.addEntry(entry);objective.getScore(entry).setScore(11-i);rows.add(team);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);return new Board(objective,previous,rows);
    }
    void clearBoards() {
        for(var entry:boards.entrySet()) {
            Board view=entry.getValue();
            if(view.objective().getScoreboard()==null)continue;
            boolean current=entry.getKey().getObjective(DisplaySlot.SIDEBAR)==view.objective();
            view.objective().unregister();view.rows().forEach(Team::unregister);
            if(current&&view.previous()!=null&&view.previous().getScoreboard()!=null)view.previous().setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        boards.clear();
    }
    @EventHandler public void quit(PlayerQuitEvent event){save();}
    private void save() {
        var data=new YamlConfiguration();data.set("event",run==null?null:run.toString());
        data.set("scores",ranking.all().stream().map(e->Map.of("id",e.player().toString(),"name",e.name(),"damage",e.damage())).toList());
        try{EventFiles.save(file,data);dirty=false;}catch(IOException e){plugin.getLogger().severe("Cannot save Juggernaut damage: "+e.getMessage());}
    }
    public void close(){task.cancel();clearBoards();save();HandlerList.unregisterAll(this);}
}
