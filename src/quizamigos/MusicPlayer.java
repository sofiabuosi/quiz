package quizamigos;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.swing.Timer;

final class MusicPlayer {

    enum Effect {
        REVEAL_TO_SCORE("assets/reveal-to-score.wav"),
        SCOREBOARD("assets/scoreboard.wav"),
        CURTAIN("assets/curtain.wav");

        private final String resource;
        Effect(String resource) { this.resource = resource; }
    }

    private static final String MUSIC_RESOURCE = "assets/music.wav";
    private static final long DEFAULT_CURTAIN_MILLIS = 1_400L;
    private final Map<Effect, Clip> effectClips = new EnumMap<>(Effect.class);
    private Clip musicClip;
    private Timer fadeTimer;

    MusicPlayer() { }

    boolean isPlaying() {
        return musicClip != null && musicClip.isOpen() && musicClip.isRunning();
    }

    void playLoopWithFadeIn() {
        stopMusicImmediately();
        try (AudioInputStream stream = openMusicStream()) {
            if (stream == null) return;
            musicClip = AudioSystem.getClip();
            musicClip.open(stream);
            musicClip.setFramePosition(0);
            if (musicClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) musicClip.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(), -42f));
            }
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            fadeMusicTo(0f, 1_500L, false);
        } catch (Exception ex) {
            stopMusicImmediately();
        }
    }

    long playEffect(Effect effect) {
        long duration = effectDurationMillis(effect);
        try (AudioInputStream stream = openEffectStream(effect)) {
            if (stream == null) return duration;
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            Clip previous = effectClips.put(effect, clip);
            if (previous != null && previous.isOpen()) {
                previous.stop();
                previous.close();
            }
            LineListener cleanup = event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    Clip current = effectClips.get(effect);
                    if (current == clip) effectClips.remove(effect);
                    if (clip.isOpen()) clip.close();
                }
            };
            clip.addLineListener(cleanup);
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception ignored) {
        }
        return duration;
    }

    private long effectDurationMillis(Effect effect) {
        try (AudioInputStream stream = openEffectStream(effect)) {
            if (stream == null) return 0L;
            long frames = stream.getFrameLength();
            float rate = stream.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) return Math.max(1L, Math.round(frames * 1000d / rate));
        } catch (Exception ignored) { }
        return 0L;
    }

    long playCurtainAndGetDuration() {
        long duration = playEffect(Effect.CURTAIN);
        return duration > 0L ? duration : DEFAULT_CURTAIN_MILLIS;
    }

    void fadeOutAndStop(long durationMillis) {
        if (musicClip == null || !musicClip.isOpen()) return;
        fadeMusicTo(-45f, durationMillis, true);
    }

    void stopMusicImmediately() {
        if (fadeTimer != null) {
            fadeTimer.stop();
            fadeTimer = null;
        }
        if (musicClip != null) {
            try { musicClip.stop(); musicClip.close(); } catch (RuntimeException ignored) { }
            musicClip = null;
        }
    }

    void shutdown() {
        stopMusicImmediately();
        for (Clip clip : new ArrayList<>(effectClips.values())) {
            try { clip.stop(); clip.close(); } catch (RuntimeException ignored) { }
        }
        effectClips.clear();
    }

    private AudioInputStream openMusicStream() throws Exception {
        InputStream resource = getClass().getResourceAsStream(MUSIC_RESOURCE);
        return resource == null ? null : AudioSystem.getAudioInputStream(new BufferedInputStream(resource));
    }

    private AudioInputStream openEffectStream(Effect effect) throws Exception {
        InputStream resource = getClass().getResourceAsStream(effect.resource);
        return resource == null ? null : AudioSystem.getAudioInputStream(new BufferedInputStream(resource));
    }

    private void fadeMusicTo(float targetDb, long durationMillis, boolean closeWhenDone) {
        if (fadeTimer != null) fadeTimer.stop();
        if (musicClip == null || !musicClip.isOpen()
                || !musicClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            if (closeWhenDone) stopMusicImmediately();
            return;
        }
        FloatControl gain = (FloatControl) musicClip.getControl(FloatControl.Type.MASTER_GAIN);
        float start = gain.getValue();
        float boundedTarget = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), targetDb));
        long started = System.currentTimeMillis();
        fadeTimer = new Timer(25, event -> {
            float fraction = Math.min(1f, (System.currentTimeMillis() - started)
                    / (float) Math.max(1L, durationMillis));
            gain.setValue(start + ((boundedTarget - start) * fraction));
            if (fraction >= 1f) {
                fadeTimer.stop();
                fadeTimer = null;
                if (closeWhenDone) stopMusicImmediately();
            }
        });
        fadeTimer.start();
    }

}
