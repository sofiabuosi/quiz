package pitiupi.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class AppVersion {
    private AppVersion() { }

    public static String current() {
        String v = AppVersion.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.0.0-dev";
    }

    /** O JAR em execução, ou vazio se estiver rodando de pasta de classes (IDE). */
    public static Optional<Path> runningJar() {
        try {
            Path p = Path.of(AppVersion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.toString().endsWith(".jar") ? Optional.of(p) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
