package quizamigos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class QuizMessageSerializationTest {

    private QuizMessageSerializationTest() { }

    public static void main(String[] args) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000L;
        QuizMessage question = QuizMessage.question(
                QuizMessage.MODE_OPEN, "quiz-1", "question-1", "coord-1", "Ana",
                "Qual é o álbum de estreia?", new String[] {"SOUR", "GUTS", "X", "Y"},
                1, 5, 15, deadline);
        QuizMessage questionCopy = roundTrip(question);
        check(QuizMessage.QUESTION.equals(questionCopy.getType()), "tipo QUESTION");
        check(QuizMessage.MODE_OPEN.equals(questionCopy.getMode()), "modo sem anfitrião");
        check(questionCopy.getDeadlineEpochMillis() == deadline, "prazo absoluto compartilhado");
        check(questionCopy.getTimerSeconds() == 15, "duração da rodada");
        check(questionCopy.getQuestionIndex() == 1 && questionCopy.getTotalQuestions() == 5,
                "posição no quiz pronto");
        check("GUTS".equals(questionCopy.getOptions()[1]), "alternativas");

        QuizMessage answer = QuizMessage.answer(
                QuizMessage.MODE_OPEN, "quiz-1", "question-1", "coord-1", "player-1", "Bia", 1);
        QuizMessage answerCopy = roundTrip(answer);
        check(QuizMessage.ANSWER.equals(answerCopy.getType()), "tipo ANSWER");
        check(answerCopy.getSelectedOption() == 1, "alternativa escolhida");

        QuizMessage result = QuizMessage.result(
                QuizMessage.MODE_OPEN, "quiz-1", "question-1", "coord-1", "Ana",
                "Qual é o álbum de estreia?", new String[] {"SOUR", "GUTS", "X", "Y"},
                0, 1, new String[] {"player-1"}, new String[] {"Bia"}, new int[] {1},
                1, 5, false);
        QuizMessage resultCopy = roundTrip(result);
        check(QuizMessage.RESULT.equals(resultCopy.getType()), "tipo RESULT");
        check(resultCopy.getCorrectOption() == 0, "resposta correta");
        check(!resultCopy.isFinalResult(), "resultado intermediário não é placar final");
        check(resultCopy.getScoreValues().length == 1 && resultCopy.getScoreValues()[0] == 1,
                "placar acumulado");

        QuizMessage finalResult = QuizMessage.result(
                QuizMessage.MODE_OPEN, "quiz-1", "question-5", "coord-1", "Ana",
                "Pergunta final", new String[] {"A", "B", "C", "D"},
                2, 2, new String[] {"player-1"}, new String[] {"Bia"}, new int[] {4},
                5, 5, true);
        check(roundTrip(finalResult).isFinalResult(), "marcador de placar final");

        System.out.println("OK: QUESTION, ANSWER, RESULT e prazo compartilhado serializam corretamente.");
    }

    private static QuizMessage roundTrip(QuizMessage message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(message); }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (QuizMessage) input.readObject();
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Falhou: " + description);
    }
}
