package dev.turtleroles.service;

import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Stages hash-verified official Geyser/Floodgate releases for the next normal restart. */
public final class BedrockUpdater implements AutoCloseable {
    private final JavaPlugin plugin;
    private BukkitTask task;
    private volatile boolean closed;
    public BedrockUpdater(JavaPlugin plugin){this.plugin=plugin;}
    public void start(){
        if(!plugin.getConfig().getBoolean("bedrock-updates.enabled",true))return;
        task=plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,()->{
            for(String project:new String[]{"geyser","floodgate"}) {
                if(closed)return;
                try { check(project); }
                catch(Exception ex){plugin.getLogger().warning("Bedrock update check for "+project+" failed; installed version retained: "+ex.getMessage());}
            }
        },2400,6*60*60*20L);
    }
    static URI downloadUri(String project,String version,int build) {
        if(!project.equals("geyser")&&!project.equals("floodgate"))throw new IllegalArgumentException("Unknown project");
        if(!version.matches("[A-Za-z0-9._-]{1,60}")||build<1)throw new IllegalArgumentException("Invalid release");
        return URI.create("https://download.geysermc.org/v2/projects/"+project+"/versions/"+version+"/builds/"+build+"/downloads/spigot");
    }
    static String sha(Path file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}
        return HexFormat.of().formatHex(digest.digest());
    }
    static URI officialRedirect(URI from,String location) throws java.io.IOException {
        URI next=from.resolve(location);
        if(!"https".equals(next.getScheme()) || !"download.geysermc.org".equals(next.getHost()) || next.getUserInfo()!=null || (next.getPort()!=-1 && next.getPort()!=443))
            throw new java.io.IOException("Redirect left the official HTTPS download service");
        return next;
    }
    static com.google.gson.JsonObject metadata(HttpClient client,URI uri) throws Exception {
        for(int redirects=0;redirects<5;redirects++) {
            var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent","ConquestSMP-BedrockUpdater/3.5.0").GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==200)return JsonParser.parseString(response.body()).getAsJsonObject();
            if(java.util.Set.of(301,302,303,307,308).contains(response.statusCode())) {
                uri=officialRedirect(uri,response.headers().firstValue("Location").orElseThrow(()->new java.io.IOException("Redirect missing Location")));
            } else throw new java.io.IOException("Metadata HTTP "+response.statusCode());
        }
        throw new java.io.IOException("Too many metadata redirects");
    }
    private void check(String project) throws Exception {
        String filename=project.equals("geyser")?"Geyser-Spigot.jar":"floodgate-spigot.jar";
        Path plugins=plugin.getDataFolder().toPath().getParent();
        Path installed=plugins.resolve(filename);
        if(!Files.isRegularFile(installed))return;
        try(HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            URI metadata=URI.create("https://download.geysermc.org/v2/projects/"+project+"/versions/latest/builds/latest");
            var release=metadata(client,metadata);
            String version=release.get("version").getAsString();int build=release.get("build").getAsInt();
            String expected=release.getAsJsonObject("downloads").getAsJsonObject("spigot").get("sha256").getAsString();
            if(!expected.matches("[a-fA-F0-9]{64}"))throw new java.io.IOException("Invalid checksum");
            if(sha(installed).equalsIgnoreCase(expected)) {
                plugin.getLogger().info("Verified official "+project+" "+version+" build "+build+": already installed.");return;
            }
            Path update=plugin.getServer().getUpdateFolderFile().toPath();Files.createDirectories(update);
            Path staged=update.resolve(filename);
            if(Files.exists(staged)&&sha(staged).equalsIgnoreCase(expected))return;
            Path temp=Files.createTempFile(update,"conquest-", ".download");
            try {
                var downloaded=client.send(HttpRequest.newBuilder(downloadUri(project,version,build)).timeout(Duration.ofMinutes(3)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var input=downloaded.body();var output=Files.newOutputStream(temp)) {
                    if(downloaded.statusCode()!=200)throw new java.io.IOException("Download HTTP "+downloaded.statusCode());
                    byte[] buffer=new byte[65536];long total=0;int n;
                    while((n=input.read(buffer))!=-1){total+=n;if(total>160_000_000)throw new java.io.IOException("Oversized release");output.write(buffer,0,n);}
                }
                if(!sha(temp).equalsIgnoreCase(expected))throw new java.io.IOException("Checksum mismatch");
                try(JarFile jar=new JarFile(temp.toFile())){if(jar.getJarEntry("plugin.yml")==null)throw new java.io.IOException("Not a Bukkit plugin");}
                if(closed)return;
                Path backup=plugin.getDataFolder().toPath().resolve("addon-backups");Files.createDirectories(backup);
                Path previous=backup.resolve(filename+"."+sha(installed)+".backup");
                if(!Files.exists(previous))Files.copy(installed,previous);
                Files.move(temp,staged,StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Staged official "+project+" "+version+" build "+build+" for the next normal restart. No players were disconnected.");
            } finally {Files.deleteIfExists(temp);}
        }
    }
    @Override public void close(){closed=true;if(task!=null)task.cancel();}
}
