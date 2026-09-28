package dev.turtleroles.migration;
import java.io.IOException;
import java.nio.file.*;

/** Cold-start copy: old data stays intact, and incomplete copies never become live. */
public final class LegacyDataMigration {
    private LegacyDataMigration() {}
    public static boolean copy(Path current) throws IOException {
        if (Files.exists(current)) return false;
        Path legacy = current.resolveSibling("shockSMP");
        if (!Files.isDirectory(legacy)) legacy = current.resolveSibling("TurtleRoles");
        if (!Files.isDirectory(legacy)) return false;
        Path staging = current.resolveSibling(current.getFileName() + ".migration-" + java.util.UUID.randomUUID());
        try (var paths = Files.walk(legacy)) {
            for (Path source : paths.toList()) {
                Path target = staging.resolve(legacy.relativize(source));
                if (Files.isSymbolicLink(source)) throw new IOException("Refusing a symlink in legacy plugin data: " + source);
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        Files.move(staging, current, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }
}
