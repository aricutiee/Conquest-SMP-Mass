package dev.turtleroles.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.*;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Packet-only display profiles. They never become authenticated Bukkit players. */
public final class FakePlayers implements Listener,CommandExecutor,TabCompleter,AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final Path file;
    private final LinkedHashMap<String,Fake> players=new LinkedHashMap<>();
    private final Map<UUID,Set<Integer>> visible=new HashMap<>();
    private final Map<UUID,Long> lastRequest=new HashMap<>();

    private BukkitTask task;
    private int nextId=2_000_000_000,ticks;
    private boolean afk;
    private final List<String> names=new ArrayList<>();
    static final class Fake {
        final String name;final UUID uuid;final int entity;final int basePing;int ping;Location location;
        Fake(String name,UUID uuid,int entity,int ping){this.name=name;this.uuid=uuid;this.entity=entity;this.basePing=ping;this.ping=ping;}
        PlayerInfo info(){return new PlayerInfo(new UserProfile(uuid,name),true,ping,com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL,Component.text(name),null);}
    }
    public FakePlayers(TurtleRolesPlugin plugin){this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("fakeplayers.yml");}
    public static boolean owner(Role role){return role==Role.OWNER;}
    private boolean owner(CommandSender sender){return sender instanceof Player p&&owner(plugin.roleService().roleOf(p.getUniqueId()));}
    public void start(){
        var command=Objects.requireNonNull(plugin.getCommand("fakeplayers"));command.setExecutor(this);command.setTabCompleter(this);
        if(!Bukkit.getPluginManager().isPluginEnabled("packetevents")){plugin.getLogger().warning("Fake player displays require PacketEvents.");return;}
        YamlConfiguration data=YamlConfiguration.loadConfiguration(file.toFile());afk=data.getBoolean("afk");names.addAll(data.getStringList("names"));
        var section=data.getConfigurationSection("players");
        if(section!=null)for(String name:section.getKeys(false))try{
            if(!validName(name)||players.size()>=limit())continue;
            players.put(name.toLowerCase(Locale.ROOT),new Fake(name,UUID.fromString(section.getString(name+".uuid")),nextId--,Math.clamp(section.getInt(name+".ping",70),10,180)));
        }catch(IllegalArgumentException ignored){}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
        for(Player p:Bukkit.getOnlinePlayers()){removeCollision(p.getName());sendList(p,true);}
        if(afk)placeAfk();
    }
    private int limit(){return Math.clamp(plugin.getConfig().getInt("fakeplayers.max-count",500),1,10000);}
    static boolean validName(String name){return name!=null&&name.matches("[A-Za-z0-9_]{3,16}");}
    static int afkCount(int count){return Math.round(count/3f);}
    static int ping(Random random){return switch(random.nextInt(3)){case 0->10+random.nextInt(41);case 1->55+random.nextInt(51);default->130+random.nextInt(51);};}
    private String newName(){
        List<String> available=names.stream().filter(FakePlayers::validName).filter(n->!occupied(n)).toList();
        if(!available.isEmpty())return available.get(ThreadLocalRandom.current().nextInt(available.size()));
        String[] first={"Lunar","Pixel","Silent","Nova","Frost","Aero","Cinder","Cloud","Echo","Iron","Violet","River","Mint","Solar","Void","Swift"};
        String[] last={"Fox","Wolf","Knight","Bunny","Leaf","Rush","Sky","Stone","Blade","Dawn","Drift","Bloom","Craft","Storm","Ash","Reef"};
        var r=ThreadLocalRandom.current();for(int i=0;i<1000;i++){String candidate=first[r.nextInt(first.length)]+last[r.nextInt(last.length)]+r.nextInt(10000);if(!occupied(candidate))return candidate;}
        throw new IllegalStateException("Could not allocate a unique display name");
    }
    private boolean occupied(String name){return players.containsKey(name.toLowerCase(Locale.ROOT))||Bukkit.getPlayerExact(name)!=null;}
    private void save() throws java.io.IOException {
        YamlConfiguration data=new YamlConfiguration();data.set("afk",afk);data.set("names",names);
        for(Fake f:players.values()){data.set("players."+f.name+".uuid",f.uuid.toString());data.set("players."+f.name+".ping",f.basePing);}
        AtomicYaml.save(data,file);
    }
    private void send(Player p,com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet){PacketEvents.getAPI().getPlayerManager().sendPacket(p,packet);}
    private void sendList(Player p,boolean add){
        List<Fake> all=new ArrayList<>(players.values());
        for(int i=0;i<all.size();i+=80){List<PlayerInfo> info=all.subList(i,Math.min(i+80,all.size())).stream().map(Fake::info).toList();
            send(p,new WrapperPlayServerPlayerInfoUpdate(add?EnumSet.of(Action.ADD_PLAYER,Action.UPDATE_LISTED,Action.UPDATE_GAME_MODE,Action.UPDATE_LATENCY,Action.UPDATE_DISPLAY_NAME):EnumSet.of(Action.UPDATE_LATENCY),info));}
    }
    private void removeDisplays(){
        List<UUID> ids=players.values().stream().map(f->f.uuid).toList();
        for(Player p:Bukkit.getOnlinePlayers()){
            Set<Integer> entities=visible.get(p.getUniqueId());if(entities!=null&&!entities.isEmpty())send(p,new WrapperPlayServerDestroyEntities(entities.stream().mapToInt(Integer::intValue).toArray()));
            for(int i=0;i<ids.size();i+=80)send(p,new WrapperPlayServerPlayerInfoRemove(ids.subList(i,Math.min(i+80,ids.size()))));
        }visible.clear();
    }
    private void tick(){
        if(players.isEmpty())return;
        if(++ticks%30==0){if(afk&&players.values().stream().noneMatch(f->f.location!=null))placeAfk();for(Fake f:players.values())f.ping=Math.clamp(f.basePing+ThreadLocalRandom.current().nextInt(-5,6),10,180);for(Player p:Bukkit.getOnlinePlayers())sendList(p,false);}
        for(Player p:Bukkit.getOnlinePlayers()){
            Set<Integer> shown=visible.computeIfAbsent(p.getUniqueId(),id->new HashSet<>());
            for(Fake f:players.values()){
                Location at=f.location;boolean near=at!=null&&at.getWorld()==p.getWorld()&&at.distanceSquared(p.getLocation())<=80*80&&at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);
                if(near&&shown.add(f.entity)){
                    send(p,new WrapperPlayServerSpawnEntity(f.entity,Optional.of(f.uuid),EntityTypes.PLAYER,new Vector3d(at.getX(),at.getY(),at.getZ()),0,at.getYaw(),at.getYaw(),0,Optional.empty()));
                    send(p,new WrapperPlayServerEntityHeadLook(f.entity,at.getYaw()));
                }else if(!near&&shown.remove(f.entity))send(p,new WrapperPlayServerDestroyEntities(f.entity));
            }
        }
    }
    private int placeAfk(){
        for(Player p:Bukkit.getOnlinePlayers()){Set<Integer> ids=visible.remove(p.getUniqueId());if(ids!=null&&!ids.isEmpty())send(p,new WrapperPlayServerDestroyEntities(ids.stream().mapToInt(Integer::intValue).toArray()));}
        players.values().forEach(f->f.location=null);if(!afk)return 0;
        YamlConfiguration zone=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("afk-zone.yml").toFile());
        if(!zone.getBoolean("enabled"))return 0;
        World world;try{world=Bukkit.getWorld(UUID.fromString(zone.getString("world","")));}catch(IllegalArgumentException e){return 0;}if(world==null)return 0;
        int minX=zone.getInt("min-x"),maxX=zone.getInt("max-x"),minZ=zone.getInt("min-z"),maxZ=zone.getInt("max-z");
        int minY=Math.max(world.getMinHeight()+1,zone.getInt("min-y")),maxY=Math.min(world.getMaxHeight()-2,zone.getInt("max-y"));
        if(maxX<minX||maxZ<minZ||maxY<minY)return 0;
        Set<String> used=new HashSet<>();int placed=0;var random=ThreadLocalRandom.current();
        for(Fake f:players.values()){
            if(placed>=afkCount(players.size()))break;
            for(int attempt=0;attempt<30;attempt++){
                int x=(int)random.nextLong(minX,(long)maxX+1),z=(int)random.nextLong(minZ,(long)maxZ+1);
                if(!world.isChunkLoaded(x>>4,z>>4))continue;
                for(int y=maxY;y>=minY;y--){
                    String key=x+":"+y+":"+z;if(used.contains(key))continue;
                    var ground=world.getBlockAt(x,y-1,z);var feet=world.getBlockAt(x,y,z);var head=world.getBlockAt(x,y+1,z);
                    Location at=new Location(world,x+.5,y,z+.5,random.nextFloat()*360,0);
                    if(!ground.getType().isSolid()||!ground.getCollisionShape().getBoundingBoxes().stream().anyMatch(b->b.getMaxY()>=1)||!feet.getType().isAir()||!head.getType().isAir()||!world.getWorldBorder().isInside(at))continue;
                    if(Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE).contains(ground.getType()))continue;
                    f.location=at;used.add(key);placed++;break;
                }if(f.location!=null)break;
            }
        }return placed;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] supplied){
        if(!owner(sender)){sender.sendMessage("Only the Owner rank can use this command.");return true;}
        if(task==null){sender.sendMessage("PacketEvents is unavailable; simulated players are disabled.");return true;}
        String[] args=supplied;if(label.equalsIgnoreCase("fake")&&args.length>0&&args[0].equalsIgnoreCase("players"))args=Arrays.copyOfRange(args,1,args.length);
        if(args.length==0){sender.sendMessage("/fakeplayers <count> | afk [off] | clear | status. Current: "+players.size()+", limit: "+limit());return true;}
        if(args[0].equalsIgnoreCase("status")){sender.sendMessage("Simulated profiles: "+players.size()+" | AFK bodies: "+players.values().stream().filter(f->f.location!=null).count());return true;}
        var old=new LinkedHashMap<>(players);boolean oldAfk=afk;
        try{
            if(args[0].equalsIgnoreCase("afk")){
                afk=args.length<2||!args[1].equalsIgnoreCase("off");int placed=placeAfk();save();sender.sendMessage("AFK display: "+placed+" / "+afkCount(players.size())+" placed. Uses safe, loaded ground inside the AFK selection.");return true;
            }
            int count=args[0].equalsIgnoreCase("clear")?0:Integer.parseInt(args[0]);
            if(count<0||count>limit()){sender.sendMessage("Choose 0 to "+limit()+". The owner can adjust fakeplayers.max-count in config.yml.");return true;}
            removeDisplays();while(players.size()>count){String key=new ArrayList<>(players.keySet()).getLast();players.remove(key);}
            while(players.size()<count){String name=newName();players.put(name.toLowerCase(Locale.ROOT),new Fake(name,UUID.randomUUID(),nextId--,ping(ThreadLocalRandom.current())));}
            save();if(afk)placeAfk();for(Player p:Bukkit.getOnlinePlayers())sendList(p,true);sender.sendMessage("Simulated player count set to "+count+".");
        }catch(NumberFormatException e){sender.sendMessage("Use /fakeplayers <count>, afk [off], clear or status.");}
        catch(Exception e){removeDisplays();players.clear();players.putAll(old);afk=oldAfk;if(afk)placeAfk();for(Player p:Bukkit.getOnlinePlayers())sendList(p,true);sender.sendMessage("Could not save simulated players; previous state restored.");plugin.getLogger().warning("Fake players: "+e.getMessage());}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args){return owner(s)?List.of("afk","clear","status","10","25","50").stream().filter(v->v.startsWith(args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT))).toList():List.of();}
    private void removeCollision(String name){Fake f=players.remove(name.toLowerCase(Locale.ROOT));if(f==null)return;for(Player viewer:Bukkit.getOnlinePlayers()){send(viewer,new WrapperPlayServerPlayerInfoRemove(List.of(f.uuid)));send(viewer,new WrapperPlayServerDestroyEntities(f.entity));}visible.values().forEach(s->s.remove(f.entity));try{save();}catch(Exception e){plugin.getLogger().warning("Could not save fake-player collision removal");}}
    @EventHandler public void join(PlayerJoinEvent e){removeCollision(e.getPlayer().getName());Bukkit.getScheduler().runTaskLater(plugin,()->{if(e.getPlayer().isOnline())sendList(e.getPlayer(),true);},20);}
    @EventHandler public void changedWorld(PlayerChangedWorldEvent e){visible.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void respawn(PlayerRespawnEvent e){visible.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){visible.remove(e.getPlayer().getUniqueId());lastRequest.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void commands(PlayerCommandSendEvent e){if(!owner(e.getPlayer()))e.getCommands().removeIf(s->s.equals("fake")||s.equals("fakeplayers")||s.endsWith(":fakeplayers")||s.endsWith(":fake"));}
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true) public void target(PlayerCommandPreprocessEvent e){
        String[] args=e.getMessage().substring(1).trim().split("\\s+",3);if(args.length<2)return;
        String root=args[0].toLowerCase(Locale.ROOT);root=root.substring(root.lastIndexOf(':')+1);
        boolean message=Set.of("msg","message","tell","w","whisper","m","pm").contains(root);
        boolean teleport=Set.of("tpa","tpahere","tphere","tp","teleport").contains(root);
        if(!message&&!teleport)return;
        Fake fake=players.get(args[1].toLowerCase(Locale.ROOT));if(fake==null||Bukkit.getPlayerExact(args[1])!=null)return;
        e.setCancelled(true);Player p=e.getPlayer();
        if(plugin.combat()!=null&&plugin.combat().tagged(p)){p.sendMessage("Commands are disabled while you are in combat.");return;}
        if(message){if(args.length<3){p.sendMessage("Usage: /msg <player> <message>");return;}p.sendMessage(Component.text("To "+fake.name+": "+args[2],NamedTextColor.GRAY));return;}
        long now=System.currentTimeMillis();if(lastRequest.getOrDefault(p.getUniqueId(),0L)>now){p.sendMessage("Please wait before sending another teleport request.");return;}
        lastRequest.put(p.getUniqueId(),now+10000);p.sendMessage("Teleport request sent to "+fake.name+".");
    }
    @Override public void close(){if(task!=null){task.cancel();removeDisplays();}HandlerList.unregisterAll(this);players.clear();visible.clear();lastRequest.clear();}
}
