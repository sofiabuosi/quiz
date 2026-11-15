package pitiupi.plugins.remote;

import java.util.List;
import java.util.Optional;
import pitiupi.plugins.store.InstalledPluginRef;


public final class PluginStatus {
    public enum State { NOT_INSTALLED, UP_TO_DATE, UPDATE_AVAILABLE, UNKNOWN_VERSION }

    private PluginStatus() { }

    /** Casa pelo id remoto contra arquivoId, identifier ou nome do arquivo (sem ".jar"/"pitiupi."). */
    public static Optional<InstalledPluginRef> findInstalled(List<InstalledPluginRef> installed, String remoteId) {
        for (InstalledPluginRef ref : installed) {
            String stem = ref.getFileName().replaceFirst("\\.jar$", "").replaceFirst("^pitiupi\\.", "");
            if (remoteId.equalsIgnoreCase(ref.getArquivoId().orElse(""))
                    || remoteId.equalsIgnoreCase(ref.getIdentifier().orElse(""))
                    || remoteId.equalsIgnoreCase(stem)) {
                return Optional.of(ref);
            }
        }
        return Optional.empty();
    }

    public static State of(RemotePluginEntry remote, Optional<InstalledPluginRef> installed) {
        if (installed.isEmpty()) {
            return State.NOT_INSTALLED;
        }
        Optional<String> v = installed.get().getVersion();
        if (v.isEmpty()) {
            return State.UNKNOWN_VERSION;   // JAR nunca carregado: sem .plugin.meta ainda
        }
        return VersionCompare.compare(remote.version(), v.get()) > 0
                ? State.UPDATE_AVAILABLE : State.UP_TO_DATE;
    }
}
