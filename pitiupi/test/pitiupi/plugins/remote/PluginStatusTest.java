package pitiupi.plugins.remote;

import org.junit.jupiter.api.Test;
import pitiupi.plugins.store.InstalledPluginRef;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginStatusTest {

    private static RemotePluginEntry remote(String id, String version) {
        return new RemotePluginEntry(id, id, version, "", "", id + ".jar", "a".repeat(64));
    }

    private static InstalledPluginRef installed(String file, String arquivoId, String version) {
        return new InstalledPluginRef(Path.of("/x/" + file), file,
                Optional.ofNullable(arquivoId), Optional.ofNullable(version),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void matchesByFileNameWithoutPrefix() {
        List<InstalledPluginRef> list = List.of(installed("pitiupi.chat.jar", null, "1.0"));
        assertTrue(PluginStatus.findInstalled(list, "chat").isPresent());
    }

    @Test
    void matchesByArquivoId() {
        List<InstalledPluginRef> list = List.of(installed("chat.jar", "chat", "1.0"));
        assertTrue(PluginStatus.findInstalled(list, "chat").isPresent());
    }

    @Test
    void notInstalled() {
        assertEquals(PluginStatus.State.NOT_INSTALLED,
                PluginStatus.of(remote("chat", "1.0"), Optional.empty()));
    }

    @Test
    void updateAvailableWhenRemoteIsNewer() {
        Optional<InstalledPluginRef> inst = Optional.of(installed("chat.jar", "chat", "1.9.0"));
        assertEquals(PluginStatus.State.UPDATE_AVAILABLE, PluginStatus.of(remote("chat", "1.10.0"), inst));
    }

    @Test
    void upToDateWhenSameOrOlder() {
        Optional<InstalledPluginRef> inst = Optional.of(installed("chat.jar", "chat", "2.0"));
        assertEquals(PluginStatus.State.UP_TO_DATE, PluginStatus.of(remote("chat", "2.0"), inst));
        assertEquals(PluginStatus.State.UP_TO_DATE, PluginStatus.of(remote("chat", "1.0"), inst));
    }

    @Test
    void unknownVersionWhenThereIsNoMetadata() {
        Optional<InstalledPluginRef> inst = Optional.of(installed("chat.jar", null, null));
        assertEquals(PluginStatus.State.UNKNOWN_VERSION, PluginStatus.of(remote("chat", "1.0"), inst));
    }
}
