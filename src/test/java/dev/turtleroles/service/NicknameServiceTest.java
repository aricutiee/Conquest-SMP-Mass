package dev.turtleroles.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NicknameServiceTest {
    TurtleRolesPlugin plugin;RoleService roles;PresentationService presentation;NicknameService service;
    MockedStatic<Bukkit> bukkit;List<Player> online;CompletableFuture<PlayerProfile> result;
    @BeforeEach void setup(){
        plugin=mock(TurtleRolesPlugin.class);roles=mock(RoleService.class);presentation=mock(PresentationService.class);
        when(plugin.roleService()).thenReturn(roles);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.isEnabled()).thenReturn(true);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        online=new ArrayList<>();bukkit=mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(i->online);
        bukkit.when(()->Bukkit.getPlayer(any(UUID.class))).thenAnswer(i->online.stream().filter(p->p.getUniqueId().equals(i.getArgument(0))).findFirst().orElse(null));
        var scheduler=mock(BukkitScheduler.class);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{i.<Runnable>getArgument(1).run();return null;});
        result=new CompletableFuture<>();service=new NicknameService(plugin,presentation,name->result);
    }
    @AfterEach void cleanup(){bukkit.close();}
    Player player(String name,Role role){
        Player p=mock(Player.class);UUID id=UUID.randomUUID();AtomicReference<PlayerProfile> current=new AtomicReference<>(profile(id,name));
        when(p.getUniqueId()).thenReturn(id);when(p.getName()).thenAnswer(i->current.get().getName());
        when(p.getPlayerProfile()).thenAnswer(i->current.get());when(p.isOnline()).thenReturn(true);
        doAnswer(i->{current.set(i.getArgument(0));return null;}).when(p).setPlayerProfile(any());
        when(roles.roleOf(id)).thenReturn(role);online.add(p);return p;
    }
    PlayerProfile profile(UUID id,String name){
        var p=mock(PlayerProfile.class);AtomicReference<UUID> uuid=new AtomicReference<>(id);
        when(p.getId()).thenAnswer(i->uuid.get());when(p.getName()).thenReturn(name);when(p.hasTextures()).thenReturn(true);
        when(p.setId(any())).thenAnswer(i->uuid.getAndSet(i.getArgument(0)));
        when(p.clone()).thenAnswer(i->profile(uuid.get(),name));return p;
    }
    void cmd(Player p,String... args){service.onCommand(p,null,"nick",args);}
    @Test void onlyBoosterAndHigherOrOpQualify(){
        for(Role role:Role.values())assertEquals(role!=Role.MEMBER,NicknameService.eligible(role,false));
        assertTrue(NicknameService.eligible(Role.MEMBER,true));
        assertFalse(NicknameService.validName("../Joe"));assertFalse(NicknameService.validName("a b"));assertTrue(NicknameService.validName("Jerry_12"));
        Player p=player("Ari",Role.MEMBER);cmd(p,"Jerry");result.complete(profile(UUID.randomUUID(),"Jerry"));verify(p,never()).setPlayerProfile(any());
    }
    @Test void copyNameAndSkinPreservesRealUuidAndRestoresOriginalProfile(){
        Player p=player("Ari",Role.BOOSTER);UUID real=p.getUniqueId();PlayerProfile original=p.getPlayerProfile();
        PlayerProfile target=profile(UUID.randomUUID(),"Jerry");UUID targetId=target.getId();
        cmd(p,"Jerry");result.complete(target);
        assertEquals("Jerry",p.getName());assertEquals(real,p.getPlayerProfile().getId());assertEquals(real,p.getUniqueId());
        assertEquals(targetId,target.getId());assertEquals("Ari",service.realName(p));
        verify(p,never()).getInventory();verify(p,never()).setOp(anyBoolean());
        cmd(p,"reset");assertEquals(original.getName(),p.getName());assertEquals(real,p.getPlayerProfile().getId());
    }
    @Test void realOwnerJoinResetsNickname(){
        Player nick=player("Ari",Role.BOOSTER_X2);cmd(nick,"Jerry");result.complete(profile(UUID.randomUUID(),"Jerry"));
        Player real=player("Jerry",Role.MEMBER);service.joined(new PlayerJoinEvent(real,net.kyori.adventure.text.Component.empty()));
        assertEquals("Ari",nick.getName());assertEquals("Jerry",real.getName());
    }
    @Test void joiningDuringLookupPreventsLateImpersonation(){
        Player nick=player("Ari",Role.BOOSTER);cmd(nick,"Jerry");player("Jerry",Role.MEMBER);
        result.complete(profile(UUID.randomUUID(),"Jerry"));verify(nick,never()).setPlayerProfile(any());
    }
    @Test void resetCancelsPendingLookup(){
        Player p=player("Ari",Role.BOOSTER);cmd(p,"Jerry");cmd(p,"reset");result.complete(profile(UUID.randomUUID(),"Jerry"));
        verify(p,never()).setPlayerProfile(any());
    }
    @Test void failedLookupDoesNotChangeAnything(){
        Player p=player("Ari",Role.BOOSTER);cmd(p,"Jerry");result.completeExceptionally(new IllegalStateException("offline"));
        verify(p,never()).setPlayerProfile(any());assertEquals("Ari",p.getName());
    }
    @Test void nickCheckIsStaffOnlyAndShowsRealAccount(){
        Player p=player("Ari",Role.BOOSTER);cmd(p,"Jerry");result.complete(profile(UUID.randomUUID(),"Jerry"));
        Player staff=player("Staff",Role.HELPER);cmd(staff,"check","Jerry");verify(staff).sendMessage(contains("Jerry -> Ari"));
        cmd(p,"check");verify(p).sendMessage("Only staff can check nicknames.");
    }
    @Test void logoutRestoresOriginalAndDoesNotTouchData(){
        Player p=player("Ari",Role.MEDIA);cmd(p,"Jerry");result.complete(profile(UUID.randomUUID(),"Jerry"));
        service.quit(new PlayerQuitEvent(p,net.kyori.adventure.text.Component.empty()));assertEquals("Ari",p.getName());
        verify(p,never()).getInventory();verify(p,never()).setHealth(anyDouble());
    }
    @Test void noDuplicateAliasAndCannotNickAsDisguisedPlayersRealName(){
        Player p=player("Ari",Role.BOOSTER);cmd(p,"Jerry");result.complete(profile(UUID.randomUUID(),"Jerry"));
        Player other=player("Other",Role.BOOSTER);cmd(other,"Jerry");cmd(other,"Ari");verify(other,times(2)).sendMessage("That username is already online or in use.");
    }
}
