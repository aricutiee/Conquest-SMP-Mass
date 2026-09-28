package dev.turtleroles.events;

import java.util.*;

/** Actual health damage; insertion order deterministically breaks exact ties. */
final class DamageRanking {
    record Entry(UUID player,String name,double damage) {}
    private final Map<UUID,Entry> scores=new LinkedHashMap<>();
    void add(UUID player,String name,double finalDamage,double health) {
        if(!Double.isFinite(finalDamage)||!Double.isFinite(health)||finalDamage<=0||health<=0)return;
        Entry old=scores.get(player);
        scores.put(player,new Entry(player,name,(old==null?0:old.damage())+Math.min(finalDamage,health)));
    }
    void restore(UUID id,String name,double value) {if(Double.isFinite(value)&&value>0)scores.put(id,new Entry(id,name,value));}
    List<Entry> top(int count) {return scores.values().stream().sorted(Comparator.comparingDouble(Entry::damage).reversed()).limit(count).toList();}
    Collection<Entry> all(){return scores.values();}
    void clear(){scores.clear();}
}
