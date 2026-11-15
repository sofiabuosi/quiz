package pitiupi.plugins.remote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import pitiupi.errors.ErrorCode;
import pitiupi.errors.PitiupiException;
import pitiupi.platform.AppVersion;
import pitiupi.plugins.store.PluginMetadataWriter;

public final class PluginServer {

    private final Path sharedDir;
    private HttpServer server;
    private ExecutorService pool;

    public PluginServer(Path sharedDir) {
        this.sharedDir = sharedDir;
    }

    public synchronized void start(InetAddress bind, int port) throws PitiupiException {
        if (server != null) {
            return;
        }
        try {
            // bind == null -> todas as interfaces
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            pool = Executors.newFixedThreadPool(4);
            server.setExecutor(pool);
            server.createContext("/", this::handle);
            server.start();
        } catch (IOException e) {
            server = null;
            throw new PitiupiException(ErrorCode.PLUGIN_SERVER_START_FAILED, e);
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            pool.shutdownNow();
            server = null;
        }
    }

    public synchronized boolean isRunning() { return server != null; }
    public synchronized int getPort() { return server.getAddress().getPort(); }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!"GET".equals(ex.getRequestMethod())) {
                reply(ex, 405, "text/plain", new byte[0]);
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (("/" + PluginListFormat.FILE_NAME).equals(path)) {
                byte[] body = PluginListFormat.format(buildList()).getBytes(StandardCharsets.UTF_8);
                reply(ex, 200, "text/plain; charset=utf-8", body);
            } else if (("/" + PluginListFormat.CORE_FILE_NAME).equals(path)) {
                reply(ex, 200, "text/plain; charset=utf-8", coreList().getBytes(StandardCharsets.UTF_8));
            } else {
                Path jar = "/pitiupi.jar".equals(path) ? AppVersion.runningJar().orElse(null)
                        : findShared(path.substring(1));
                if (jar == null) {
                    reply(ex, 404, "text/plain", new byte[0]);
                    return;
                }
                ex.getResponseHeaders().set("Content-Type", "application/java-archive");
                ex.sendResponseHeaders(200, Files.size(jar));
                try (OutputStream out = ex.getResponseBody()) {
                    Files.copy(jar, out);
                }
            }
        } catch (IOException e) {
            // cliente caiu no meio ou erro de leitura: a conexão é fechada pelo try-with-resources
        }
    }

    /** Só serve arquivos que estão na pasta: o nome do pedido nunca vira caminho (sem path traversal). */
    private Path findShared(String name) throws IOException {
        if (!PluginListFormat.isSafeJarName(name)) {
            return null;
        }
        try (Stream<Path> s = Files.list(sharedDir)) {
            return s.filter(p -> p.getFileName().toString().equals(name))
                    .filter(Files::isRegularFile).findFirst().orElse(null);
        }
    }

    List<RemotePluginEntry> buildList() throws IOException {
        List<RemotePluginEntry> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(sharedDir)) {
            for (Path jar : s.filter(p -> PluginListFormat.isSafeJarName(p.getFileName().toString()))
                    .sorted().toList()) {
                String file = jar.getFileName().toString();
                String stem = file.substring(0, file.length() - 4);
                Properties meta = readMeta(jar);
                out.add(new RemotePluginEntry(
                        meta.getProperty("arquivoId", stem),
                        meta.getProperty("displayName", meta.getProperty("name", stem)),
                        meta.getProperty("version", "0"),
                        meta.getProperty("author", ""),
                        meta.getProperty("description", ""),
                        file,
                        Sha256.of(jar)));
            }
        }
        return out;
    }

    private String coreList() throws IOException {
        Optional<Path> jar = AppVersion.runningJar();
        if (jar.isEmpty()) {
            return "";
        }
        return PluginListFormat.format(List.of(new RemotePluginEntry(
                "pitiupi", "Pitiupi", AppVersion.current(), "", "", "pitiupi.jar", Sha256.of(jar.get()))));
    }

    private static Properties readMeta(Path jar) {
        Properties p = new Properties();
        Path meta = PluginMetadataWriter.metaPathFor(jar);
        if (Files.isRegularFile(meta)) {
            try (InputStream in = Files.newInputStream(meta)) {
                p.load(in);
            } catch (IOException ignored) { }
        }
        return p;
    }

    private static void reply(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
