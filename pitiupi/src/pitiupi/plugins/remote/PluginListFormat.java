package pitiupi.plugins.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PluginListFormat {
    public static final String FILE_NAME = "lista-de-plugins.txt";
    public static final String CORE_FILE_NAME = "atualizacao-pitiupi.txt";
    private static final Pattern SAFE_JAR = Pattern.compile("[A-Za-z0-9._-]+\\.jar");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private PluginListFormat() { }

    public static boolean isSafeJarName(String name) {
        return name != null && SAFE_JAR.matcher(name).matches();
    }

    public static List<RemotePluginEntry> parse(String text) {
        List<RemotePluginEntry> out = new ArrayList<>();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        for (String raw : text.split("\\R")) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) {
                continue;
            }
            String[] c = raw.split("\t", -1);
            if (c.length < 7) {
                continue;
            }
            String id = c[0].trim(), file = c[5].trim(), sha = c[6].trim();
            if (!SAFE_ID.matcher(id).matches() || !isSafeJarName(file)
                    || !SHA256.matcher(sha).matches()) {
                continue;   // linha ruim não derruba a lista inteira
            }
            out.add(new RemotePluginEntry(id, c[1].trim(), c[2].trim(), c[3].trim(),
                    c[4].trim(), file, sha));
        }
        return out;
    }

    public static String format(List<RemotePluginEntry> entries) {
        StringBuilder sb = new StringBuilder("# id\tnome\tversao\tautor\tdescricao\tarquivo\tsha256\n");
        for (RemotePluginEntry e : entries) {
            sb.append(String.join("\t", clean(e.id()), clean(e.name()), clean(e.version()),
                    clean(e.author()), clean(e.description()), e.file(), e.sha256())).append('\n');
        }
        return sb.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ").trim();
    }
}
