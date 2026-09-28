package me.jade.ariServerUtil.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/** Keeps the original ServerUtil files usable without migration or overwrites. */
public final class ModuleFiles {
    private ModuleFiles() { }

    public static void installDefaults(Path folder, Function<String, InputStream> resources) throws IOException {
        Files.createDirectories(folder);
        for (String name : new String[]{"config.yml", "messages.yml"}) {
            Path destination = folder.resolve(name);
            if (Files.exists(destination)) continue;
            try (InputStream input = resources.apply("serverutil/" + name)) {
                if (input == null) throw new IOException("Missing bundled ServerUtil resource: " + name);
                Files.copy(input, destination);
            }
        }
    }
}
