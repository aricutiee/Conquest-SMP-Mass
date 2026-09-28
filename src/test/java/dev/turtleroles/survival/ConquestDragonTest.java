package dev.turtleroles.survival;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConquestDragonTest {
    ServerMock server;JavaPlugin plugin;ConquestDragon rules;EnderDragon dragon;AttributeInstance max;double hp;
    @BeforeEach void setup(){
        server=MockBukkit.mock();plugin=mock(JavaPlugin.class);when(plugin.getConfig()).thenReturn(new YamlConfiguration());rules=new ConquestDragon(plugin);
        dragon=mock(EnderDragon.class);max=server.addPlayer().getAttribute(Attribute.MAX_HEALTH);max.setBaseValue(200);hp=200;
        when(dragon.getAttribute(Attribute.MAX_HEALTH)).thenReturn(max);when(dragon.getHealth()).thenAnswer(i->hp);doAnswer(i->{hp=i.getArgument(0);return null;}).when(dragon).setHealth(anyDouble());
        when(dragon.getBossBar()).thenReturn(server.createBossBar("Ender Dragon",org.bukkit.boss.BarColor.PINK,org.bukkit.boss.BarStyle.SOLID));when(dragon.getPhase()).thenReturn(EnderDragon.Phase.CIRCLING);
    }
    @AfterEach void close(){rules.close();MockBukkit.unmock();}
    @Test void vanillaHealthBecomes1000AndDoesNotStackOrHealOnRestart(){
        rules.apply(dragon);assertEquals(1000,max.getValue());assertEquals(1000,hp);
        hp=730;rules.apply(dragon);new ConquestDragon(plugin).apply(dragon);assertEquals(730,hp);assertEquals(1,max.getModifiers().size());assertEquals(1000,max.getValue());
        assertEquals(org.bukkit.boss.BarColor.PURPLE,dragon.getBossBar().getColor());assertEquals(.73,dragon.getBossBar().getProgress(),.001);
    }
    @Test void firstAdoptionPreservesInjuryAndExternalModifiers(){
        var other=new AttributeModifier(new NamespacedKey("another","bonus"),20,AttributeModifier.Operation.ADD_NUMBER);max.addModifier(other);hp=110;
        rules.apply(dragon);assertTrue(max.getModifiers().contains(other));assertEquals(1020,max.getValue());assertEquals(510,hp);
    }
    @Test void deadDragonsAreNeverRevived(){hp=0;rules.apply(dragon);assertEquals(0,hp);assertEquals(200,max.getValue());verify(dragon,never()).setHealth(anyDouble());}
    @Test void nameUsesSmallCapsAndThreeStopGradient(){
        var name=ConquestDragon.name();assertEquals("ᴇɴᴅᴇʀ ᴅʀᴀɢᴏɴ",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name));
        String json=net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(name);assertTrue(json.toLowerCase().contains("#08000d"));assertTrue(json.toLowerCase().contains("#a347e0"));
    }
    @Test void explosionsBlockedInEveryPhaseForParentAndParts(){
        var part=mock(EnderDragonPart.class);when(part.getParent()).thenReturn(dragon);
        for(var phase:EnderDragon.Phase.values())for(Entity target:new Entity[]{dragon,part})for(var cause:new EntityDamageEvent.DamageCause[]{EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION}){
            when(dragon.getPhase()).thenReturn(phase);var damage=mock(EntityDamageEvent.class);when(damage.getEntity()).thenReturn(target);when(damage.getCause()).thenReturn(cause);rules.damage(damage);verify(damage).setCancelled(true);
        }
    }
    @Test void onlyPlayerMeleeAcceptedInAllThreePerchedPhases(){
        for(var phase:new EnderDragon.Phase[]{EnderDragon.Phase.BREATH_ATTACK,EnderDragon.Phase.ROAR_BEFORE_ATTACK,EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET}){
            when(dragon.getPhase()).thenReturn(phase);
            for(Entity attacker:new Entity[]{mock(Player.class),mock(Arrow.class),mock(Trident.class),mock(Zombie.class)}){
                var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(dragon);when(hit.getDamager()).thenReturn(attacker);when(hit.getCause()).thenReturn(attacker instanceof Projectile?EntityDamageEvent.DamageCause.PROJECTILE:EntityDamageEvent.DamageCause.ENTITY_ATTACK);
                rules.damage(hit);verify(hit,attacker instanceof Player?never():times(1)).setCancelled(true);
            }
        }
    }
    @Test void flightStillAllowsArrowsAndOtherMobDamageIsUntouched(){
        var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(dragon);when(hit.getDamager()).thenReturn(mock(Arrow.class));when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.PROJECTILE);rules.damage(hit);verify(hit,never()).setCancelled(anyBoolean());
        when(hit.getEntity()).thenReturn(mock(Zombie.class));when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION);rules.damage(hit);verify(hit,never()).setCancelled(anyBoolean());
    }
}
