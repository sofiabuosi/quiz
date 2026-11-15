package quizamigos;

import java.util.HashSet;
import java.util.Set;

public final class QuizBehaviorRulesTest {

    private QuizBehaviorRulesTest() { }

    public static void main(String[] args) {
        testInvitationDoesNotReplaceActiveGame();
        testIdlePlayerCanBeInvitedOnlyOnce();
        testSameGameContinuesWithoutAnotherInvitation();
        testHostedParticipantsCanAnswer();
        testHostedCoordinatorCannotAnswer();
        testOpenCoordinatorCanAnswer();
        testAllFourOptionsCanBeCorrect();
        System.out.println("OK: convite, proteção de partida, respostas do anfitrião e alternativas A-D.");
    }

    private static void testInvitationDoesNotReplaceActiveGame() {
        check(!QuizBehaviorRules.shouldOfferJoin("game-a", "game-b", true, Set.of()),
                "uma partida ativa não deve ser substituída por outra");
    }

    private static void testIdlePlayerCanBeInvitedOnlyOnce() {
        Set<String> declined = new HashSet<>();
        check(QuizBehaviorRules.shouldOfferJoin(null, "game-b", false, declined),
                "jogador ocioso deve receber convite");
        declined.add("game-b");
        check(!QuizBehaviorRules.shouldOfferJoin(null, "game-b", false, declined),
                "convite recusado não deve reaparecer em cada pergunta");
    }

    private static void testSameGameContinuesWithoutAnotherInvitation() {
        check(!QuizBehaviorRules.shouldOfferJoin("game-a", "game-a", true, Set.of()),
                "perguntas seguintes da mesma partida não pedem novo aceite");
    }

    private static void testHostedParticipantsCanAnswer() {
        check(QuizBehaviorRules.canSubmitAnswer(true, false, true, false, 0),
                "participante deve responder no modo anfitrião");
        check(QuizBehaviorRules.canSubmitAnswer(true, false, true, false, 3),
                "participante deve poder selecionar D no modo anfitrião");
    }

    private static void testHostedCoordinatorCannotAnswer() {
        check(!QuizBehaviorRules.canSubmitAnswer(true, false, true, true, 0),
                "anfitrião não responde à própria pergunta");
    }

    private static void testOpenCoordinatorCanAnswer() {
        check(QuizBehaviorRules.canSubmitAnswer(true, false, false, true, 0),
                "coordenador do quiz normal também responde");
        check(!QuizBehaviorRules.canSubmitAnswer(true, true, false, true, 1),
                "não pode enviar mais de uma resposta por rodada");
        check(!QuizBehaviorRules.canSubmitAnswer(true, false, false, false, -1),
                "não envia sem selecionar alternativa");
    }

    private static void testAllFourOptionsCanBeCorrect() {
        for (int option = 0; option < 4; option++) {
            check(QuizBehaviorRules.isCorrectOption(option, option),
                    "alternativa " + option + " deve ser reconhecida como correta");
            for (int other = 0; other < 4; other++) {
                if (other != option) {
                    check(!QuizBehaviorRules.isCorrectOption(other, option),
                            "alternativa diferente não pode ser pontuada como correta");
                }
            }
        }
        check(!QuizBehaviorRules.isCorrectOption(-1, 0), "índice negativo inválido");
        check(!QuizBehaviorRules.isCorrectOption(4, 0), "índice acima de D inválido");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Falhou: " + description);
    }
}
