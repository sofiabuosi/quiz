package quizamigos;

import java.util.List;

final class QuizCatalog {

    private QuizCatalog() { }

    static List<QuizPack> packs() {
        return List.of(new QuizPack("olivia", "Olivia Rodrigo", "Música / Pop", List.of(
                q("Qual é o álbum de estreia de Olivia Rodrigo, lançado em 2021?", "SOUR", "GUTS", "The Rise", "Drivers", 0),
                q("Qual single de 2021 fala sobre tirar a carteira de motorista?", "deja vu", "traitor", "drivers license", "favorite crime", 2),
                q("Em qual álbum está a música 'vampire'?", "SOUR", "GUTS", "Emails I Can't Send", "Happier Than Ever", 1),
                q("Qual personagem Olivia interpretou na série Bizaardvark?", "Paige Olvera", "Nini Salazar-Roberts", "Maddie", "Stacie", 0),
                q("Em que ano foi lançado o álbum GUTS?", "2021", "2022", "2023", "2024", 2))));
    }

    private static QuizQuestion q(String text, String a, String b, String c, String d, int correct) {
        return new QuizQuestion(text, new String[] {a, b, c, d}, correct);
    }

    record QuizPack(String id, String title, String category, List<QuizQuestion> questions) {
        @Override public String toString() { return title + " · " + category; }
    }

    record QuizQuestion(String text, String[] options, int correctOption) {
        QuizQuestion { options = options.clone(); }
        @Override public String[] options() { return options.clone(); }
    }
}
