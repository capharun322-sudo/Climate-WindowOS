import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** Climate WindowOS: a small desktop that hosts the apps. */
public class Desk extends MIDlet implements DeskCanvas.Host, AsistanApp.Host {
    private Display display;
    private DeskCanvas cv;
    private PyApp py;
    private NotesApp notes;
    private CalcApp calc;
    private FileApp files;
    private AsistanApp asistan;
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
            } else if (app == 5) {
                if (asistan == null) {
                    asistan = new AsistanApp(display, back, this);
                }
                asistan.start();
            } else if (app == 4) {
                msg("Climate WindowOS", "S\u00fcr\u00fcm 0.2\nAlcatel i\u00e7in masa\u00fcst\u00fc.\nPython, Notlar, Hesap makinesi, Dosyalar, Asistan.");
            }
        } catch (Throwable t) {
            msg("Hata", "Uygulama a\u00e7\u0131lamad\u0131: " + t);
        }
    }

    private void openFiles() {
        String err;
        try {
            err = FileApp.check();
        } catch (Throwable t) {
            err = String.valueOf(t);
        }
        if (err != null) {
            Alert a = new Alert("Dosyalar", "Dosya eri\u015fimi a\u00e7\u0131lamad\u0131:\n" + err, null, AlertType.ERROR);
            a.setTimeout(Alert.FOREVER);
            display.setCurrent(a, cv);
            return;
        }
        if (files == null) {
            files = new FileApp(display, back);
        }
        files.start();
    }

    public void goToFolder(String dir) {
        try {
            String err = FileApp.check();
            if (err != null) {
                msg("Dosyalar", err);
                return;
            }
            if (files == null) {
                files = new FileApp(display, back);
            }
            files.goTo(dir);
        } catch (Throwable t) {
            msg("Hata", String.valueOf(t));
        }
    }
}
