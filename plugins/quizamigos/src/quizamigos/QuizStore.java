package quizamigos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
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
    private String storageError = "";

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

    synchronized String getStorageError() { return storageError; }

    synchronized boolean completeGame(
            String gameId, String[] playerIds, String[] playerNames, int[] playerScores) {
        if (gameId == null || gameId.isBlank()) {
            storageError = "Identificador da partida inválido; histórico não foi atualizado.";
            return false;
        }
        if (history.getProperty("game." + gameId) != null) return true;

        int count = Math.min(playerIds == null ? 0 : playerIds.length,
                Math.min(playerNames == null ? 0 : playerNames.length,
                        playerScores == null ? 0 : playerScores.length));
        Properties previous = copyHistory();
        if (count == 0) {
            history.setProperty("game." + gameId, "done");
            if (!saveHistory()) {
                restoreHistory(previous);
                return false;
            }
            return true;
        }

        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (playerIds[i] != null && !playerIds[i].isBlank()) {
                highest = Math.max(highest, Math.max(0, playerScores[i]));
            }
        }
        for (int i = 0; i < count; i++) {
            String id = playerIds[i];
            if (id == null || id.isBlank()) continue;
            String prefix = playerKey(id);
            String oldName = history.getProperty(prefix + ".name", "Jogador");
            String newName = playerNames[i] == null || playerNames[i].isBlank()
                    ? oldName : playerNames[i].trim();
            int score = Math.max(0, playerScores[i]);
            history.setProperty(prefix + ".name", newName);
            history.setProperty(prefix + ".points", Integer.toString(
                    intProperty(prefix + ".points") + score));
            history.setProperty(prefix + ".games", Integer.toString(intProperty(prefix + ".games") + 1));
            boolean winner = score == highest && highest > 0;
            history.setProperty(prefix + ".wins", Integer.toString(
                    intProperty(prefix + ".wins") + (winner ? 1 : 0)));
        }
        history.setProperty("game." + gameId, "done");
        if (!saveHistory()) {
            restoreHistory(previous);
            return false;
        }
        return true;
    }

    synchronized List<PlayerStats> getRanking() {
        List<PlayerStats> rows = new ArrayList<>();
        for (String key : history.stringPropertyNames()) {
            if (!key.startsWith("player.") || !key.endsWith(".name")) continue;
            String prefix = key.substring(0, key.length() - ".name".length());
            String encodedId = prefix.substring("player.".length());
            String id;
            try {
                id = new String(Base64.getUrlDecoder().decode(encodedId), StandardCharsets.UTF_8);
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
            storageError = "Não foi possível criar o identificador local: " + ex.getMessage();
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
        } catch (IOException | IllegalArgumentException ex) {
            storageError = "Não foi possível ler o histórico local: " + ex.getMessage();
        }
    }

    private int intProperty(String key) {
        try { return Integer.parseInt(history.getProperty(key, "0")); }
        catch (NumberFormatException ex) { return 0; }
    }

    private String playerKey(String id) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                id.getBytes(StandardCharsets.UTF_8));
        return "player." + encoded;
    }

    private Properties copyHistory() {
        Properties copy = new Properties();
        copy.putAll(history);
        return copy;
    }

    private void restoreHistory(Properties previous) {
        history.clear();
        history.putAll(previous);
    }

    private boolean saveHistory() {
        Path temp = directory.resolve("history.properties.tmp");
        try {
            Files.createDirectories(directory);
            try (OutputStream output = Files.newOutputStream(temp)) {
                history.store(output, "Histórico local do Quiz entre amigos");
            }
            try {
                Files.move(temp, historyFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(temp, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
            if (storageError.startsWith("Não foi possível gravar o histórico local:")) {
                storageError = "";
            }
            return true;
        } catch (IOException ex) {
            storageError = "Não foi possível gravar o histórico local em "
                    + historyFile + ": " + ex.getMessage();
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            return false;
        }
    }

    record PlayerStats(String id, String name, int points, int games, int wins) { }
}
