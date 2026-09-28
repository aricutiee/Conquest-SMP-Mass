package dev.turtleroles.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Optional integrations use the owning plugin's class loader, without bundling its API. */
public final class ClientCompatibility {
    private ClientCompatibility() {}
    public static boolean bedrock(Player player) {
        if(Bukkit.getPluginManager()==null)return false;
        var floodgate=Bukkit.getPluginManager().getPlugin("floodgate");
        if(floodgate==null || !floodgate.isEnabled())return false;
        try {
            Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,floodgate.getClass().getClassLoader());
            return (boolean)api.getMethod("isFloodgatePlayer",java.util.UUID.class).invoke(api.getMethod("getInstance").invoke(null),player.getUniqueId());
        } catch(ReflectiveOperationException e) {
            // Floodgate's unlinked Bedrock UUID format has a zero most-significant half.
            return player.getUniqueId().getMostSignificantBits()==0;
        }
    }
    public static boolean authenticated(Player player) {
        if(Bukkit.getPluginManager()==null)return true;
        var auth=Bukkit.getPluginManager().getPlugin("AuthMe");
        if(auth==null || !auth.isEnabled())return true;
        try {
            Class<?> api=Class.forName("fr.xephi.authme.api.v3.AuthMeApi",true,auth.getClass().getClassLoader());
            return (boolean)api.getMethod("isAuthenticated",Player.class).invoke(api.getMethod("getInstance").invoke(null),player);
        } catch(ReflectiveOperationException e) {return false;}
    }
}
