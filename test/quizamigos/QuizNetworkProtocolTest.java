package quizamigos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class QuizNetworkProtocolTest {
    private QuizNetworkProtocolTest() { }

    public static void main(String[] args) throws Exception {
        long sharedDeadline = System.currentTimeMillis() + 20_000L;
        QuizMessage question = QuizMessage.question(
                QuizMessage.MODE_OPEN, "match-1", "question-1", "host-1", "Ana",
                "Qual é o álbum de estreia?", new String[] {"SOUR", "GUTS", "X", "Y"},
                1, 5, 20, sharedDeadline);

        QuizMessage clientA = transfer(question);
        QuizMessage clientB = transfer(question);
        check(clientA.getDeadlineEpochMillis() == clientB.getDeadlineEpochMillis(),
                "os dois clientes recebem o mesmo prazo absoluto");
        check(clientA.getTimerSeconds() == 20 && clientB.getTimerSeconds() == 20,
                "os dois clientes recebem a mesma duração");

        QuizMessage answerA = transfer(QuizMessage.answer(
                QuizMessage.MODE_OPEN, "match-1", "question-1", "host-1", "player-a", "Bia", 0));
        QuizMessage answerB = transfer(QuizMessage.answer(
                QuizMessage.MODE_OPEN, "match-1", "question-1", "host-1", "player-b", "Caio", 1));
        check("player-a".equals(answerA.getSenderId()) && answerA.getSelectedOption() == 0,
                "resposta do primeiro participante preservada");
        check("player-b".equals(answerB.getSenderId()) && answerB.getSelectedOption() == 1,
                "resposta do segundo participante preservada");
        System.out.println("OK: protocolo simulado com dois clientes, prazo comum e respostas distintas.");
    }

    private static QuizMessage transfer(QuizMessage message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(message);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (QuizMessage) input.readObject();
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Falhou: " + description);
    }
}
