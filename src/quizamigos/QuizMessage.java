package quizamigos;

import pitiupi.models.Messages.Message;

public class QuizMessage extends Message {

    private static final long serialVersionUID = 2L;

    public static final String QUESTION = "QUESTION";
    public static final String ANSWER = "ANSWER";
    public static final String RESULT = "RESULT";
    public static final String MODE_HOSTED = "HOSTED";
    public static final String MODE_OPEN = "OPEN";

    private String type;
    private String mode;
    private String quizId;
    private String questionId;
    private String hostId;
    private String hostName;
    private String senderId;
    private String senderName;
    private String question;
    private String[] options;
    private int selectedOption = -1;
    private int correctOption = -1;
    private int totalAnswers;
    private String[] scorePlayerIds;
    private String[] scorePlayerNames;
    private int[] scoreValues;
    private int questionIndex;
    private int totalQuestions;
    private int timerSeconds;
    private long deadlineEpochMillis;
    private boolean finalResult;

    public QuizMessage() { super(); }

    public static QuizMessage question(
            String mode, String quizId, String questionId, String hostId, String hostName,
            String question, String[] options, int questionIndex, int totalQuestions,
            int timerSeconds, long deadlineEpochMillis) {
        QuizMessage message = new QuizMessage();
        message.type = QUESTION;
        message.mode = mode;
        message.quizId = quizId;
        message.questionId = questionId;
        message.hostId = hostId;
        message.hostName = hostName;
        message.question = question;
        message.options = options == null ? null : options.clone();
        message.questionIndex = questionIndex;
        message.totalQuestions = totalQuestions;
        message.timerSeconds = timerSeconds;
        message.deadlineEpochMillis = deadlineEpochMillis;
        return message;
    }

    public static QuizMessage answer(
            String mode, String quizId, String questionId, String hostId,
            String senderId, String senderName, int selectedOption) {
        QuizMessage message = new QuizMessage();
        message.type = ANSWER;
        message.mode = mode;
        message.quizId = quizId;
        message.questionId = questionId;
        message.hostId = hostId;
        message.senderId = senderId;
        message.senderName = senderName;
        message.selectedOption = selectedOption;
        return message;
    }

    public static QuizMessage result(
            String mode, String quizId, String questionId, String hostId, String hostName,
            String question, String[] options, int correctOption, int totalAnswers,
            String[] scorePlayerIds, String[] scorePlayerNames, int[] scoreValues,
            int questionIndex, int totalQuestions, boolean finalResult) {
        QuizMessage message = new QuizMessage();
        message.type = RESULT;
        message.mode = mode;
        message.quizId = quizId;
        message.questionId = questionId;
        message.hostId = hostId;
        message.hostName = hostName;
        message.question = question;
        message.options = options == null ? null : options.clone();
        message.correctOption = correctOption;
        message.totalAnswers = totalAnswers;
        message.scorePlayerIds = scorePlayerIds == null ? null : scorePlayerIds.clone();
        message.scorePlayerNames = scorePlayerNames == null ? null : scorePlayerNames.clone();
        message.scoreValues = scoreValues == null ? null : scoreValues.clone();
        message.questionIndex = questionIndex;
        message.totalQuestions = totalQuestions;
        message.finalResult = finalResult;
        return message;
    }

    public String getType() { return type; }
    public String getMode() { return mode; }
    public String getQuizId() { return quizId; }
    public String getQuestionId() { return questionId; }
    public String getHostId() { return hostId; }
    public String getHostName() { return hostName; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getQuestion() { return question; }
    public String[] getOptions() { return options == null ? new String[0] : options.clone(); }
    public int getSelectedOption() { return selectedOption; }
    public int getCorrectOption() { return correctOption; }
    public int getTotalAnswers() { return totalAnswers; }
    public String[] getScorePlayerIds() {
        return scorePlayerIds == null ? new String[0] : scorePlayerIds.clone();
    }
    public String[] getScorePlayerNames() {
        return scorePlayerNames == null ? new String[0] : scorePlayerNames.clone();
    }
    public int[] getScoreValues() { return scoreValues == null ? new int[0] : scoreValues.clone(); }
    public int getQuestionIndex() { return questionIndex; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getTimerSeconds() { return timerSeconds; }
    public long getDeadlineEpochMillis() { return deadlineEpochMillis; }
    public boolean isFinalResult() { return finalResult; }
}
