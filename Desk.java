import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** Climate WindowOS: a small desktop that hosts the apps. */
public class Desk extends MIDlet implements DeskCanvas.Host {
    private Display display;
    private DeskCanvas cv;
    private PyApp py;
    private NotesApp notes;
    private CalcApp calc;
    private FileApp files;
    private Runnable back = new Runnable() {
        public void run() {
            display.setCurrent(cv);
        }
    };

    public void startApp() {
        if (cv != null) {
            return;
        }
        display = Display.getDisplay(this);
        cv = new DeskCanvas(this);
        display.setCurrent(cv);
        cv.begin();
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
        if (py != null) {
            py.destroyApp(true);
        }
        if (cv != null) {
            cv.stopThreads();
        }
    }

    public void quit() {
        destroyApp(true);
        notifyDestroyed();
    }

    private void msg(String title, String text) {
        Alert a = new Alert(title, text, null, AlertType.INFO);
        a.setTimeout(3000);
        display.setCurrent(a, cv);
    }

    public void launch(int app) {
        try {
            if (app == 0) {
                openFiles();
            } else if (app == 1) {
                if (py == null) {
                    py = new PyApp(this, back);
                }
                py.start();
            } else if (app == 2) {
                if (notes == null) {
                    notes = new NotesApp(display, back);
                }
                notes.start();
            } else if (app == 3) {
                if (calc == null) {
                    calc = new CalcApp(display, back);
                }
                calc.start();
            } else if (app == 4) {
                msg("Climate WindowOS", "S\u00fcr\u00fcm 0.1\nAlcatel i\u00e7in masa\u00fcst\u00fc.\nPython, Notlar, Hesap makinesi, Dosyalar.");
            }
        } catch (Throwable t) {
            msg("Hata", "Uygulama a\u00e7\u0131lamad\u0131: " + t);
        }
    }

    private void openFiles() {
        boolean ok = false;
        try {
            ok = FileApp.supported();
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            msg("Dosyalar", "Bu telefon dosya eri\u015fimini (JSR-75) desteklemiyor.");
            return;
        }
        if (files == null) {
            files = new FileApp(display, back);
        }
        files.start();
    }
}
