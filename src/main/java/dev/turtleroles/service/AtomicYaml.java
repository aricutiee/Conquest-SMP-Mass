package dev.turtleroles.service;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.IOException;
final class AtomicYaml {
 static void save(YamlConfiguration data,Path file)throws IOException{Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");data.save(tmp.toFile());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
}
