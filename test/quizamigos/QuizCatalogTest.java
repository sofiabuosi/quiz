package quizamigos;

public final class QuizCatalogTest {
    private QuizCatalogTest() { }

    public static void main(String[] args) {
        check(QuizCatalog.packs().size() == 1, "deve existir somente um quiz pronto");
        QuizCatalog.QuizPack olivia = QuizCatalog.packs().get(0);
        check("olivia".equals(olivia.id()), "ID Olivia");
        check("Olivia Rodrigo".equals(olivia.title()), "nome Olivia Rodrigo");
        check(olivia.questions().size() == 5, "cinco perguntas");
        for (QuizCatalog.QuizQuestion question : olivia.questions()) {
            check(question.options().length == 4, "quatro alternativas");
            check(question.correctOption() >= 0 && question.correctOption() < 4,
                    "alternativa correta válida");
        }
        System.out.println("OK: catálogo contém somente Olivia Rodrigo, com cinco perguntas.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Falhou: " + description);
    }
}
