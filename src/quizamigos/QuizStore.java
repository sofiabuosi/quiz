package quizamigos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

final class QuizStore {

    private final Path directory;
    private final Path playerIdFile;
    private final Path historyFile;
    private final Properties history = new Properties();
    private final String playerId;

    QuizStore() {
        this(Path.of(System.getProperty("user.home"), ".pitiupi", "quizamigos"));
    }

    QuizStore(Path directory) {
        this.directory = directory;
        this.playerIdFile = directory.resolve("player.id");
        this.historyFile = directory.resolve("history.properties");
        this.playerId = loadOrCreatePlayerId();
        loadHistory();
    }

    String getPlayerId() { return playerId; }

    synchronized void completeGame(
            String gameId, String[] playerIds, String[] playerNames, int[] playerScores) {
        if (gameId == null || gameId.isBlank() || history.getProperty("game." + gameId) != null) {
            return;
        }
        int count = Math.min(playerIds == null ? 0 : playerIds.length,
                Math.min(playerNames == null ? 0 : playerNames.length,
                        playerScores == null ? 0 : playerScores.length));
        if (count == 0) {
            history.setProperty("game." + gameId, "done");
            saveHistory();
            return;
        }

        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (playerIds[i] != null && !playerIds[i].isBlank()) {
                highest = Math.max(highest, playerScores[i]);
            }
        }
        for (int i = 0; i < count; i++) {
            String id = playerIds[i];
            if (id == null || id.isBlank()) continue;
            String prefix = playerKey(id);
            String oldName = history.getProperty(prefix + ".name", "Jogador");
            String newName = playerNames[i] == null || playerNames[i].isBlank()
                    ? oldName : playerNames[i].trim();
            history.setProperty(prefix + ".name", newName);
            history.setProperty(prefix + ".points", Integer.toString(
                    intProperty(prefix + ".points") + Math.max(0, playerScores[i])));
            history.setProperty(prefix + ".games", Integer.toString(intProperty(prefix + ".games") + 1));
            boolean winner = playerScores[i] == highest && highest > 0;
            history.setProperty(prefix + ".wins", Integer.toString(
                    intProperty(prefix + ".wins") + (winner ? 1 : 0)));
        }
        history.setProperty("game." + gameId, "done");
        saveHistory();
    }

    synchronized List<PlayerStats> getRanking() {
        List<PlayerStats> rows = new ArrayList<>();
        for (String key : history.stringPropertyNames()) {
            if (!key.startsWith("player.") || !key.endsWith(".name")) continue;
            String prefix = key.substring(0, key.length() - ".name".length());
            String encodedId = prefix.substring("player.".length());
            String id;
            try {
                id = new String(Base64.getUrlDecoder().decode(encodedId), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            rows.add(new PlayerStats(
                    id,
                    history.getProperty(prefix + ".name", "Jogador"),
                    intProperty(prefix + ".points"),
                    intProperty(prefix + ".games"),
                    intProperty(prefix + ".wins")));
        }
        rows.sort(Comparator.comparingInt(PlayerStats::wins).reversed()
                .thenComparing(Comparator.comparingInt(PlayerStats::points).reversed())
                .thenComparing(PlayerStats::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private String loadOrCreatePlayerId() {
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(playerIdFile)) {
                String value = Files.readString(playerIdFile).trim();
                if (!value.isEmpty()) return value;
            }
            String value = UUID.randomUUID().toString();
            Files.writeString(playerIdFile, value);
            return value;
        } catch (IOException ex) {
            return UUID.randomUUID().toString();
        }
    }

    private void loadHistory() {
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(historyFile)) {
                try (InputStream input = Files.newInputStream(historyFile)) {
                    history.load(input);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private int intProperty(String key) {
        try { return Integer.parseInt(history.getProperty(key, "0")); }
        catch (NumberFormatException ex) { return 0; }
    }

    private String playerKey(String id) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "player." + encoded;
    }

    private void saveHistory() {
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve("history.properties.tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                history.store(output, "Histórico local do Quiz entre amigos");
            }
            try {
                Files.move(temp, historyFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(temp, historyFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    record PlayerStats(String id, String name, int points, int games, int wins) { }
}
