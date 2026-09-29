package dev.turtleroles.anticheat;

import com.craftpilot.clientpolicy.ClientPolicyPlugin;
import org.bukkit.entity.Player;
import java.util.*;

/** Calls ClientPolicy's side-effect-free evaluator, not its enforcement service. */
final class ClientBinding {
    record Snapshot(String detail, String violations) {}
    private final ClientPolicyPlugin client;
    ClientBinding(org.bukkit.plugin.Plugin plugin){client=(ClientPolicyPlugin)plugin;}
    Snapshot snapshot(Player player) {
        var fp=client.fingerprints().get(player.getUniqueId());
        if(fp==null || !fp.initialScanDone())return null;
        var result=client.policy().evaluate(player,fp,false);
        String mods=result.matched().keySet().stream().map(s->s.id()).sorted().limit(40).collect(java.util.stream.Collectors.joining(", "));
        String violations=result.violations().stream().filter(v->v.usesLadder()).map(v->v.modId()).sorted().limit(40).collect(java.util.stream.Collectors.joining(", "));
        String detail="Brand="+IntelStore.clean(result.brand(),64)+"; loader="+result.loader()+"; announced mods="+(mods.isEmpty()?"none observed":mods)
            +"; policy matches="+(violations.isEmpty()?"none":violations)+"; locale="+IntelStore.clean(player.locale().toLanguageTag(),32)
            +"; view distance="+player.getClientViewDistance()+"; main hand="+player.getMainHand()
            +". Client-reported context only; not a unique instance ID or complete mod list.";
        return new Snapshot(detail,violations);
    }
}
