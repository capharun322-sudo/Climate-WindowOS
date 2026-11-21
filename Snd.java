import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * Sound helper. Uses MMAPI (optional on old phones), so every caller wraps
 * calls in try/catch Throwable. This class is never packaged with javax.*.
 */
public class Snd {
    private static Player player;
    private static final String[] TYPES = {"audio/x-wav", "audio/wav", "audio/wave"};

    /** Plays a WAV from the jar. Returns true if some player started. */
    public static synchronized boolean play(String res) {
        stop();
        for (int i = 0; i < TYPES.length; i++) {
            InputStream is = null;
            try {
                is = Snd.class.getResourceAsStream(res);
                if (is == null) {
                    return false;
                }
                Player p = Manager.createPlayer(is, TYPES[i]);
                p.realize();
                try {
                    VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
                    if (vc != null) {
                        vc.setLevel(80);
                    }
                } catch (Throwable t) {
                    // volume control is optional
                }
                p.prefetch();
                p.start();
                player = p;
                return true;
            } catch (Throwable t) {
                // try the next MIME type
            }
        }
        return false;
    }

    public static synchronized void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable t) {
                // ignore
            }
            try {
                player.close();
            } catch (Throwable t) {
                // ignore
            }
            player = null;
        }
    }

    /** Fallback: a short rising tune made of simple tones. */
    public static void tune(boolean up) {
        int[] n = up ? new int[] {67, 72, 76, 79, 84} : new int[] {84, 79, 72, 67};
        for (int i = 0; i < n.length; i++) {
            try {
                Manager.playTone(n[i], i == n.length - 1 ? 700 : 260, 80);
                Thread.sleep(i == n.length - 1 ? 700 : 280);
            } catch (Throwable t) {
                return;
            }
        }
    }

    public static void click() {
        try {
            Manager.playTone(88, 40, 60);
        } catch (Throwable t) {
            // ignore
        }
    }
}
