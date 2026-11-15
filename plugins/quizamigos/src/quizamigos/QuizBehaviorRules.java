package quizamigos;

import java.util.Set;

final class QuizBehaviorRules {

    private QuizBehaviorRules() { }

    static boolean shouldOfferJoin(
            String currentQuizId, String incomingQuizId,
            boolean gameSessionActive, Set<String> declinedQuizIds) {
        if (incomingQuizId == null || incomingQuizId.isBlank()) return false;
        if (incomingQuizId.equals(currentQuizId)) return false;
        if (gameSessionActive) return false;
        return declinedQuizIds == null || !declinedQuizIds.contains(incomingQuizId);
    }

    static boolean canSubmitAnswer(
            boolean roundActive, boolean hasAnswered,
            boolean hostedMode, boolean coordinator, int selectedOption) {
        if (!roundActive || hasAnswered || selectedOption < 0 || selectedOption > 3) return false;
        return !(hostedMode && coordinator);
    }

    static boolean isCorrectOption(int selectedOption, int correctOption) {
        return selectedOption >= 0 && selectedOption <= 3
                && correctOption >= 0 && correctOption <= 3
                && selectedOption == correctOption;
    }
}
