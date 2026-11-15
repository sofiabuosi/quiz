package pitiupi.plugins.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import pitiupi.errors.ErrorCode;
import pitiupi.errors.PitiupiException;

public final class PluginRepositoryClient {
    private static final long MAX_JAR_BYTES = 50L * 1024 * 1024;
    private static final long MAX_LIST_BYTES = 256L * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** "192.168.0.10:9090" vira "http://192.168.0.10:9090/". */
    public static URI normalize(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Informe o endereço do servidor.");
        }
        if (!s.contains("://")) {
            s = "http://" + s;
        }
        URI uri = URI.create(s);
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
            throw new IllegalArgumentException("Endereço inválido: " + input);
        }
        return URI.create(s.endsWith("/") ? s : s + "/");
    }

    public List<RemotePluginEntry> fetchList(URI base, String listName) throws PitiupiException {
        try {
            HttpRequest req = HttpRequest.newBuilder(base.resolve(listName))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new PitiupiException(ErrorCode.PLUGIN_SERVER_UNREACHABLE, "HTTP " + res.statusCode());
            }
            if (res.body().length > MAX_LIST_BYTES) {
                throw new PitiupiException(ErrorCode.PLUGIN_LIST_INVALID);
            }
            return PluginListFormat.parse(new String(res.body(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PitiupiException(ErrorCode.PLUGIN_SERVER_UNREACHABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PitiupiException(ErrorCode.PLUGIN_SERVER_UNREACHABLE, e);
        }
    }

    /** Baixa para um arquivo temporário em cacheDir, conferindo tamanho e SHA-256. */
    public Path download(URI base, RemotePluginEntry entry, Path cacheDir) throws PitiupiException {
        Path tmp = null;
        try {
            Files.createDirectories(cacheDir);
            HttpRequest req = HttpRequest.newBuilder(base.resolve(entry.file()))
                    .timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                res.body().close();
                throw new PitiupiException(ErrorCode.PLUGIN_DOWNLOAD_FAILED, "HTTP " + res.statusCode());
            }
            tmp = Files.createTempFile(cacheDir, "dl-", ".part");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_JAR_BYTES) {
                        throw new PitiupiException(ErrorCode.PLUGIN_DOWNLOAD_FAILED, "Arquivo grande demais");
                    }
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            if (!HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(entry.sha256())) {
                throw new PitiupiException(ErrorCode.PLUGIN_HASH_MISMATCH);
            }
            return tmp;
        } catch (PitiupiException e) {
            deleteQuietly(tmp);
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteQuietly(tmp);
            throw new PitiupiException(ErrorCode.PLUGIN_DOWNLOAD_FAILED, e);
        } catch (InterruptedException e) {
            deleteQuietly(tmp);
            Thread.currentThread().interrupt();
            throw new PitiupiException(ErrorCode.PLUGIN_DOWNLOAD_FAILED, e);
        }
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
        }
    }
}
