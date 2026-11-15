package quizamigos;

public final class MusicPlayerAudioTest {
    private MusicPlayerAudioTest() { }

    public static void main(String[] args) {
        MusicPlayer player = new MusicPlayer();
        boolean bundledCurtain = MusicPlayer.class.getResource("assets/curtain.wav") != null;
        boolean bundledReveal = MusicPlayer.class.getResource("assets/reveal-to-score.wav") != null;
        boolean bundledScoreboard = MusicPlayer.class.getResource("assets/scoreboard.wav") != null;
        long curtainDuration = player.playCurtainAndGetDuration();
        if (curtainDuration <= 0L || (!bundledCurtain && curtainDuration != 1_400L)) {
            throw new AssertionError("duração da cortina ou fallback incorreto");
        }
        if (MusicPlayer.Effect.values().length != 3) {
            throw new AssertionError("devem existir somente os três efeitos do jogo, sem som de clique");
        }
        if (bundledReveal && player.playEffect(MusicPlayer.Effect.REVEAL_TO_SCORE) <= 0L) {
            throw new AssertionError("efeito de transição deveria ter duração positiva");
        }
        if (bundledScoreboard && player.playEffect(MusicPlayer.Effect.SCOREBOARD) <= 0L) {
            throw new AssertionError("efeito de placar deveria ter duração positiva");
        }
        player.shutdown();
        System.out.println("OK: efeitos do jogo, ausência de som de clique e fallback da cortina.");
    }
}
