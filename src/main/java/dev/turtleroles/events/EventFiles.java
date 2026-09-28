package dev.turtleroles.events;
import java.nio.file.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;
final class EventFiles {
    private EventFiles(){}
    static void save(File file,YamlConfiguration data) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Path temp=file.toPath().resolveSibling(file.getName()+".tmp");
        Files.writeString(temp,data.saveToString());
        try {Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
}
