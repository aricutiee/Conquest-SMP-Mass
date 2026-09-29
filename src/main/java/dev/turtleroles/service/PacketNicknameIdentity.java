package dev.turtleroles.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** PacketEvents 2.14 bridge for UUID-based client tier lookups on Paper 1.21.11. */
final class PacketNicknameIdentity extends PacketListenerAbstract implements NicknameIdentity {
    private final Map<UUID, UUID> aliases = new ConcurrentHashMap<>();
    // Remember what each connection was actually sent, rather than consulting a new alias
    // when an older removal is still queued on that connection's network event loop.
    private final Map<UUID, Map<UUID, UUID>> sent = new ConcurrentHashMap<>();
    private final Set<UUID> excluded = ConcurrentHashMap.newKeySet();

    PacketNicknameIdentity() { super(PacketListenerPriority.HIGHEST); }
    void start() { PacketEvents.getAPI().getEventManager().registerListener(this); }
    @Override public void set(UUID real, UUID shown) {
        if(shown==null||real.equals(shown)) aliases.remove(real); else aliases.put(real,shown);
    }
    @Override public void viewer(UUID id, boolean skip) { if(skip)excluded.add(id);else excluded.remove(id); }
    @Override public void forgetViewer(UUID id) { sent.remove(id);excluded.remove(id); }

    List<PlayerInfo> update(UUID viewer,List<PlayerInfo> entries,boolean adding) {
        if(excluded.contains(viewer))return entries;
        Map<UUID,UUID> view=sent.computeIfAbsent(viewer,k->new ConcurrentHashMap<>());
        List<PlayerInfo> result=new ArrayList<>();
        for(PlayerInfo info:entries) {
            UUID real=info.getProfileId();
            UUID alias=real.equals(viewer)?null:(adding?aliases.get(real):view.get(real));
            if(alias==null){result.add(info);continue;}
            if(adding)view.put(real,alias);
            // Keep the authentic profile and signing session available but unlisted.
            // PLAYER_CHAT is deliberately never rewritten: its signed sender stays real.
            PlayerInfo authentic=new PlayerInfo(info);authentic.setListed(false);result.add(authentic);
            PlayerInfo display=new PlayerInfo(info);
            UserProfile profile=info.getGameProfile();
            display.setGameProfile(new UserProfile(alias,profile.getName(),new ArrayList<>(profile.getTextureProperties())));
            // A signing key bound to the real UUID must never be attached to the alias UUID.
            display.setChatSession(null);result.add(display);
        }
        return result;
    }
    List<UUID> remove(UUID viewer,List<UUID> entries) {
        Map<UUID,UUID> view=sent.get(viewer);
        if(view==null)return entries;
        Set<UUID> result=new LinkedHashSet<>(entries);
        for(UUID real:entries){UUID alias=view.remove(real);if(alias!=null)result.add(alias);}
        if(view.isEmpty())sent.remove(viewer,view);
        return new ArrayList<>(result);
    }
    UUID spawned(UUID viewer,UUID real) {
        if(excluded.contains(viewer)||real.equals(viewer))return real;
        Map<UUID,UUID> view=sent.get(viewer);return view==null?real:view.getOrDefault(real,real);
    }
    @Override public void onPacketSend(PacketSendEvent event) {
        if(event.isCancelled()||event.getUser()==null)return;
        UUID viewer=event.getUser().getUUID();if(viewer==null)return;
        if(aliases.isEmpty()&&!sent.containsKey(viewer))return;
        var type=event.getPacketType();
        if(type==PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            var packet=new WrapperPlayServerPlayerInfoUpdate(event);
            packet.setEntries(update(viewer,packet.getEntries(),packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)));
        } else if(type==PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            var packet=new WrapperPlayServerPlayerInfoRemove(event);
            packet.setProfileIds(remove(viewer,packet.getProfileIds()));
        } else if(type==PacketType.Play.Server.SPAWN_ENTITY) {
            var packet=new WrapperPlayServerSpawnEntity(event);
            if(packet.getEntityType()==EntityTypes.PLAYER)packet.setUUID(packet.getUUID().map(id->spawned(viewer,id)));
        }
    }
    @Override public void close() {
        // Explicit removals also cover queued Paper refresh packets during plugin shutdown.
        for(var entry:sent.entrySet()) {
            var player=org.bukkit.Bukkit.getPlayer(entry.getKey());if(player==null)continue;
            var user=PacketEvents.getAPI().getPlayerManager().getUser(player);
            if(user!=null&&!entry.getValue().isEmpty())user.sendPacketSilently(new WrapperPlayServerPlayerInfoRemove(new ArrayList<>(entry.getValue().values())));
        }
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        aliases.clear();sent.clear();excluded.clear();
    }
}
