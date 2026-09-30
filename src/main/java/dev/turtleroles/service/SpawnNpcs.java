package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.biomeraces.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import java.nio.file.Path;
import java.util.*;

/** Vanilla villagers, decorative display entities and server-owned menus. */
public final class SpawnNpcs implements Listener,CommandExecutor,TabCompleter,AutoCloseable {
 enum Kind {utilities,discord,races,rtp,reroll,spawners}
 private record Actor(Villager body,List<Entity> decorations){void remove(){body.remove();decorations.forEach(Entity::remove);}}
 private static final class Menu implements InventoryHolder {
  final String kind;final UUID owner;Inventory inventory;final Map<Integer,ItemStack> stock=new HashMap<>();final Map<Integer,Long> prices=new HashMap<>();
  Menu(String kind,UUID owner){this.kind=kind;this.owner=owner;}public Inventory getInventory(){return inventory;}
 }
 private final TurtleRolesPlugin plugin;private final Path file;private final YamlConfiguration data;
 private final Map<Kind,Actor> actors=new EnumMap<>(Kind.class);private final Map<UUID,Kind> entities=new HashMap<>();
 private final Map<UUID,Long> clicks=new HashMap<>();private final ShardRewards rewards;private BukkitTask task;private UUID editor;private boolean relocating;
 public SpawnNpcs(TurtleRolesPlugin plugin){this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("npcs.yml");data=YamlConfiguration.loadConfiguration(file.toFile());rewards=new ShardRewards(plugin);
  defaults("discord-invite","https://discord.gg/hn23SeVzh3");
  String[] names={"Utilities","Discord","Races Guide","Random Teleport","Race Reroll","Spawner Shop"};String[] colors={"purple","purple","green","gold","pink","purple"};
  String[] lines={"Buy supplies with shards","Right-click me to get an invite to our Discord server","Discover your race and its abilities","Right-click to randomly teleport","Roll a random race for 250 shards","Buy virtual spawners with shards"};
  for(Kind kind:Kind.values()){defaults(kind+".title",names[kind.ordinal()]);defaults(kind+".color",colors[kind.ordinal()]);defaults(kind+".description",lines[kind.ordinal()]);}
  if(!data.getBoolean("utilities-purple-v1")){data.set("utilities.color","purple");data.set("utilities-purple-v1",true);save();}
  if(!data.contains("shop-initialized")){Material[] items={Material.PACKED_ICE,Material.WIND_CHARGE,Material.OBSIDIAN,Material.CHORUS_FRUIT};for(int i=0;i<items.length;i++)data.set("shop."+i+".item",new ItemStack(items[i],16));data.set("shop-initialized",true);}
 }
 public void cancelAfkSelection(UUID id){rewards.cancelSelection(id);}
 private void defaults(String key,Object value){if(!data.contains(key))data.set(key,value);}
 public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);var c=Objects.requireNonNull(plugin.getCommand("npc"));c.setExecutor(this);c.setTabCompleter(this);rewards.start();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,10);}
 private static Component label(String text,TextColor color){return Component.text(ConquestMotd.smallCaps(text),color).decoration(TextDecoration.ITALIC,false);}
 private TextColor color(Kind kind){TextColor color=SkyWords.color(data.getString(kind+".color","purple"));return color==null?TextColor.color(0xB477FF):color;}
 Location location(Kind kind){String k=kind+".location.";String world=data.getString(k+"world");if(world==null)return null;try{World w=Bukkit.getWorld(UUID.fromString(world));return w==null?null:new Location(w,data.getDouble(k+"x"),data.getDouble(k+"y"),data.getDouble(k+"z"),(float)data.getDouble(k+"yaw"),0);}catch(IllegalArgumentException ex){return null;}}
 private void clear(Kind k){Actor a=actors.remove(k);if(a!=null){entities.remove(a.body.getUniqueId());a.remove();}}
 private void tick(){for(Kind k:Kind.values()){
  Location l=location(k);Actor a=actors.get(k);if(l==null||!l.isChunkLoaded()){clear(k);continue;}
  if(a==null||!a.body.isValid()||a.decorations.stream().anyMatch(e->!e.isValid())){clear(k);a=create(k,l);actors.put(k,a);entities.put(a.body.getUniqueId(),k);}
  a.body.setVelocity(new org.bukkit.util.Vector());if(a.body.getLocation().distanceSquared(l)>.01){relocating=true;try{a.body.teleport(l);}finally{relocating=false;}}
  int itemIndex=0;double seconds=System.nanoTime()/1_000_000_000.0;
  for(Entity decoration:a.decorations)if(decoration instanceof ItemDisplay item){double phase=seconds*.65+itemIndex++*Math.PI;item.teleport(orbit(l,phase,seconds));}
  Player nearest=null;double best=36;for(Player p:l.getWorld().getPlayers()){double d=p.getLocation().distanceSquared(l);if(d<best){best=d;nearest=p;}}
  if(nearest!=null){var direction=nearest.getEyeLocation().toVector().subtract(a.body.getEyeLocation().toVector());Location facing=l.clone().setDirection(direction);a.body.setRotation(facing.getYaw(),Math.clamp(facing.getPitch(),-35,35));}
 }}
 static Location orbit(Location anchor,double phase,double seconds){return anchor.clone().add(Math.cos(phase)*.85,1.5+Math.sin(seconds*1.6+phase)*.15,Math.sin(phase)*.85);}
 private Actor create(Kind k,Location l){List<Entity> decorations=new ArrayList<>();Villager body=l.getWorld().spawn(l,Villager.class,v->{v.setAI(false);v.setAdult();v.setInvulnerable(true);v.setSilent(true);v.setGravity(false);v.setCollidable(false);v.setPersistent(false);v.setRemoveWhenFarAway(false);v.setCanPickupItems(false);v.setProfession(switch(k){case utilities->Villager.Profession.TOOLSMITH;case discord->Villager.Profession.CLERIC;case races->Villager.Profession.LIBRARIAN;case rtp->Villager.Profession.CARTOGRAPHER;case reroll->Villager.Profession.ARMORER;case spawners->Villager.Profession.TOOLSMITH;});v.setVillagerType(k==Kind.utilities?Villager.Type.SNOW:Villager.Type.PLAINS);});
  try{
   TextDisplay text=l.getWorld().spawn(l.clone().add(0,2.5,0),TextDisplay.class,e->{e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setBillboard(Display.Billboard.CENTER);e.setBrightness(new Display.Brightness(15,15));e.setSeeThrough(false);e.setDefaultBackground(false);e.setBackgroundColor(Color.fromARGB(0));e.setShadowed(true);e.setLineWidth(280);
    e.text(label(data.getString(k+".title"),color(k)).decorate(TextDecoration.BOLD).append(Component.newline()).append(label(data.getString(k+".description"),k==Kind.utilities?color(k):NamedTextColor.WHITE)).append(Component.newline()).append(label("→ interact ←",color(k)).decorate(TextDecoration.BOLD)));});decorations.add(text);
   Material icon=switch(k){case utilities->Material.WIND_CHARGE;case discord->Material.AMETHYST_SHARD;case races->Material.BOOK;case rtp->Material.COMPASS;case reroll->Material.NETHER_STAR;case spawners->Material.SPAWNER;};
   for(int side:new int[]{-1,1}){ItemDisplay item=l.getWorld().spawn(l.clone().add(side*.85,1.5,0),ItemDisplay.class,e->{e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setBrightness(new Display.Brightness(15,15));e.setBillboard(Display.Billboard.CENTER);e.setTeleportDuration(10);e.setItemStack(new ItemStack(icon));e.setTransformationMatrix(new Matrix4f().scaling(.5f));});decorations.add(item);}
   return new Actor(body,decorations);
  }catch(RuntimeException e){body.remove();decorations.forEach(Entity::remove);throw e;}
 }
 private boolean usable(Player p){if(!ClientCompatibility.authenticated(p)||p.isDead())return false;if(plugin.combat().tagged(p)){p.sendMessage("NPC services are unavailable during combat.");return false;}return true;}
 @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent e){Kind k=entities.get(e.getRightClicked().getUniqueId());if(k==null)return;boolean blocked=e.isCancelled();e.setCancelled(true);if(blocked||e.getHand()!=EquipmentSlot.HAND||!usable(e.getPlayer()))return;
  Player p=e.getPlayer();long now=System.currentTimeMillis();if(now-clicks.getOrDefault(p.getUniqueId(),0L)<400)return;clicks.put(p.getUniqueId(),now);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.25f,1.7f);
  switch(k){case spawners->{if(plugin.virtualSpawners()!=null)plugin.virtualSpawners().shop(p);else p.sendMessage("Spawner shop is unavailable. Contact staff.");}case utilities->shop(p);case races->guide(p);case reroll->reroll(p);case rtp->p.performCommand("rtp");case discord->{String url=data.getString("discord-invite");p.sendMessage(MiniMessage.miniMessage().deserialize("<bold><gradient:#914CFF:#DDB8FF>"+ConquestMotd.smallCaps("This is our Discord")+"</gradient></bold>").append(Component.newline()).append(Component.text(url,TextColor.color(0xC59AFF)).clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(label("Open our Discord",NamedTextColor.LIGHT_PURPLE)))));}}
 }
 @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e){if(entities.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST) public void portal(EntityTeleportEvent e){if(!relocating&&entities.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
 @EventHandler public void leash(PlayerLeashEntityEvent e){if(entities.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
 @EventHandler public void fish(PlayerFishEvent e){if(e.getCaught()!=null&&entities.containsKey(e.getCaught().getUniqueId()))e.setCancelled(true);}
 @EventHandler public void quit(PlayerQuitEvent e){clicks.remove(e.getPlayer().getUniqueId());}
 private Menu menu(Player p,String kind,int size,String name){Menu m=new Menu(kind,p.getUniqueId());m.inventory=Bukkit.createInventory(m,size,label(name,TextColor.color(0xC78FFF)));return m;}
 private static ItemStack item(Material material,String name,TextColor color,List<Component> lore){ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(label(name,color).decorate(TextDecoration.BOLD));meta.lore(lore.stream().map(l->l.decoration(TextDecoration.ITALIC,false)).toList());item.setItemMeta(meta);return item;}
 private static Component lore(String s){return label(s,NamedTextColor.WHITE);}
 private void guide(Player p){Menu m=menu(p,"guide",36,"Races Guide");int[] slots={10,12,14,16,22};Material[] icons={Material.LILY_PAD,Material.MANGROVE_ROOTS,Material.CHERRY_SAPLING,Material.PALE_OAK_SAPLING,Material.IRON_PICKAXE};RollSettings settings=new RollSettings(plugin.races().settings().yaml);
  for(int i=0;i<5;i++){Race race=RollSettings.BASE.get(i);List<Component> lines=new ArrayList<>();for(String raw:plugin.races().settings().yaml.getStringList(race.key()+".description")){String plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(raw));for(String line:wrap(plain,48))lines.add(lore(line));}m.inventory.setItem(slots[i],item(icons[i],race.label(),settings.color(race),lines));}
  m.inventory.setItem(31,item(Material.BOOK,"How abilities work",NamedTextColor.LIGHT_PURPLE,List.of(lore("Abilities activate automatically."),lore("Every third charged melee hit triggers offense."),lore("Same target; 8s reset; 10s offense cooldown."),lore("Defensive cooldowns are independent."),lore("Carry a Dragon Egg to become Dragonborn."),lore("Your saved base race remains intact."))));p.openInventory(m.inventory);
 }
 static List<String> wrap(String text,int width){List<String> result=new ArrayList<>();String line="";for(String word:text.split("\\s+")){if(!line.isEmpty()&&line.length()+word.length()+1>width){result.add(line);line="";}line+=(line.isEmpty()?"":" ")+word;}if(!line.isEmpty())result.add(line);return result;}
 private void reroll(Player p){Menu m=menu(p,"reroll",27,"Random Race Reroll");m.inventory.setItem(11,item(Material.LIME_CONCRETE,"Confirm: 250 shards",NamedTextColor.GREEN,List.of(lore("Balance: "+plugin.races().state(p).shards+" shards"),lore("Random base race. You cannot choose."),lore("The same race can be drawn again."),lore("Includes the full five-second roll."),lore("The result is saved even if you disconnect."))));m.inventory.setItem(15,item(Material.RED_CONCRETE,"Cancel",NamedTextColor.RED,List.of()));p.openInventory(m.inventory);}
 private long price(int slot){return data.getLong("shop."+slot+".price",-1);}
 private ItemStack stock(int slot){ItemStack item=data.getItemStack("shop."+slot+".item");return item==null?null:item.clone();}
 private void shop(Player p){Menu m=menu(p,"shop",54,"Utilities • Shard Shop");for(int slot=0;slot<45;slot++){ItemStack stock=stock(slot);if(stock==null||stock.getType().isAir()){ItemStack pane=new ItemStack(((slot/9+slot%9)%2==0)?Material.PURPLE_STAINED_GLASS_PANE:Material.BLACK_STAINED_GLASS_PANE);var decoration=pane.getItemMeta();decoration.displayName(Component.text(" "));decoration.setHideTooltip(true);pane.setItemMeta(decoration);m.inventory.setItem(slot,pane);continue;}long price=price(slot);m.stock.put(slot,stock.clone());m.prices.put(slot,price);ItemStack shown=stock.clone();var meta=shown.getItemMeta();String offerName=meta.hasDisplayName()?net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName()):stock.getType().name().replace('_',' ');meta.displayName(label(offerName,TextColor.color(0xB477FF)));List<Component> lines=new ArrayList<>(meta.lore()==null?List.of():meta.lore());lines.add(label(price>0?"Price: "+price+" shards for this stack":"Not for sale yet",TextColor.color(0xB477FF)));meta.lore(lines);shown.setItemMeta(meta);m.inventory.setItem(slot,shown);}m.inventory.setItem(49,item(Material.AMETHYST_SHARD,"Your shards: "+plugin.races().state(p).shards,NamedTextColor.LIGHT_PURPLE,List.of(lore("Right-click an offer to purchase."))));p.openInventory(m.inventory);}
 /** Returns a complete replacement storage array or null; never partially delivers a purchase. */
 static ItemStack[] delivery(ItemStack[] storage,ItemStack offer){ItemStack[] next=Arrays.stream(storage).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);int remaining=offer.getAmount();for(ItemStack stack:next)if(stack!=null&&stack.isSimilar(offer)){int add=Math.min(remaining,Math.max(0,stack.getMaxStackSize()-stack.getAmount()));stack.setAmount(stack.getAmount()+add);remaining-=add;}for(int i=0;i<next.length&&remaining>0;i++)if(next[i]==null||next[i].getType().isAir()){next[i]=offer.clone();int add=Math.min(remaining,offer.getMaxStackSize());next[i].setAmount(add);remaining-=add;}return remaining==0?next:null;}
 @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;Player p=(Player)e.getWhoClicked();
  if(m.kind.equals("stock")&&m.owner.equals(p.getUniqueId())&&ShardRewards.admin(plugin,p))return;
  boolean blocked=e.isCancelled();e.setCancelled(true);if(blocked||!m.owner.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=m.inventory.getSize()||!usable(p))return;
  if(m.kind.equals("reroll")){if(e.getRawSlot()==11){p.closeInventory();plugin.races().paidReroll(p,250);}else if(e.getRawSlot()==15)p.closeInventory();}
  else if(m.kind.equals("shop")&&e.getRawSlot()<45){int slot=e.getRawSlot();ItemStack offer=m.stock.get(slot);Long price=m.prices.get(slot);if(offer==null)return;if(price==null||price<=0){p.sendMessage("This offer is not for sale yet.");return;}if(!offer.equals(stock(slot))||price!=price(slot)){p.sendMessage("This offer changed. Please review the refreshed shop.");shop(p);return;}ItemStack[] next=delivery(p.getInventory().getStorageContents(),offer);if(next==null){p.sendMessage("Not enough inventory space. No shards were spent.");return;}
   try{if(!plugin.races().store().spend(p.getUniqueId(),price)){p.sendMessage("You do not have enough shards.");return;}}catch(Exception ex){p.sendMessage("The purchase could not be saved. No shards were spent.");return;}p.getInventory().setStorageContents(next);p.saveData();p.sendMessage("Purchased supplies for "+price+" shards.");shop(p);
  }
 }
 @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu m&&!(m.kind.equals("stock")&&m.owner.equals(e.getWhoClicked().getUniqueId())&&ShardRewards.admin(plugin,e.getWhoClicked())))e.setCancelled(true);}
 @EventHandler public void closeMenu(InventoryCloseEvent e){if(!(e.getInventory().getHolder() instanceof Menu m)||!m.kind.equals("stock"))return;editor=null;if(!ShardRewards.admin(plugin,e.getPlayer()))return;
  Map<Integer,ItemStack> old=new HashMap<>();for(int i=0;i<45;i++){old.put(i,stock(i));data.set("shop."+i+".item",e.getInventory().getItem(i));}if(!save()){old.forEach((i,item)->data.set("shop."+i+".item",item));e.getPlayer().sendMessage("Shop stock could not be saved.");}else e.getPlayer().sendMessage("Shop stock saved. Set shard prices with /npc edit utilities price <slot 1-45> <amount>.");
 }
 private boolean save(){try{AtomicYaml.save(data,file);return true;}catch(Exception e){plugin.getLogger().warning("Could not save NPCs: "+e.getMessage());return false;}}
 private void writeValue(String path,Object value){
  // Bukkit set(path, Map) leaves an opaque map; nested getters need a section.
  if(value instanceof Map<?,?> map)data.createSection(path,map);else data.set(path,value);
 }
 private boolean set(String path,Object value){Object old=data.get(path);if(old instanceof org.bukkit.configuration.ConfigurationSection section)old=new LinkedHashMap<>(section.getValues(false));writeValue(path,value);if(save())return true;writeValue(path,old);return false;}
 private void edit(Player p,Kind k){if(k==Kind.utilities){if(editor!=null){p.sendMessage("Another stock editor is already open.");return;}Menu m=menu(p,"stock",45,"Edit Utilities Stock");for(int i=0;i<45;i++)m.inventory.setItem(i,stock(i));editor=p.getUniqueId();p.openInventory(m.inventory);p.sendMessage("Arrange the items for sale, then close to save. Items are templates. Prices are per displayed stack.");}
  else{Menu m=menu(p,"edit",27,"Edit "+data.getString(k+".title"));m.inventory.setItem(13,item(Material.WRITABLE_BOOK,"NPC editing commands",color(k),List.of(lore("/npc edit "+k+" title <text>"),lore("/npc edit "+k+" description <text>"),lore("/npc edit "+k+" color <color>"),lore("/npc set "+k+" to move here"),lore("/npc remove "+k))));p.openInventory(m.inventory);}}
 @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){if(!ShardRewards.admin(plugin,s)){s.sendMessage("Only administrators can edit NPCs.");return true;}if(a.length==1&&a[0].equalsIgnoreCase("list")){for(Kind k:Kind.values())s.sendMessage(k+": "+data.getString(k+".title")+(data.contains(k+".location")?" (placed)":" (not placed)"));return true;}
  if(a.length<2){help(s);return true;}Kind k;try{k=Kind.valueOf(a[1].toLowerCase(Locale.ROOT));}catch(IllegalArgumentException ex){help(s);return true;}
  String action=a[0].toLowerCase(Locale.ROOT);if(action.equals("set")&&a.length==2&&s instanceof Player p){Location l=p.getLocation();Map<String,Object> pos=new LinkedHashMap<>();pos.put("world",l.getWorld().getUID().toString());pos.put("x",l.getX());pos.put("y",l.getY());pos.put("z",l.getZ());pos.put("yaw",l.getYaw()+180);if(set(k+".location",pos)){clear(k);tick();s.sendMessage("NPC "+k+" placed here.");}return true;}
  if(action.equals("remove")&&a.length==2){if(set(k+".location",null)){clear(k);s.sendMessage("NPC removed.");}return true;}
  if(action.equals("edit")){
   if(a.length==2&&s instanceof Player p){edit(p,k);return true;}
   if(a.length==4&&k==Kind.discord&&a[2].equalsIgnoreCase("invite")){if(!a[3].matches("https://(?:discord[.]gg/|discord[.]com/invite/)[A-Za-z0-9-]+")){s.sendMessage("Use a valid HTTPS Discord invite.");return true;}if(set("discord-invite",a[3]))s.sendMessage("Discord invite saved.");return true;}
   if(a.length==5&&k==Kind.utilities&&a[2].equalsIgnoreCase("price")){try{int slot=Integer.parseInt(a[3])-1;long value=Long.parseLong(a[4]);if(slot<0||slot>=45||value<0)throw new IllegalArgumentException();if(set("shop."+slot+".price",value))s.sendMessage(value==0?"Offer disabled.":"Price saved: "+value+" shards per stack.");}catch(IllegalArgumentException ex){s.sendMessage("Use slot 1-45 and a nonnegative price. Zero disables an offer.");}return true;}
   if(a.length>=4&&List.of("title","description","color").contains(a[2])){String value=String.join(" ",Arrays.copyOfRange(a,3,a.length));if(value.length()>180||(a[2].equals("color")&&SkyWords.color(value)==null)){s.sendMessage("Use a valid color and at most 180 characters.");return true;}if(set(k+"."+a[2],value)){clear(k);tick();s.sendMessage("NPC updated.");}return true;}
  }help(s);return true;
 }
 private static void help(CommandSender s){s.sendMessage("/npc set|edit|remove <utilities|discord|races|rtp|reroll|spawners>, /npc list. /npc edit utilities price <slot 1-45> <shards>. Other edits: title, description, color followed by text.");}
 @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] a){if(!ShardRewards.admin(plugin,s))return List.of();List<String> values=a.length==1?List.of("set","edit","remove","list"):a.length==2?Arrays.stream(Kind.values()).map(Enum::name).toList():a.length==3&&a[0].equals("edit")?List.of("title","description","color","price","invite"):a.length==4&&a[2].equals("color")?SkyWords.COLORS:List.of();String prefix=a.length==0?"":a[a.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.startsWith(prefix)).toList();}
 @Override public void close(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();rewards.close();for(Kind k:Kind.values())clear(k);HandlerList.unregisterAll(this);clicks.clear();}
}
