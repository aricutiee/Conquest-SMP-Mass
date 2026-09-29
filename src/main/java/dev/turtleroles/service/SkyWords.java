package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Saved floating labels, rendered with vanilla text displays and Unicode small caps. */
public final class SkyWords implements CommandExecutor, TabCompleter, AutoCloseable {
    record Word(String id,UUID world,double x,double y,double z,String color,String text,float scale) {}
    private final TurtleRolesPlugin plugin;
    private final Path file;
    final Map<String,Word> words=new LinkedHashMap<>();
    private final Map<String,TextDisplay> displays=new HashMap<>();
    private BukkitTask task;
    static final List<String> COLORS=List.of("purple","red","pink","blue","aqua","green","yellow","gold","white","black","gray","dark_purple");
    public SkyWords(TurtleRolesPlugin plugin){this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("sky-words.yml");load();}
    public void start(){var c=Objects.requireNonNull(plugin.getCommand("setword"));c.setExecutor(this);c.setTabCompleter(this);task=Bukkit.getScheduler().runTaskTimer(plugin,this::render,1,100);}
    static TextColor color(String name){
        if(name==null)return null;
        return switch(name.toLowerCase(Locale.ROOT)) {
            case "purple" -> TextColor.color(0xB477FF);
            case "pink" -> TextColor.color(0xF08BDC);
            default -> name.matches("#[0-9a-fA-F]{6}")?TextColor.fromHexString(name):NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
        };
    }
    static Component text(Word word){return Component.text(ConquestMotd.smallCaps(word.text),color(word.color)).decorate(TextDecoration.BOLD);}
    private boolean allowed(CommandSender s){return s.hasPermission("conquest.setword.admin")||s instanceof Player p&&(p.isOp()||plugin.roleService().roleOf(p.getUniqueId()).weight()>=Role.ADMIN.weight());}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){
        if(!allowed(s)){s.sendMessage("Only administrators can manage floating words.");return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("list")){
            if(words.isEmpty())s.sendMessage("No floating words have been created.");
            for(Word w:words.values())s.sendMessage(w.text+" ("+w.color+", size "+w.scale+")");return true;
        }
        if(args.length>=2&&Set.of("remove","move","size","edit").contains(args[0].toLowerCase(Locale.ROOT))){
            String action=args[0].toLowerCase(Locale.ROOT);
            args=resolve(args);Word old=words.get(args[1]);
            if(old==null){s.sendMessage("Unknown text. Use /setword list, then type the words exactly as shown.");return true;}
            Word replacement=old;
            if(action.equals("remove")&&args.length==2)replacement=null;
            else if(action.equals("move")&&args.length==2&&s instanceof Player p){Location l=anchor(p);replacement=new Word(old.id,l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),old.color,old.text,old.scale);}
            else if(action.equals("size")&&args.length==3){
                float size;try{size=Float.parseFloat(args[2]);}catch(NumberFormatException ex){size=Float.NaN;}
                if(!Float.isFinite(size)||size<1||size>50){s.sendMessage("Size must be between 1 and 50.");return true;}
                replacement=new Word(old.id,old.world,old.x,old.y,old.z,old.color,old.text,size);
            }else if(action.equals("edit")&&args.length>=4){
                String content=String.join(" ",Arrays.copyOfRange(args,3,args.length));
                if(!valid(s,args[2],content))return true;
                if(words.values().stream().anyMatch(w->!w.id.equals(old.id)&&w.text.equalsIgnoreCase(content))){s.sendMessage("That text already exists.");return true;}
                replacement=new Word(old.id,old.world,old.x,old.y,old.z,args[2].toLowerCase(Locale.ROOT),content,old.scale);
            }else {help(s);return true;}
            if(change(old.id,replacement)){s.sendMessage("Floating text updated: "+(replacement==null?old.text:replacement.text));}else s.sendMessage("Could not save the change. Nothing was changed.");
            return true;
        }
        if(!(s instanceof Player p)||args.length<2){help(s);return true;}
        String content=String.join(" ",Arrays.copyOfRange(args,1,args.length));if(!valid(s,args[0],content))return true;
        if(words.values().stream().anyMatch(w->w.text.equalsIgnoreCase(content))){s.sendMessage("That text already exists. Edit or move it instead.");return true;}
        if(words.size()>=200){s.sendMessage("The 200-word limit has been reached. Remove an old word first.");return true;}
        int n=1;while(words.containsKey("word"+n))n++;String id="word"+n;Location l=anchor(p);
        Word word=new Word(id,l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),args[0].toLowerCase(Locale.ROOT),content,4);
        if(change(id,word))s.sendMessage("Created "+content+" above you. /setword move "+content+", /setword size "+content+" <1-50>");
        else s.sendMessage("Could not save the word. Nothing was created.");return true;
    }
    private String[] resolve(String[] args){
        for(int end=args.length;end>=2;end--){String name=String.join(" ",Arrays.copyOfRange(args,1,end));for(Word word:words.values())if(word.text.equalsIgnoreCase(name)){String[] result=new String[args.length-end+2];result[0]=args[0];result[1]=word.id;System.arraycopy(args,end,result,2,args.length-end);return result;}}return args;
    }
    private static Location anchor(Player p){return p.getEyeLocation().clone().add(0,1,0);}
    private static boolean valid(CommandSender s,String color,String text){
        if(color(color)==null){s.sendMessage("Choose a color such as purple, red, pink or #B477FF.");return false;}
        if(text.isBlank()||text.codePointCount(0,text.length())>100||text.codePoints().anyMatch(Character::isISOControl)){s.sendMessage("Use 1 to 100 visible characters.");return false;}return true;
    }
    private static void help(CommandSender s){s.sendMessage("/setword <color> <text> | list | move <existing text> | remove <existing text> | size <existing text> <1-50> | edit <existing text> <color> <new text>");}
    boolean change(String id,Word value){
        Word old=words.get(id);if(value==null)words.remove(id);else words.put(id,value);
        if(!save()){if(old==null)words.remove(id);else words.put(id,old);return false;}
        TextDisplay display=displays.remove(id);if(display!=null)display.remove();render();return true;
    }
    private void render(){
        for(Word w:words.values()){
            World world=Bukkit.getWorld(w.world);TextDisplay old=displays.get(w.id);
            boolean loaded=world!=null&&world.isChunkLoaded(((int)Math.floor(w.x))>>4,((int)Math.floor(w.z))>>4);
            if(!loaded){if(old!=null){old.remove();displays.remove(w.id);}continue;}
            if(old!=null&&old.isValid())continue;
            TextDisplay display=world.spawn(new Location(world,w.x,w.y,w.z),TextDisplay.class,e->{
                e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);
                e.setBillboard(Display.Billboard.CENTER);e.setBrightness(new Display.Brightness(15,15));e.setViewRange(2);
                e.setSeeThrough(false);e.setShadowed(true);e.setDefaultBackground(false);e.setBackgroundColor(Color.fromARGB(0));
                e.setAlignment(TextDisplay.TextAlignment.CENTER);e.setLineWidth(2000);e.text(text(w));
                e.setTransformationMatrix(new Matrix4f().scaling(w.scale));
            });displays.put(w.id,display);
        }
    }
    private void load(){
        if(!Files.exists(file))return;
        YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file.toFile());
        for(String id:yaml.getKeys(false))try{
            String key=id+".";String content=yaml.getString(key+"text","");String tint=yaml.getString(key+"color","purple");
            float scale=(float)yaml.getDouble(key+"size",4);double x=yaml.getDouble(key+"x"),y=yaml.getDouble(key+"y"),z=yaml.getDouble(key+"z");
            if(!id.matches("word[0-9]+")||color(tint)==null||content.isBlank()||content.length()>200||!Float.isFinite(scale)||scale<1||scale>50||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException();
            words.put(id,new Word(id,UUID.fromString(yaml.getString(key+"world","")),x,y,z,tint,content,scale));
        }catch(IllegalArgumentException ex){plugin.getLogger().warning("Skipped invalid sky word: "+id);}
    }
    private boolean save(){
        YamlConfiguration yaml=new YamlConfiguration();for(Word w:words.values()){
            String k=w.id+".";yaml.set(k+"world",w.world.toString());yaml.set(k+"x",w.x);yaml.set(k+"y",w.y);yaml.set(k+"z",w.z);yaml.set(k+"color",w.color);yaml.set(k+"text",w.text);yaml.set(k+"size",w.scale);
        }
        try{Files.createDirectories(file.getParent());Path temp=file.resolveSibling("sky-words.yml.tmp");yaml.save(temp.toFile());
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}return true;
        }catch(IOException ex){plugin.getLogger().warning("Could not save sky words: "+ex.getMessage());return false;}
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args){
        if(!allowed(s))return List.of();List<String> options=new ArrayList<>();
        if(args.length==1){options.addAll(COLORS);options.addAll(List.of("list","move","remove","size","edit"));}
        else if(args.length>=2&&Set.of("move","remove","size","edit").contains(args[0].toLowerCase(Locale.ROOT))){
            String typed=String.join(" ",Arrays.copyOfRange(args,1,args.length)).toLowerCase(Locale.ROOT);
            int preceding=args.length<=2?0:String.join(" ",Arrays.copyOfRange(args,1,args.length-1)).length()+1;
            List<String> names=words.values().stream().map(Word::text).filter(t->t.toLowerCase(Locale.ROOT).startsWith(typed)).map(t->t.substring(Math.min(preceding,t.length()))).toList();
            if(!names.isEmpty())return names;
            String[] resolved=resolve(args);
            if(resolved.length==3&&words.containsKey(resolved[1])){
                if(args[0].equalsIgnoreCase("edit"))options.addAll(COLORS);
                if(args[0].equalsIgnoreCase("size"))options.addAll(List.of("1","4","8","16","32","50"));
            }
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    @Override public void close(){if(task!=null)task.cancel();displays.values().forEach(Entity::remove);displays.clear();}
}
