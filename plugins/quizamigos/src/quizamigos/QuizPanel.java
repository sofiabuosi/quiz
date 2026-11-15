package quizamigos;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.control.ConnectionManager;

@SuppressWarnings("serial")
final class QuizPanel extends JPanel {

    private static final String[] LETTERS = {"A", "B", "C", "D"};
    private static final String CARD_INTRO = "intro";
    private static final String CARD_HOME = "home";
    private static final String CARD_HOST = "host";
    private static final String CARD_GAME = "game";
    private static final String CARD_HISTORY = "history";

    private final ConnectionManager connectionManager;
    private final QuizStore store;
    private final MusicPlayer musicPlayer;
    private JButton homeMusicToggleButton;
    private JButton hostMusicToggleButton;

    private final String localPlayerId;
    private final String localPlayerName;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final IntroPanel introPanel = new IntroPanel();

    private final JLabel coverLabel = new JLabel("QUIZ", SwingConstants.CENTER);
    private final JComboBox<QuizCatalog.QuizPack> packSelector =
            new JComboBox<>(QuizCatalog.packs().toArray(new QuizCatalog.QuizPack[0]));

    private final JTextArea questionInput = new JTextArea(3, 32);
    private final JTextField[] optionInputs = {
        new JTextField(27), new JTextField(27), new JTextField(27), new JTextField(27)
    };
    private final JComboBox<String> correctOptionInput = new JComboBox<>(LETTERS);
    private final JComboBox<Integer> hostTimerSelector = new JComboBox<>(new Integer[] {10, 20, 30, 45, 60});

