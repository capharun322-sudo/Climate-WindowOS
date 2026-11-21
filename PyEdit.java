import javax.microedition.midlet.MIDlet;

/** Stand-alone PyPhone (the editor also runs inside the Climate WindowOS desktop). */
public class PyEdit extends MIDlet {
    private PyApp app;

    public void startApp() {
        if (app == null) {
            app = new PyApp(this, new Runnable() {
                public void run() {
                    notifyDestroyed();
                }
            });
        }
        app.start();
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
        if (app != null) {
            app.destroyApp(unconditional);
        }
    }
}
