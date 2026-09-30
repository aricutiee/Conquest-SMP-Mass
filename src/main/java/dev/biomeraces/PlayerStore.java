package dev.biomeraces;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Atomic saves on choices and cooldown activation, not just logout. Expiries use UTC epoch millis. */
public final class PlayerStore {
    private final Path file;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private record KillWindow(int count,long until){}
    private final Map<String,KillWindow> kills=new HashMap<>();
    public PlayerStore(Path file) throws Exception {
        this.file = file;
        if (!Files.exists(file)) return;
        YamlConfiguration data = new YamlConfiguration(); data.load(file.toFile());
        var section = data.getConfigurationSection("players");
        Path legacyCopy=file.resolveSibling(file.getFileName()+".pre-shards");
        if(section!=null&&!section.getKeys(false).isEmpty()&&!Files.exists(legacyCopy)
                &&section.getKeys(false).stream().noneMatch(id->data.contains("players."+id+".shards")))Files.copy(file,legacyCopy);
        if (section != null) for (String id : section.getKeys(false)) {
            UUID uuid = UUID.fromString(id); String path = "players." + id + ".";
            PlayerState state = get(uuid); state.base = Race.parse(data.getString(path + "race"));
            if (state.base == Race.DRAGONBORN) state.base = null;
            state.pendingRoll = Race.parse(data.getString(path + "pending-roll"));
            if (state.base != null || state.pendingRoll == Race.DRAGONBORN) state.pendingRoll = null;
            state.shards = Math.max(0,data.getLong(path + "shards"));
            state.offenseUntil = data.getLong(path + "offense-until");
            state.defenseUntil = data.getLong(path + "defense-until");
        }
        var killSection=data.getConfigurationSection("shard-kills");
        if(killSection!=null)for(String key:killSection.getKeys(false)){var k=killSection.getConfigurationSection(key);if(k!=null&&k.getLong("until")>System.currentTimeMillis())kills.put(key,new KillWindow(k.getInt("count"),k.getLong("until")));}
        var nameSection = data.getConfigurationSection("names");
        if (nameSection != null) for (String name : nameSection.getKeys(false)) names.put(name, UUID.fromString(nameSection.getString(name)));
    }
    public PlayerState get(UUID id) { return states.computeIfAbsent(id, unused -> new PlayerState()); }
    public Collection<PlayerState> all() { return states.values(); }
    public void remember(String name, UUID uuid) { names.put(name.toLowerCase(Locale.ROOT), uuid); }
    public UUID lookup(String name) {
        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) { return names.get(name.toLowerCase(Locale.ROOT)); }
    }
    /** Debit and recoverable roll result commit in the same atomic file replacement. */
    public boolean purchaseReroll(UUID id,Race result,long price) throws IOException {
        PlayerState state=get(id);
        if(price<=0||result==null||result==Race.DRAGONBORN||state.base==null||state.pendingRoll!=null||state.shards<price)return false;
        Race old=state.base;long balance=state.shards;
        state.base=null;state.pendingRoll=result;state.shards-=price;
        try{save();return true;}catch(IOException e){state.base=old;state.pendingRoll=null;state.shards=balance;throw e;}
    }
    public boolean setShards(UUID id,long amount) throws IOException {
        if(amount<0)return false;
        PlayerState state=get(id);long old=state.shards;state.shards=amount;
        try{save();return true;}catch(IOException e){state.shards=old;throw e;}
    }
    public boolean addShards(UUID id,long amount) throws IOException {
        if(amount<=0)return false;PlayerState state=get(id);long old=state.shards;
        try{state.shards=Math.addExact(old,amount);}catch(ArithmeticException e){return false;}
        try{save();return true;}catch(IOException e){state.shards=old;throw e;}
    }
    public boolean spend(UUID id,long amount) throws IOException {
        PlayerState state=get(id);if(amount<=0||state.shards<amount)return false;state.shards-=amount;
        try{save();return true;}catch(IOException e){state.shards+=amount;throw e;}
    }
    public void creditBatch(Map<UUID,Long> awards) throws IOException {
        Map<UUID,Long> old=new HashMap<>();
        try{for(var award:awards.entrySet())if(award.getValue()>0){PlayerState state=get(award.getKey());old.put(award.getKey(),state.shards);state.shards=Math.addExact(state.shards,award.getValue());}save();}
        catch(IOException|ArithmeticException e){old.forEach((id,balance)->get(id).shards=balance);throw e;}
    }
    /** At most three rewarded kills of a given victim in each persisted 72-hour window. */
    public boolean rewardKill(UUID killer,UUID victim,long now) throws IOException {
        if(killer.equals(victim))return false;
        String key=killer+"_"+victim;KillWindow old=kills.get(key);KillWindow window=old;
        if(window==null||now>=window.until)window=new KillWindow(0,now+72L*60*60*1000);
        if(window.count>=3)return false;
        PlayerState state=get(killer);long balance=state.shards;
        if(balance>Long.MAX_VALUE-25)return false;
        state.shards+=25;kills.put(key,new KillWindow(window.count+1,window.until));
        try{save();return true;}catch(IOException e){state.shards=balance;if(old==null)kills.remove(key);else kills.put(key,old);throw e;}
    }
    public void save() throws IOException {
        YamlConfiguration data = new YamlConfiguration();
        states.forEach((id, state) -> {
            String path = "players." + id + ".";
            data.set(path + "race", state.base == null ? null : state.base.key());
            data.set(path + "pending-roll", state.pendingRoll == null ? null : state.pendingRoll.key());
            data.set(path + "shards", state.shards);
            data.set(path + "offense-until", state.offenseUntil);
            data.set(path + "defense-until", state.defenseUntil);
        });
        names.forEach((name, id) -> data.set("names." + name, id.toString()));
        kills.forEach((key,value)->{data.set("shard-kills."+key+".count",value.count);data.set("shard-kills."+key+".until",value.until);});
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, data.saveToString(), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
