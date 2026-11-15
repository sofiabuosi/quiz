package pitiupi.plugins.remote;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pitiupi.errors.ErrorCode;
import pitiupi.errors.PitiupiException;
import pitiupi.plugins.store.PluginMetadataWriter;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginServerTest {

    @TempDir
    Path temp;

    private PluginServer server;
    private URI base;
    private Path chatJar;
    private final PluginRepositoryClient client = new PluginRepositoryClient();

    @BeforeEach
    void setUp() throws Exception {
        Path shared = temp.resolve("shared");
        Files.createDirectories(shared);

        chatJar = shared.resolve("chat.jar");
        Files.writeString(chatJar, "conteudo-do-jar");
        Properties meta = new Properties();
        meta.setProperty("arquivoId", "chat");
        meta.setProperty("displayName", "Chat");
        meta.setProperty("version", "1.2.0");
        meta.setProperty("author", "Fulano");
        meta.setProperty("description", "Chat de teste");
        try (OutputStream out = Files.newOutputStream(PluginMetadataWriter.metaPathFor(chatJar))) {
            meta.store(out, null);
        }
        Files.writeString(shared.resolve("extra.jar"), "outro");        // sem .plugin.meta
        Files.writeString(shared.resolve("notas.txt"), "nao e jar");   // não deve aparecer

        server = new PluginServer(shared);
        server.start(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0);
        base = URI.create("http://127.0.0.1:" + server.getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private int status(String path, String method) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void listShowsOnlyJarsAndReadsMetadata() throws Exception {
        List<RemotePluginEntry> list = client.fetchList(base, PluginListFormat.FILE_NAME);
        assertEquals(2, list.size());

        RemotePluginEntry chat = list.get(0);
        assertEquals("chat", chat.id());
        assertEquals("Chat", chat.name());
        assertEquals("1.2.0", chat.version());
        assertEquals("Fulano", chat.author());
        assertEquals(Sha256.of(chatJar), chat.sha256());

        RemotePluginEntry extra = list.get(1);
        assertEquals("extra", extra.id());
        assertEquals("0", extra.version());
    }

    @Test
    void downloadDeliversTheSameBytes() throws Exception {
        RemotePluginEntry chat = client.fetchList(base, PluginListFormat.FILE_NAME).get(0);
        Path downloaded = client.download(base, chat, temp.resolve("cache"));
        assertEquals("conteudo-do-jar", Files.readString(downloaded));
    }

    @Test
    void wrongHashIsRejectedAndNothingIsLeftBehind() throws Exception {
        RemotePluginEntry bad = new RemotePluginEntry(
                "chat", "Chat", "1", "", "", "chat.jar", "0".repeat(64));
        Path cache = temp.resolve("cache");

        PitiupiException ex = assertThrows(PitiupiException.class, () -> client.download(base, bad, cache));
        assertEquals(ErrorCode.PLUGIN_HASH_MISMATCH, ex.getCode());
        try (Stream<Path> s = Files.list(cache)) {
            assertEquals(0, s.count());
        }
    }

    @Test
    void onlyListedJarsAreServed() throws Exception {
        assertEquals(200, status("chat.jar", "GET"));
        assertEquals(404, status("nao-existe.jar", "GET"));
        assertEquals(404, status("notas.txt", "GET"));
        assertEquals(404, status("..%2Fconfig.properties", "GET"));
        assertEquals(404, status("..%2F..%2Fetc%2Fpasswd", "GET"));
    }

    @Test
    void onlyGetIsAllowed() throws Exception {
        assertEquals(405, status("chat.jar", "POST"));
    }
}
