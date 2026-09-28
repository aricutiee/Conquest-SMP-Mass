package dev.turtleroles.survival;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.key.Key;
import java.util.function.Predicate;
import java.util.*;

final class SurvivalSidebar implements AutoCloseable {
    private record View(Scoreboard board,Scoreboard previous,Objective objective,List<Team> lines){}
    private final Map<UUID,View> views=new HashMap<>();
    private final NamespacedKey streak;
    private final Predicate<Player> customIcons;
    private static final TextColor PURPLE = TextColor.color(0xE1CBFF);
    private static final Key ICON_FONT = Key.key("turtleroles:stats");
    private static final Key SIDEBAR_FONT = Key.key("turtleroles:sidebar");
    private static final int LINE_COUNT = 11;
    SurvivalSidebar(NamespacedKey streak, Predicate<Player> customIcons){this.streak=streak;this.customIcons=customIcons;}
    static String duration(long seconds) {
        if(seconds<60)return seconds+"s";
        if(seconds<3600)return seconds/60+"m "+seconds%60+"s";
        if(seconds<86400)return seconds/3600+"h "+seconds%3600/60+"m";
        return seconds/86400+"d "+seconds%86400/3600+"h";
    }
    void refresh(Player player) {
        // A join during an event must not replace the event's already attached board.
        if(!views.containsKey(player.getUniqueId())&&player.getScoreboard().getObjective(DisplaySlot.SIDEBAR)!=null)return;
        View view=views.computeIfAbsent(player.getUniqueId(),id->create(player));
        if(player.getScoreboard()!=view.board)return;
        boolean icons=customIcons.test(player);
        Component rule=Component.text("------------------",PURPLE).decorate(TextDecoration.STRIKETHROUGH);
        Component[] lines={heading("COMBAT",icons),rule,
                stat(0,"Kills",Integer.toString(player.getStatistic(Statistic.PLAYER_KILLS)),icons),
                stat(1,"Deaths",Integer.toString(player.getStatistic(Statistic.DEATHS)),icons),
                stat(2,"Streak",Integer.toString(player.getPersistentDataContainer().getOrDefault(streak,PersistentDataType.INTEGER,0)),icons),
                Component.space(),heading("OTHER",icons),rule,
                stat(3,"Ping",player.getPing()+"ms",icons),
                stat(4,"Playtime",duration(Integer.toUnsignedLong(player.getStatistic(Statistic.PLAY_ONE_MINUTE))/20),icons),rule};
        for(int i=0;i<lines.length;i++)view.lines.get(i).prefix(lines[i]);
        // A grace-period objective can temporarily occupy the sidebar without being overwritten.
        if(view.board.getObjective(DisplaySlot.SIDEBAR)==null)view.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }
    private static Component heading(String text,boolean custom) {
        return letters(text,custom).decorate(TextDecoration.BOLD);
    }
    static Component letters(String text,boolean custom) {
        // Bitmap colors must be multiplied by white to preserve the selected light purple.
        return custom ? Component.text(text.toUpperCase(Locale.ROOT),TextColor.color(0xFFFFFF)).font(SIDEBAR_FONT)
                : Component.text(smallCaps(text),PURPLE).font(Key.key("minecraft:default"));
    }
    private static Component stat(int icon,String label,String value,boolean custom) {
        Component symbol=custom ? Component.text(Character.toString((char)(0xE300+icon))).font(ICON_FONT)
                : Component.text(new String[]{"⚔","☠","▲","≋","◷"}[icon]);
        return Component.empty().font(Key.key("minecraft:default")).color(custom?TextColor.color(0xFFFFFF):PURPLE)
                .decoration(TextDecoration.BOLD,false).decoration(TextDecoration.ITALIC,false)
                .append(symbol.decoration(TextDecoration.BOLD,false)).append(Component.space())
                .append(letters(label+": ",custom).decorate(TextDecoration.BOLD))
                .append(letters(value,custom).decoration(TextDecoration.BOLD,false));
    }
    private static String smallCaps(String text) {
        String alphabet="ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder result=new StringBuilder();
        for(char c:text.toCharArray()) {
            int index=Character.toUpperCase(c)-'A';
            result.append(index>=0 && index<26 ? alphabet.charAt(index) : c);
        }
        return result.toString();
    }
    private static TextColor blend(int from,int to,double fraction) {
        int r=(int)Math.round(((from>>16)&255)*(1-fraction)+((to>>16)&255)*fraction);
        int g=(int)Math.round(((from>>8)&255)*(1-fraction)+((to>>8)&255)*fraction);
        int b=(int)Math.round((from&255)*(1-fraction)+(to&255)*fraction);
        return TextColor.color(r,g,b);
    }
    private static Component title() {
        String text=smallCaps("CONQUEST SMP");
        Component result=Component.empty().decorate(TextDecoration.BOLD);
        for(int i=0;i<text.length();i++)result=result.append(Component.text(String.valueOf(text.charAt(i)),blend(0x29143D,0xB78AFF,i/(double)(text.length()-1))));
        return result;
    }
    private View create(Player player) {
        Scoreboard previous=player.getScoreboard(), board=Bukkit.getScoreboardManager().getNewScoreboard();
        for(Team original:previous.getTeams()) {
            Team team=board.registerNewTeam(original.getName());team.prefix(original.prefix());team.suffix(original.suffix());
            team.setColor(original.getColor());team.setAllowFriendlyFire(original.allowFriendlyFire());team.setCanSeeFriendlyInvisibles(original.canSeeFriendlyInvisibles());
            for(Team.Option option:Team.Option.values())team.setOption(option,original.getOption(option));
            original.getEntries().forEach(team::addEntry);
        }
        Objective objective=board.registerNewObjective("cq_survival",Criteria.DUMMY,title());
        objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        List<Team> lines=new ArrayList<>();
        for(int i=0;i<LINE_COUNT;i++) {
            String entry="§"+Integer.toHexString(i)+"§r";
            Team team=board.registerNewTeam("cq_line"+i);team.addEntry(entry);lines.add(team);objective.getScore(entry).setScore(LINE_COUNT-i);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);player.setScoreboard(board);
        return new View(board,previous,objective,lines);
    }
    void remove(Player player) {
        View view=views.remove(player.getUniqueId());
        if(view!=null && player.getScoreboard()==view.board)player.setScoreboard(view.previous);
    }
    @Override public void close(){for(Player player:Bukkit.getOnlinePlayers())remove(player);views.clear();}
}