    private final JLabel questionLabel = new JLabel("A pergunta aparecerá aqui.", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("Tempo: --", SwingConstants.CENTER);
    private final JLabel gameStatusLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel revealLabel = new JLabel("", SwingConstants.CENTER);
    private final JTextArea scoreboardArea = new JTextArea(9, 30);
    private final JRadioButton[] answerButtons = new JRadioButton[4];
    private final ButtonGroup answerGroup = new ButtonGroup();
    private final JButton submitAnswerButton;
    private final JButton revealNowButton;
    private final JButton nextHostedQuestionButton;
    private final JButton finishHostedButton;
    private final JButton leaveButton;
    private final JTextArea historyArea = new JTextArea(14, 42);

    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Map<String, String> scoreNames = new LinkedHashMap<>();
    private final Map<String, Integer> receivedAnswers = new LinkedHashMap<>();
    private final Set<String> answeredPlayerIds = new HashSet<>();

    private JPanel gamePanel;
    private JPanel hostPanel;
    private Timer countdownTimer;
    private Timer nextQuestionTimer;
    private Timer transitionTimer;
    private String quizId;
    private String questionId;
    private String coordinatorId;
    private String coordinatorName;
    private String currentMode = QuizMessage.MODE_HOSTED;
    private String currentQuestion;
    private String[] currentOptions = new String[0];
    private int correctOption = -1;
    private int mySelectedOption = -1;
    private int currentQuestionIndex;
    private int totalQuestions;
    private int currentTimerSeconds;
    private long deadlineEpochMillis;
    private boolean isCoordinator;
    private boolean isHostedMode;
    private boolean roundActive;
    private boolean hasAnswered;
    private boolean deadlineHandled;
    private QuizCatalog.QuizPack selectedPack;

    QuizPanel(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.store = new QuizStore();
        this.musicPlayer = new MusicPlayer();
        this.submitAnswerButton = retroButton("Enviar resposta");
        this.revealNowButton = retroButton("Revelar agora");
        this.nextHostedQuestionButton = retroButton("Nova pergunta");
        this.finishHostedButton = retroButton("Finalizar e mostrar ganhador");
        this.leaveButton = retroButton("Sair do quiz");
        this.localPlayerId = store.getPlayerId();
        String configuredName = connectionManager.getUserName();
        this.localPlayerName = configuredName == null || configuredName.isBlank()
                ? "Jogador" : configuredName.trim();
        scoreNames.put(localPlayerId, localPlayerName);
        buildInterface();
        loadMainCoverAsync();
        cardLayout.show(cards, CARD_HOME);
        musicPlayer.playLoopWithFadeIn();
    }

    private void buildInterface() {
        setLayout(new BorderLayout());
        setBackground(RetroPanel.INK);
        cards.setOpaque(false);
        cards.add(introPanel, CARD_INTRO);
        cards.add(buildHomePanel(), CARD_HOME);
        hostPanel = buildHostPanel();
        cards.add(hostPanel, CARD_HOST);
        gamePanel = buildGamePanel();
        cards.add(gamePanel, CARD_GAME);
        cards.add(buildHistoryPanel(), CARD_HISTORY);
        add(cards, BorderLayout.CENTER);
    }

    private JButton createMusicToggleButton() {
        JButton button = retroButton(
                musicPlayer.isMusicMuted() ? "MÚSICA: MUTADA" : "MÚSICA: LIGADA");
        button.setPreferredSize(new Dimension(190, 42));
        button.addActionListener(event -> {
            musicPlayer.setMusicMuted(!musicPlayer.isMusicMuted());
            String label = musicPlayer.isMusicMuted()
                    ? "MÚSICA: MUTADA" : "MÚSICA: LIGADA";

            if (homeMusicToggleButton != null) {
                homeMusicToggleButton.setText(label);
            }
            if (hostMusicToggleButton != null) {
                hostMusicToggleButton.setText(label);
            }
        });
        return button;
    }

    private JPanel buildHomePanel() {
        RetroPanel panel = new RetroPanel();
        panel.setLayout(new BorderLayout(18, 18));
        panel.setBorder(BorderFactory.createEmptyBorder(28, 34, 26, 34));

        JLabel title = new JLabel("QUIZ", SwingConstants.CENTER);
        title.setForeground(RetroPanel.PALE);
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        JPanel heading = transparentPanel(new BorderLayout());
        heading.add(title, BorderLayout.CENTER);
        homeMusicToggleButton = createMusicToggleButton();
        heading.add(homeMusicToggleButton, BorderLayout.EAST);
panel.add(heading, BorderLayout.NORTH);

        JPanel content = transparentPanel(new GridLayout(1, 2, 22, 12));
        JPanel menu = transparentPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.add(Box.createVerticalStrut(24));
        JLabel packCaption = retroLabel("QUIZES", 12, true);
        packCaption.setAlignmentX(CENTER_ALIGNMENT);
        packSelector.setMaximumSize(new Dimension(390, 34));
        menu.add(packCaption);
        menu.add(packSelector);
        menu.add(Box.createVerticalStrut(18));
        JButton hostButton = retroButton("MODO COM ANFITRIÃO");
        hostButton.setAlignmentX(CENTER_ALIGNMENT);
        hostButton.addActionListener(event -> startHostedSetup());
        JButton openButton = retroButton("MODO NORMAL");
        openButton.setAlignmentX(CENTER_ALIGNMENT);
        openButton.addActionListener(event -> startOpenGame());
        JButton historyButton = retroButton("HISTÓRICO LOCAL / RANKING");
        historyButton.setAlignmentX(CENTER_ALIGNMENT);
        historyButton.addActionListener(event -> showHistory());
        for (JButton button : List.of(hostButton, openButton, historyButton)) {
            button.setMaximumSize(new Dimension(390, 48));
            menu.add(button);
            menu.add(Box.createVerticalStrut(12));
        }
        content.add(menu);

        JPanel cover = transparentPanel();
        cover.setLayout(new BoxLayout(cover, BoxLayout.Y_AXIS));
        coverLabel.setOpaque(true);
        coverLabel.setBackground(new Color(60, 20, 100));
        coverLabel.setForeground(RetroPanel.PALE);
        coverLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        coverLabel.setPreferredSize(new Dimension(310, 360));
        coverLabel.setMaximumSize(new Dimension(310, 360));
        coverLabel.setAlignmentX(CENTER_ALIGNMENT);
        cover.add(Box.createVerticalGlue());
        cover.add(coverLabel);
        cover.add(Box.createVerticalGlue());
        content.add(cover);
        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildHostPanel() {
        RetroPanel panel = new RetroPanel();
        panel.setLayout(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));
        JLabel heading = retroLabel("MODO COM ANFITRIÃO", 24, true);
        panel.add(heading, BorderLayout.NORTH);

        JPanel fields = transparentPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        JLabel questionCaption = retroLabel("PERGUNTA", 13, true);
        questionInput.setLineWrap(true);
        questionInput.setWrapStyleWord(true);
        styleTextArea(questionInput);
        fields.add(questionCaption);
        fields.add(new JScrollPane(questionInput));
        fields.add(Box.createVerticalStrut(7));
        for (int i = 0; i < optionInputs.length; i++) {
            JPanel row = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            JLabel letter = retroLabel(LETTERS[i] + ")", 13, true);
            optionInputs[i].setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            row.add(letter);
            row.add(optionInputs[i]);
            fields.add(row);
        }
        hostTimerSelector.setSelectedItem(30);
        JPanel settings = transparentPanel(new FlowLayout(FlowLayout.LEFT, 9, 5));
        settings.add(retroLabel("RESPOSTA CERTA:", 12, true));
        settings.add(correctOptionInput);
        settings.add(retroLabel("TEMPO:", 12, true));
        settings.add(hostTimerSelector);
        settings.add(retroLabel("segundos", 11, false));
        fields.add(settings);
        panel.add(fields, BorderLayout.CENTER);

        JPanel buttons = transparentPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        JButton publish = retroButton("PUBLICAR PERGUNTA E INICIAR RELÓGIO");
        publish.addActionListener(event -> startHostedRound());
        JButton back = retroButton("VOLTAR");
        back.addActionListener(event -> {
            cardLayout.show(cards, CARD_HOME);
            if (!musicPlayer.isPlaying()) musicPlayer.playLoopWithFadeIn();
        });
        buttons.add(publish);
        buttons.add(back);
        panel.add(buttons, BorderLayout.SOUTH);

        java.awt.Component previousNorth =
                ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        JPanel hostHeader = transparentPanel(new BorderLayout());
        if (previousNorth != null) {
            hostHeader.add(previousNorth, BorderLayout.CENTER);
        }
        hostMusicToggleButton = createMusicToggleButton();
        hostHeader.add(hostMusicToggleButton, BorderLayout.EAST);
        panel.add(hostHeader, BorderLayout.NORTH);

        return panel;
    }

    private JPanel buildGamePanel() {
        RetroPanel panel = new RetroPanel();
        panel.setLayout(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));

        JPanel top = transparentPanel(new BorderLayout(8, 8));
        JLabel title = retroLabel("QUIZ", 22, true);
        timerLabel.setForeground(RetroPanel.PINK);
        timerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 19));
        top.add(title, BorderLayout.WEST);
        top.add(timerLabel, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        JPanel center = transparentPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        questionLabel.setForeground(RetroPanel.PALE);
        questionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        questionLabel.setVerticalAlignment(SwingConstants.CENTER);
        questionLabel.setAlignmentX(CENTER_ALIGNMENT);
        questionLabel.setBorder(BorderFactory.createEmptyBorder(12, 18, 20, 18));
        center.add(questionLabel);
        JPanel answers = transparentPanel(new GridLayout(2, 2, 10, 10));
        for (int i = 0; i < answerButtons.length; i++) {
            final int option = i;
            answerButtons[i] = new JRadioButton(LETTERS[i]);
            answerButtons[i].setOpaque(true);
            answerButtons[i].setBackground(new Color(57, 24, 92));
            answerButtons[i].setForeground(RetroPanel.PALE);
            answerButtons[i].setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            answerButtons[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(183, 111, 236), 1),
                    BorderFactory.createEmptyBorder(13, 14, 13, 14)));
            answerButtons[i].addActionListener(event -> mySelectedOption = option);
            answerGroup.add(answerButtons[i]);
            answers.add(answerButtons[i]);
        }
        center.add(answers);
        revealLabel.setForeground(new Color(255, 222, 120));
        revealLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        revealLabel.setAlignmentX(CENTER_ALIGNMENT);
        revealLabel.setBorder(BorderFactory.createEmptyBorder(12, 2, 8, 2));
        center.add(revealLabel);
        gameStatusLabel.setForeground(RetroPanel.LILAC);
        gameStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gameStatusLabel.setAlignmentX(CENTER_ALIGNMENT);
        center.add(gameStatusLabel);
        panel.add(center, BorderLayout.CENTER);

        scoreboardArea.setEditable(false);
        scoreboardArea.setBackground(new Color(24, 9, 43));
        scoreboardArea.setForeground(RetroPanel.PALE);
        scoreboardArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane board = new JScrollPane(scoreboardArea);
        board.setPreferredSize(new Dimension(240, 130));
        board.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(RetroPanel.LILAC), "PLACAR DA PARTIDA",
                0, 0, new Font(Font.MONOSPACED, Font.BOLD, 11), RetroPanel.LILAC));
        panel.add(board, BorderLayout.EAST);

        JPanel actions = transparentPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        submitAnswerButton.addActionListener(event -> submitAnswer());
        revealNowButton.addActionListener(event -> closeHostedRound());
        nextHostedQuestionButton.addActionListener(event -> cardLayout.show(cards, CARD_HOST));
        finishHostedButton.addActionListener(event -> finishHostedQuiz());
        leaveButton.addActionListener(event -> exitToHome());
        actions.add(submitAnswerButton);
        actions.add(revealNowButton);
        actions.add(nextHostedQuestionButton);
        actions.add(finishHostedButton);
        actions.add(leaveButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildHistoryPanel() {
        RetroPanel panel = new RetroPanel();
        panel.setLayout(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(26, 34, 26, 34));
        panel.add(retroLabel("HISTÓRICO DESTE COMPUTADOR", 22, true), BorderLayout.NORTH);
        historyArea.setEditable(false);
        styleTextArea(historyArea);
        panel.add(new JScrollPane(historyArea), BorderLayout.CENTER);
        JButton back = retroButton("VOLTAR AO MENU");
        back.addActionListener(event -> cardLayout.show(cards, CARD_HOME));
        panel.add(back, BorderLayout.SOUTH);
        return panel;
    }

    private void startHostedSetup() {
        enterStage(() -> {
            resetGame(QuizMessage.MODE_HOSTED);
            isHostedMode = true;
            isCoordinator = true;
            coordinatorId = localPlayerId;
            coordinatorName = localPlayerName;
            questionInput.setText("");
            for (JTextField input : optionInputs) input.setText("");
            correctOptionInput.setSelectedIndex(0);
            cardLayout.show(cards, CARD_HOST);
        });
    }

    private void startOpenGame() {
        selectedPack = (QuizCatalog.QuizPack) packSelector.getSelectedItem();
        if (selectedPack == null) return;
        loadOliviaIntroCoverAsync();
        enterStage(() -> {
            resetGame(QuizMessage.MODE_OPEN);
            isHostedMode = false;
            isCoordinator = true; // coordenador automático: inicia o relógio, mas também responde.
            coordinatorId = localPlayerId;
            coordinatorName = localPlayerName;
            totalQuestions = selectedPack.questions().size();
            currentQuestionIndex = 0;
            currentTimerSeconds = 20; // tempo padrão do quiz normal; igual para todos os jogadores.
            if (musicPlayer.isPlaying()) {
                musicPlayer.fadeOutAndStop(550);
                if (nextQuestionTimer != null) nextQuestionTimer.stop();
                nextQuestionTimer = new Timer(575, event -> {
                    nextQuestionTimer.stop();
                    nextQuestionTimer = null;
                    startOpenQuestion();
                });
                nextQuestionTimer.setRepeats(false);
                nextQuestionTimer.start();
            } else {
                startOpenQuestion();
            }
        });
    }

    private void enterStage(Runnable ready) {
        if (introPanel.isCurtainOpen()) {
            ready.run();
            return;
        }
        cardLayout.show(cards, CARD_INTRO);
        introPanel.animateEntrance(ready);
    }

    private void resetGame(String mode) {
        stopTimers();
        quizId = UUID.randomUUID().toString();
        questionId = null;
        coordinatorId = localPlayerId;
        coordinatorName = localPlayerName;
        currentMode = mode;
        currentQuestion = null;
        currentOptions = new String[0];
        correctOption = -1;
        currentQuestionIndex = 0;
        totalQuestions = 0;
        mySelectedOption = -1;
        roundActive = false;
        hasAnswered = false;
        deadlineHandled = false;
        scores.clear();
        scoreNames.clear();
        scoreNames.put(localPlayerId, localPlayerName);
        receivedAnswers.clear();
        answeredPlayerIds.clear();
        scoreboardArea.setText("Aguardando a primeira resposta...\n");
        revealLabel.setText("");
    }

    private void startHostedRound() {
        if (roundActive) {
            showWarning("A rodada atual ainda está em andamento.");
            return;
        }
        String question = questionInput.getText().trim();
        String[] options = new String[4];
        for (int i = 0; i < options.length; i++) options[i] = optionInputs[i].getText().trim();
        if (question.isEmpty()) {
            showWarning("Digite o texto da pergunta.");
            return;
        }
        for (String option : options) {
            if (option.isEmpty()) {
                showWarning("Preencha as quatro alternativas (A, B, C e D).");
                return;
            }
        }
        correctOption = correctOptionInput.getSelectedIndex();
        currentQuestion = question;
        currentOptions = options;
        currentTimerSeconds = (Integer) hostTimerSelector.getSelectedItem();
        currentQuestionIndex++;
        totalQuestions = 0; // o anfitrião decide quando terminar a partida.
        questionId = UUID.randomUUID().toString();
        beginRoundClock();
        isCoordinator = true;
        isHostedMode = true;
        currentMode = QuizMessage.MODE_HOSTED;
        hasAnswered = false;
        mySelectedOption = -1;
        receivedAnswers.clear();
        answeredPlayerIds.clear();
        musicPlayer.stopMusicImmediately(); // nenhuma música durante o tempo de resposta.
        displayQuestion(true);
        QuizMessage message = QuizMessage.question(
                currentMode, quizId, questionId, coordinatorId, coordinatorName,
                currentQuestion, currentOptions, currentQuestionIndex, totalQuestions,
                currentTimerSeconds, deadlineEpochMillis);
        int sent = sendToRoom(message);
        gameStatusLabel.setText("Pergunta enviada. O mesmo prazo está ativo em todos os computadores. "
                + "Envios solicitados: " + sent + ".");
    }

    private void startOpenQuestion() {
        if (selectedPack == null || currentQuestionIndex >= selectedPack.questions().size()) return;
        QuizCatalog.QuizQuestion item = selectedPack.questions().get(currentQuestionIndex);
        currentQuestion = item.text();
        currentOptions = item.options();
        correctOption = item.correctOption();
        questionId = UUID.randomUUID().toString();
        totalQuestions = selectedPack.questions().size();
        isCoordinator = true;
        isHostedMode = false;
        currentMode = QuizMessage.MODE_OPEN;
        hasAnswered = false;
        mySelectedOption = -1;
        receivedAnswers.clear();
        answeredPlayerIds.clear();
        beginRoundClock();
        if (currentQuestionIndex == 0) musicPlayer.stopMusicImmediately();
        displayQuestion(false);
        QuizMessage message = QuizMessage.question(
                currentMode, quizId, questionId, coordinatorId, coordinatorName,
                currentQuestion, currentOptions, currentQuestionIndex + 1, totalQuestions,
                currentTimerSeconds, deadlineEpochMillis);
        int sent = sendToRoom(message);
        gameStatusLabel.setText("Quiz compartilhado: " + selectedPack.title()
                + ". Relógio comum iniciado por " + coordinatorName + ". Envio: " + sent + ".");
    }

    private void beginRoundClock() {
        deadlineEpochMillis = System.currentTimeMillis() + currentTimerSeconds * 1000L;
        deadlineHandled = false;
        roundActive = true;
        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new Timer(200, event -> updateCountdown());
        countdownTimer.start();
        updateCountdown();
    }

    private void updateCountdown() {
        long remaining = Math.max(0, deadlineEpochMillis - System.currentTimeMillis());
        long seconds = (remaining + 999) / 1000;
        timerLabel.setText(String.format("TEMPO  %02d:%02d", seconds / 60, seconds % 60));
        if (remaining <= 0 && !deadlineHandled) {
            deadlineHandled = true;
            if (countdownTimer != null) countdownTimer.stop();
            onDeadlineReached();
        }
    }

    private void onDeadlineReached() {
        if (!roundActive) return;
        if (isCoordinator) {
            if (isHostedMode) closeHostedRound();
            else closeOpenRound();
        } else {
            roundActive = false;
            lockAnswerControls();
            gameStatusLabel.setText("Tempo encerrado. Aguardando a revelação do coordenador da partida...");
        }
    }

    private void submitAnswer() {
        if (!roundActive || hasAnswered || isHostedMode || mySelectedOption < 0) {
            if (mySelectedOption < 0) showWarning("Escolha uma alternativa antes de enviar.");
            return;
        }
        QuizMessage message = QuizMessage.answer(
                currentMode, quizId, questionId, coordinatorId,
                localPlayerId, localPlayerName, mySelectedOption);
        if (isCoordinator && QuizMessage.MODE_OPEN.equals(currentMode)) {
            receivedAnswers.put(localPlayerId, mySelectedOption);
            answeredPlayerIds.add(localPlayerId);
            scoreNames.put(localPlayerId, localPlayerName);
            scores.putIfAbsent(localPlayerId, 0);
        }
        int sent = sendToRoom(message);
        hasAnswered = true;
        lockAnswerControls();
        gameStatusLabel.setText("Resposta registrada. Aguarde o relógio comum terminar. Envio: " + sent + ".");
        updateScoreboard();
    }

    private void closeHostedRound() {
        if (!isCoordinator || !isHostedMode || !roundActive) return;
        roundActive = false;
        stopCountdownOnly();
        addPointsFromAnswers();
        QuizMessage result = makeResult(false, correctOption);
        sendToRoom(result);
        showRoundReveal(correctOption, receivedAnswers.size(), false);
        gameStatusLabel.setText("Rodada encerrada pelo relógio/anfitrião. Você pode publicar outra pergunta ou finalizar.");
        revealNowButton.setVisible(false);
    }

    private void closeOpenRound() {
        if (!isCoordinator || isHostedMode || !roundActive) return;
        roundActive = false;
        stopCountdownOnly();
        addPointsFromAnswers();
        boolean finalQuestion = currentQuestionIndex == totalQuestions - 1;
        QuizMessage result = makeResult(finalQuestion, correctOption);
        showRoundReveal(correctOption, receivedAnswers.size(), finalQuestion);
        sendToRoom(result);
        if (finalQuestion) {
            persistFinalScores();
            gameStatusLabel.setText("Fim do quiz! O placar final foi salvo neste computador.");
        } else {
            gameStatusLabel.setText("Resposta revelada. O placar aparecerá antes da próxima pergunta.");
        }
    }

    private void addPointsFromAnswers() {
        for (Map.Entry<String, Integer> entry : receivedAnswers.entrySet()) {
            String playerId = entry.getKey();
            scores.putIfAbsent(playerId, 0);
            if (entry.getValue() == correctOption) {
                scores.put(playerId, scores.get(playerId) + 1);
            }
        }
        updateScoreboard();
    }

    private QuizMessage makeResult(boolean finalResult, int answer) {
        List<Map.Entry<String, Integer>> ranking = sortedScores();
        String[] ids = new String[ranking.size()];
        String[] names = new String[ranking.size()];
        int[] values = new int[ranking.size()];
        for (int i = 0; i < ranking.size(); i++) {
            Map.Entry<String, Integer> entry = ranking.get(i);
            ids[i] = entry.getKey();
            names[i] = scoreNames.getOrDefault(entry.getKey(), "Jogador");
            values[i] = entry.getValue();
        }
        return QuizMessage.result(currentMode, quizId, questionId, coordinatorId, coordinatorName,
                currentQuestion, currentOptions, answer, receivedAnswers.size(), ids, names, values,
                currentQuestionIndex + (isHostedMode ? 0 : 1), totalQuestions, finalResult);
    }

    private void finishHostedQuiz() {
        if (!isHostedMode || !isCoordinator || roundActive) return;
        QuizMessage result = makeResult(true, -1);
        sendToRoom(result);
        persistFinalScores();
        long transitionDuration = musicPlayer.playEffect(MusicPlayer.Effect.REVEAL_TO_SCORE);
        runAfterEffect(transitionDuration, this::showFinalScoreboard);
    }

    private void receiveQuestion(QuizMessage message) {
        if (localPlayerId.equals(message.getHostId())) return;
        String[] options = message.getOptions();
        if (message.getQuizId() == null || message.getQuestionId() == null
                || message.getQuestion() == null || options.length != 4
                || message.getDeadlineEpochMillis() <= 0
                || !(QuizMessage.MODE_HOSTED.equals(message.getMode())
                    || QuizMessage.MODE_OPEN.equals(message.getMode()))) {
            gameStatusLabel.setText("Pergunta recebida com dados inválidos; mensagem ignorada.");
            return;
        }
        if (!message.getQuizId().equals(quizId)) {
            stopTimers();
            quizId = message.getQuizId();
            scores.clear();
            scoreNames.clear();
            receivedAnswers.clear();
            answeredPlayerIds.clear();
        }
        currentMode = message.getMode();
        isHostedMode = QuizMessage.MODE_HOSTED.equals(currentMode);
        isCoordinator = false;
        coordinatorId = message.getHostId();
        coordinatorName = safeName(message.getHostName());
        questionId = message.getQuestionId();
        currentQuestion = message.getQuestion();
        currentOptions = options;
        correctOption = -1;
        currentQuestionIndex = Math.max(0, message.getQuestionIndex() - 1);
        totalQuestions = message.getTotalQuestions();
        currentTimerSeconds = message.getTimerSeconds();
        deadlineEpochMillis = message.getDeadlineEpochMillis();
        hasAnswered = false;
        mySelectedOption = -1;
        roundActive = System.currentTimeMillis() < deadlineEpochMillis;
        deadlineHandled = false;
        receivedAnswers.clear();
        answeredPlayerIds.clear();
        musicPlayer.stopMusicImmediately();
        displayQuestion(false);
        if (roundActive) startCountdownForReceivedQuestion();
        else {
            lockAnswerControls();
            gameStatusLabel.setText("A pergunta chegou após o prazo. Aguardando o resultado.");
        }
    }

    private void startCountdownForReceivedQuestion() {
        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new Timer(200, event -> updateCountdown());
        countdownTimer.start();
        updateCountdown();
    }

    private void receiveAnswer(QuizMessage message) {
        if (!isCoordinator || !roundActive
                || !equalsSafe(quizId, message.getQuizId())
                || !equalsSafe(questionId, message.getQuestionId())
                || !equalsSafe(coordinatorId, message.getHostId())
                || !equalsSafe(currentMode, message.getMode())) return;
        String playerId = message.getSenderId();
        int selected = message.getSelectedOption();
        if (System.currentTimeMillis() >= deadlineEpochMillis
                || playerId == null || playerId.isBlank() || playerId.equals(localPlayerId)
                || selected < 0 || selected > 3 || !answeredPlayerIds.add(playerId)) return;
        receivedAnswers.put(playerId, selected);
        scoreNames.put(playerId, safeName(message.getSenderName()));
        scores.putIfAbsent(playerId, 0);
        gameStatusLabel.setText("Respostas recebidas: " + receivedAnswers.size()
                + ". O prazo é comum a todos.");
        updateScoreboard();
    }

    private void receiveResult(QuizMessage message) {
        if (isCoordinator || !equalsSafe(quizId, message.getQuizId())
                || !equalsSafe(questionId, message.getQuestionId())
                || !equalsSafe(coordinatorId, message.getHostId())
                || !equalsSafe(currentMode, message.getMode())) return;
        roundActive = false;
        stopCountdownOnly();
        correctOption = message.getCorrectOption();
        currentQuestion = message.getQuestion() == null ? currentQuestion : message.getQuestion();
        currentOptions = message.getOptions().length == 4 ? message.getOptions() : currentOptions;
        loadScoreSnapshot(message);
        showRoundReveal(correctOption, message.getTotalAnswers(), message.isFinalResult());
        if (message.isFinalResult()) {
            persistFinalScores();
            gameStatusLabel.setText("Placar final recebido e salvo no histórico local deste computador.");
        } else if (QuizMessage.MODE_HOSTED.equals(currentMode)) {
            gameStatusLabel.setText("Resposta revelada pelo anfitrião. Aguardando a próxima pergunta.");
        } else {
            gameStatusLabel.setText("Resposta revelada. Próxima pergunta em instantes; trilha permanece em silêncio.");
        }
    }

    private void loadScoreSnapshot(QuizMessage message) {
        scores.clear();
        scoreNames.clear();
        int[] values = message.getScoreValues();
        String[] ids = message.getScorePlayerIds();
        String[] names = message.getScorePlayerNames();
        int count = Math.min(values.length, Math.min(ids.length, names.length));
        for (int i = 0; i < count; i++) {
            if (ids[i] == null) continue;
            scores.put(ids[i], values[i]);
            scoreNames.put(ids[i], safeName(names[i]));
        }
        updateScoreboard();
    }

    void receive(QuizMessage message) {
        if (message == null || message.getType() == null) return;
        switch (message.getType()) {
            case QuizMessage.QUESTION -> receiveQuestion(message);
            case QuizMessage.ANSWER -> receiveAnswer(message);
            case QuizMessage.RESULT -> receiveResult(message);
            default -> gameStatusLabel.setText("Mensagem de quiz desconhecida ignorada.");
        }
    }

    private void displayQuestion(boolean hostView) {
        cardLayout.show(cards, CARD_GAME);
        questionLabel.setText("<html><div style='text-align:center'>"
                + escapeHtml(currentQuestion) + "</div></html>");
        revealLabel.setText("");
        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(LETTERS[i] + ")  " + currentOptions[i]);
            answerButtons[i].setSelected(false);
            answerButtons[i].setEnabled(!hostView && roundActive);
        }
        answerGroup.clearSelection();
        submitAnswerButton.setVisible(!hostView);
        submitAnswerButton.setEnabled(!hostView && roundActive);
        revealNowButton.setVisible(hostView);
        revealNowButton.setEnabled(hostView && roundActive);
        nextHostedQuestionButton.setVisible(false);
        finishHostedButton.setVisible(false);
        leaveButton.setVisible(true);
        timerLabel.setVisible(true);
        timerLabel.setText(String.format("TEMPO  %02d:%02d", currentTimerSeconds / 60, currentTimerSeconds % 60));
        updateScoreboard();
    }

    private void showRoundReveal(int correct, int totalAnswers, boolean finalResult) {
        roundActive = false;
        lockAnswerControls();
        revealNowButton.setVisible(false);
        submitAnswerButton.setVisible(false);
        nextHostedQuestionButton.setVisible(false);
        finishHostedButton.setVisible(false);
        timerLabel.setText("TEMPO ENCERRADO");
        if (correct >= 0 && correct < currentOptions.length) {
            revealLabel.setText("RESPOSTA CERTA: " + LETTERS[correct] + ") " + currentOptions[correct]
                    + "  ·  " + totalAnswers + " resposta(s)");
        } else {
            revealLabel.setText(finalResult ? "PARTIDA ENCERRADA" : "RODADA ENCERRADA");
        }
        leaveButton.setVisible(true);
        scoreboardArea.setText("RESPOSTA REVELADA\nATUALIZANDO PLACAR...");
        scoreboardArea.setCaretPosition(0);

        long delay = musicPlayer.playEffect(MusicPlayer.Effect.REVEAL_TO_SCORE);
        runAfterEffect(delay, () -> {
            if (finalResult) {
                showFinalScoreboard();
                return;
            }
            updateScoreboard();
            long scoreboardCueDuration = musicPlayer.playEffect(MusicPlayer.Effect.SCOREBOARD);
            if (isCoordinator && isHostedMode) {
                runAfterEffect(scoreboardCueDuration, () -> {
                    nextHostedQuestionButton.setVisible(true);
                    finishHostedButton.setVisible(true);
                });
            } else if (isCoordinator && QuizMessage.MODE_OPEN.equals(currentMode)) {
                scheduleNextOpenQuestion(scoreboardCueDuration + 2_200L);
            }
        });
    }

    private void scheduleNextOpenQuestion(long delayMillis) {
        if (nextQuestionTimer != null) nextQuestionTimer.stop();
        int delay = (int) Math.min(Integer.MAX_VALUE, Math.max(3_200L, delayMillis));
        nextQuestionTimer = new Timer(delay, event -> {
            nextQuestionTimer.stop();
            nextQuestionTimer = null;
            currentQuestionIndex++;
            startOpenQuestion();
        });
        nextQuestionTimer.setRepeats(false);
        nextQuestionTimer.start();
    }

    private void runAfterEffect(long delayMillis, Runnable action) {
        if (transitionTimer != null) transitionTimer.stop();
        if (delayMillis <= 0L) {
            action.run();
            return;
        }
        transitionTimer = new Timer((int) Math.min(Integer.MAX_VALUE, delayMillis), event -> {
            transitionTimer.stop();
            transitionTimer = null;
            action.run();
        });
        transitionTimer.setRepeats(false);
        transitionTimer.start();
    }

    private void showFinalScoreboard() {
        List<Map.Entry<String, Integer>> ranking = sortedScores();
        if (ranking.isEmpty()) {
            revealLabel.setText("PLACAR FINAL · Ninguém pontuou nesta partida.");
        } else {
            int highest = ranking.get(0).getValue();
            List<String> winners = new ArrayList<>();
            if (highest > 0) {
                for (Map.Entry<String, Integer> entry : ranking) {
                    if (entry.getValue() == highest) winners.add(scoreNames.getOrDefault(entry.getKey(), "Jogador"));
                }
            }
            String winnerText = winners.isEmpty() ? "Sem vencedor" : String.join(" e ", winners);
            revealLabel.setText("GANHADOR(ES): " + winnerText + " · " + Math.max(0, highest) + " ponto(s)");
        }
        gameStatusLabel.setText("PLACAR FINAL · Histórico gravado apenas neste computador.");
        nextHostedQuestionButton.setVisible(false);
        finishHostedButton.setVisible(false);
        revealNowButton.setVisible(false);
        submitAnswerButton.setVisible(false);
        timerLabel.setVisible(false);
        leaveButton.setVisible(true);
        updateScoreboard();
        long applauseDuration = musicPlayer.playEffect(MusicPlayer.Effect.SCOREBOARD);
        runAfterEffect(applauseDuration, () -> musicPlayer.playLoopWithFadeIn());
    }

    private void persistFinalScores() {
        List<Map.Entry<String, Integer>> ranking = sortedScores();
        String[] ids = new String[ranking.size()];
        String[] names = new String[ranking.size()];
        int[] values = new int[ranking.size()];
        for (int i = 0; i < ranking.size(); i++) {
            ids[i] = ranking.get(i).getKey();
            names[i] = scoreNames.getOrDefault(ids[i], "Jogador");
            values[i] = ranking.get(i).getValue();
        }
        store.completeGame(quizId, ids, names, values);
    }

    private void updateScoreboard() {
        List<Map.Entry<String, Integer>> ranking = sortedScores();
        StringBuilder text = new StringBuilder();
        if (ranking.isEmpty()) text.append("Aguardando pontuações...\n");
        else {
            int place = 1;
            for (Map.Entry<String, Integer> entry : ranking) {
                text.append(String.format("%d. %-20s %d pt(s)%n", place++,
                        scoreNames.getOrDefault(entry.getKey(), "Jogador"), entry.getValue()));
            }
        }
        scoreboardArea.setText(text.toString());
        scoreboardArea.setCaretPosition(0);
    }

    private List<Map.Entry<String, Integer>> sortedScores() {
        List<Map.Entry<String, Integer>> ranking = new ArrayList<>(scores.entrySet());
        ranking.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(entry -> scoreNames.getOrDefault(entry.getKey(), "Jogador"),
                        String.CASE_INSENSITIVE_ORDER));
        return ranking;
    }

    private void showHistory() {
        List<QuizStore.PlayerStats> stats = store.getRanking();
        StringBuilder text = new StringBuilder();
        text.append("Arquivo local: ~/.pitiupi/quizamigos/history.properties\n\n");
        if (stats.isEmpty()) text.append("Ainda não há partidas finalizadas neste computador.\n");
        else {
            text.append("POS.  JOGADOR                       PONTOS   VITÓRIAS   PARTIDAS\n");
            text.append("----------------------------------------------------------------\n");
            int position = 1;
            for (QuizStore.PlayerStats row : stats) {
                text.append(String.format("%-5d %-28s %-8d %-10d %d%n", position++,
                        row.name(), row.points(), row.wins(), row.games()));
            }
        }
        text.append("\nEste ranking existe apenas nesta instalação. Cada computador guarda seu próprio histórico.");
        historyArea.setText(text.toString());
        historyArea.setCaretPosition(0);
        cardLayout.show(cards, CARD_HISTORY);
    }

    private void exitToHome() {
        if (roundActive) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Sair agora encerra sua participação local. Se este computador coordena a partida, "
                            + "os demais podem ficar sem a revelação desta rodada. Continuar?",
                    "Sair do quiz", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        stopTimers();
        roundActive = false;
        showIntro(false);
    }

    private void showIntro(boolean entering) {
        cardLayout.show(cards, CARD_INTRO);
        if (entering) {
            introPanel.animateEntrance(() -> {
                cardLayout.show(cards, CARD_HOME);
            });
        } else {
            introPanel.animateExit(() -> {
                cardLayout.show(cards, CARD_HOME);
                if (!musicPlayer.isPlaying()) musicPlayer.playLoopWithFadeIn();
            });
        }
    }

    private void stopTimers() {
        if (countdownTimer != null) countdownTimer.stop();
        if (nextQuestionTimer != null) nextQuestionTimer.stop();
        if (transitionTimer != null) transitionTimer.stop();
    }

    private void stopCountdownOnly() {
        if (countdownTimer != null) countdownTimer.stop();
    }

    void onTabClosed() {
        stopTimers();
        introPanel.stopAnimation();
        roundActive = false;
        musicPlayer.shutdown();
        cardLayout.show(cards, CARD_HOME);
    }

    private int sendToRoom(QuizMessage message) {
        try {
            return MessageDispatcher.send(connectionManager, message,
                    SendTarget.broadcastAll(), TransportMode.TCP);
        } catch (RuntimeException ex) {
            gameStatusLabel.setText("Falha ao enviar pela rede: " + ex.getMessage());
            return 0;
        }
    }

    private void lockAnswerControls() {
        for (JRadioButton button : answerButtons) button.setEnabled(false);
        submitAnswerButton.setEnabled(false);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Quiz entre amigos", JOptionPane.WARNING_MESSAGE);
    }

    private static JPanel transparentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel transparentPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel retroLabel(String text, int size, boolean bold) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(RetroPanel.PALE);
        label.setFont(new Font(Font.MONOSPACED, bold ? Font.BOLD : Font.PLAIN, size));
        return label;
    }

    private JButton retroButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(91, 37, 146));
        button.setForeground(RetroPanel.PALE);
        button.setFocusPainted(false);
        button.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 132, 255), 1),
                BorderFactory.createEmptyBorder(10, 13, 10, 13)));
        button.setOpaque(true);
        return button;
    }

    private static void styleTextArea(JTextArea area) {
        area.setBackground(new Color(30, 12, 53));
        area.setForeground(RetroPanel.PALE);
        area.setCaretColor(RetroPanel.PINK);
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
    }

    private static final String MAIN_COVER_URL =
            "https://img.magnific.com/vetores-gratis/ilustracao-de-um-jogo-vibrante_1308-181062.jpg?semt=ais_hybrid&w=740&q=80";
    private static final String OLIVIA_COVER_URL =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c4/Olivia_Rodrigo_2.jpeg/500px-Olivia_Rodrigo_2.jpeg";

    private void loadMainCoverAsync() {
        loadImageAsync(MAIN_COVER_URL, "assets/quiz-cover.jpg", image -> {
            coverLabel.setText("");
            coverLabel.setIcon(new ImageIcon(image.getScaledInstance(310, 360, Image.SCALE_SMOOTH)));
            introPanel.setCoverImage(image);
        });
    }

    private void loadOliviaIntroCoverAsync() {
        loadImageAsync(OLIVIA_COVER_URL, "assets/olivia-rodrigo.jpg", image ->
                introPanel.setCoverImage(image));
    }

    private void loadImageAsync(String url, String resource, java.util.function.Consumer<Image> success) {
        new SwingWorker<Image, Void>() {
            @Override protected Image doInBackground() throws Exception {
                java.net.URL bundled = QuizPanel.class.getResource(resource);
                Image original = bundled != null ? ImageIO.read(bundled) : null;
                if (original == null) original = ImageIO.read(URI.create(url).toURL());
                return original;
            }
            @Override protected void done() {
                try {
                    Image image = get();
                    if (image != null) success.accept(image);
                } catch (Exception ignored) {
                    if (resource.contains("quiz-cover")) coverLabel.setText("QUIZ");
                }
            }
        }.execute();
    }

    private static boolean equalsSafe(String a, String b) { return a != null && a.equals(b); }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "Jogador" : name.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;").replace("\n", "<br>");
    }

    @SuppressWarnings("serial")
    private final class IntroPanel extends RetroPanel {
        private Timer animation;
        private float curtainProgress;
        private int coverOffset;
        private Image coverImage;

        IntroPanel() {
            setPreferredSize(new Dimension(850, 560));
            curtainProgress = 0f;
            coverOffset = 620;
        }

        void setCoverImage(Image image) {
            coverImage = image;
            repaint();
        }

        boolean isCurtainOpen() {
            return curtainProgress >= 0.999f;
        }

        void stopAnimation() {
            if (animation != null) animation.stop();
        }

        void animateEntrance(Runnable finished) {
            stopAnimation();
            curtainProgress = 0f;
            coverOffset = Math.max(420, getHeight() + 80);
            long curtainDuration = musicPlayer.playCurtainAndGetDuration();
            animateCurtain(true, curtainDuration, () -> {
                if (!musicPlayer.isPlaying()) musicPlayer.playLoopWithFadeIn();
                animateCover(true, 1_300L, finished);
            });
        }

        void animateExit(Runnable finished) {
            stopAnimation();
            animateCover(false, 1_250L, () -> {
                musicPlayer.stopMusicImmediately();
                long curtainDuration = musicPlayer.playCurtainAndGetDuration();
                animateCurtain(false, curtainDuration, finished);
            });
        }

        private void animateCurtain(boolean opening, long duration, Runnable finished) {
            if (animation != null) animation.stop();
            long started = System.currentTimeMillis();
            animation = new Timer(16, event -> {
                float fraction = Math.min(1f, (System.currentTimeMillis() - started)
                        / (float) Math.max(1L, duration));
                curtainProgress = opening ? fraction : 1f - fraction;
                repaint();
                if (fraction >= 1f) {
                    animation.stop();
                    animation = null;
                    finished.run();
                }
            });
            animation.start();
        }

        private void animateCover(boolean rising, long duration, Runnable finished) {
            if (animation != null) animation.stop();
            int target = Math.max(0, (getHeight() - 410) / 2);
            int start = rising ? Math.max(420, getHeight() + 80) : target;
            int end = rising ? target : Math.max(420, getHeight() + 80);
            coverOffset = start;
            long started = System.currentTimeMillis();
            boolean[] fadeStarted = {false};
            animation = new Timer(16, event -> {
                long elapsed = System.currentTimeMillis() - started;
                float fraction = Math.min(1f, elapsed / (float) Math.max(1L, duration));
                float eased = fraction * fraction * (3f - 2f * fraction);
                coverOffset = Math.round(start + (end - start) * eased);
                if (!rising && !fadeStarted[0] && elapsed >= duration - 550L) {
                    fadeStarted[0] = true;
                    musicPlayer.fadeOutAndStop(550L);
                }
                repaint();
                if (fraction >= 1f) {
                    animation.stop();
                    animation = null;
                    if (!rising) musicPlayer.stopMusicImmediately();
                    finished.run();
                }
            });
            animation.start();
        }

        @Override protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();

            int imageWidth = Math.min(310, Math.max(220, width / 3));
            int imageHeight = 360;
            int imageX = (width - imageWidth) / 2;
            int imageY = (height - imageHeight) / 2 + coverOffset;
            if (coverImage != null) {
                g.setColor(new java.awt.Color(255, 74, 211, 90));
                g.fillRoundRect(imageX - 9, imageY - 9, imageWidth + 18, imageHeight + 18, 24, 24);
                g.drawImage(coverImage, imageX, imageY, imageWidth, imageHeight, this);
            } else {
                g.setPaint(new java.awt.GradientPaint(imageX, imageY, new java.awt.Color(107, 40, 164),
                        imageX + imageWidth, imageY + imageHeight, new java.awt.Color(35, 8, 65)));
                g.fillRoundRect(imageX, imageY, imageWidth, imageHeight, 26, 26);
                g.setColor(RetroPanel.PALE);
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 35));
                g.drawString("QUIZ", imageX + 80, imageY + 185);
            }
            g.setColor(RetroPanel.PALE);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
            String subtitle = "QUIZ";
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2,
                    Math.max(24, imageY + imageHeight + 30));

            paintCurtains(g, width, height);
            g.dispose();
        }

        private void paintCurtains(java.awt.Graphics2D g, int width, int height) {
            float closedFraction = Math.max(0f, Math.min(1f, 1f - curtainProgress));
            int panelWidth = Math.round(width * 0.5f * closedFraction);
            if (panelWidth <= 0) return;
            paintCurtainPanel(g, 0, panelWidth, height, true);
            paintCurtainPanel(g, width - panelWidth, panelWidth, height, false);
            int valanceHeight = Math.round(48f * closedFraction);
            g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(120, 8, 24),
                    0, valanceHeight, new java.awt.Color(56, 3, 14)));
            g.fillRoundRect(0, 0, width, valanceHeight, 22, 22);
            g.setColor(new java.awt.Color(255, 190, 150, 150));
            g.drawLine(0, valanceHeight - 2, width, valanceHeight - 2);
        }

        private void paintCurtainPanel(java.awt.Graphics2D g, int x, int panelWidth,
                int height, boolean left) {
            g.setPaint(new java.awt.GradientPaint(x, 0, new java.awt.Color(151, 12, 34),
                    x + panelWidth, 0, new java.awt.Color(64, 3, 18)));
            g.fillRect(x, 0, panelWidth, height);
            g.setStroke(new java.awt.BasicStroke(2f));
            int folds = Math.max(2, panelWidth / 24);
            for (int i = 1; i <= folds; i++) {
                int foldX = x + i * panelWidth / (folds + 1);
                g.setColor(new java.awt.Color(255, 160, 160, (i % 2 == 0) ? 68 : 30));
                g.drawLine(foldX, 0, foldX + (left ? 5 : -5), height);
            }
            g.setColor(new java.awt.Color(255, 215, 185, 115));
            int edgeX = left ? x + panelWidth - 2 : x + 1;
            g.drawLine(edgeX, 0, edgeX, height);
        }
    }
}
