package dev.turtleroles.combat;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import java.util.*;

/** Separate bars let simultaneous ability timers remain visible. */
public final class CooldownBars implements AutoCloseable {
    private record Entry(Player player, Map<String,BossBar> bars) {}
    private final Map<UUID,Entry> entries=new HashMap<>();
    public void timer(Player player,String key,String label,long left,long duration){
        if(left<=0||player.isDead()){hide(player,key);return;}
        show(player,key,label+": "+((left+999)/1000)+"s",duration<=0?0:(float)left/duration);
    }
    public void show(Player player,String key,String label,float progress){
        if(player.isDead()){hide(player,key);return;}
        Entry entry=entries.computeIfAbsent(player.getUniqueId(),id->new Entry(player,new HashMap<>()));
        BossBar bar=entry.bars.computeIfAbsent(key,id->{
            BossBar created=BossBar.bossBar(Component.empty(),1,BossBar.Color.PURPLE,BossBar.Overlay.PROGRESS);
            player.showBossBar(created);return created;
        });
        bar.name(Component.text(label,TextColor.color(0xE1CBFF)));
        bar.progress(Math.clamp(progress,0,1));
    }
    public void hide(Player player,String key){
        Entry entry=entries.get(player.getUniqueId());if(entry==null)return;
        BossBar bar=entry.bars.remove(key);if(bar!=null)player.hideBossBar(bar);
        if(entry.bars.isEmpty())entries.remove(player.getUniqueId());
    }
    public void hideAll(Player player){
        Entry entry=entries.remove(player.getUniqueId());if(entry!=null)entry.bars.values().forEach(player::hideBossBar);
    }
    public void close(){entries.values().forEach(e->e.bars.values().forEach(e.player::hideBossBar));entries.clear();}
}
