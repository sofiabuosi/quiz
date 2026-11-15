package quizamigos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class QuizStorePersistenceTest {

    private QuizStorePersistenceTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("quizamigos-store-test");
        QuizStore first = new QuizStore(directory);
        String stableId = first.getPlayerId();
        first.completeGame("match-1", new String[] {"p1", "p2"},
                new String[] {"Ana", "Bia"}, new int[] {3, 1});

        QuizStore reopened = new QuizStore(directory);
        check(stableId.equals(reopened.getPlayerId()), "ID estável do computador");
        List<QuizStore.PlayerStats> ranking = reopened.getRanking();
        check(ranking.size() == 2, "dois jogadores no histórico");
        check("Ana".equals(ranking.get(0).name()), "campeã no topo do ranking");
        check(ranking.get(0).wins() == 1 && ranking.get(0).points() == 3,
                "pontos e vitória persistidos");

        reopened.completeGame("match-1", new String[] {"p1", "p2"},
                new String[] {"Ana", "Bia"}, new int[] {3, 1});
        QuizStore afterDuplicate = new QuizStore(directory);
        check(afterDuplicate.getRanking().get(0).points() == 3,
                "mesma partida não soma pontos duas vezes");

        reopened.completeGame("match-2", new String[] {"p1", "p2"},
                new String[] {"Ana", "Bia"}, new int[] {0, 4});
        QuizStore afterSecond = new QuizStore(directory);
        check(afterSecond.getRanking().get(0).name().equals("Bia"), "ranking atualizado após nova partida");
        check(afterSecond.getRanking().get(0).wins() == 1, "vitória da segunda partida persistida");

        afterSecond.completeGame("match-zero", new String[] {"p3"},
                new String[] {"Caio"}, new int[] {0});
        QuizStore afterZeroScore = new QuizStore(directory);
        QuizStore.PlayerStats zeroScore = afterZeroScore.getRanking().stream()
                .filter(row -> "p3".equals(row.id())).findFirst().orElseThrow();
        check(zeroScore.games() == 1 && zeroScore.points() == 0 && zeroScore.wins() == 0,
                "partida sem pontos também deve aparecer no histórico");

        Path blockedPath = Files.createTempFile("quizamigos-not-a-directory", ".tmp");
        QuizStore blocked = new QuizStore(blockedPath.resolve("history"));
        boolean saved = blocked.completeGame("match-fail", new String[] {"p4"},
                new String[] {"Duda"}, new int[] {1});
        check(!saved && blocked.getStorageError() != null && !blocked.getStorageError().isBlank(),
                "falha de gravação deve ser informada e não confirmada como sucesso");

        System.out.println("OK: histórico local, ID estável, deduplicação, zero pontos e erro de gravação.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Falhou: " + description);
    }
}
