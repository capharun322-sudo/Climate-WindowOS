import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;

/**
 * File browser (JSR-75). Kept in its own class so that phones without the
 * file API only fail here, not in the rest of the desktop.
 */
public class FileApp implements CommandListener {
    private Display display;
    private Runnable onExit;
    private List list;
    private Form view;
    private String path = null; // null = list of drives
    private Vector names = new Vector();
    private Command cUp = new Command("\u00dcst klas\u00f6r", Command.BACK, 1);
    private Command cExit = new Command("Kapat", Command.SCREEN, 2);
    private Command cBack = new Command("Geri", Command.BACK, 1);

    public FileApp(Display d, Runnable onExit) {
        this.display = d;
        this.onExit = onExit;
    }

    /** True when the phone offers the file API. */
    public static boolean supported() {
        try {
            FileSystemRegistry.listRoots();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void start() {
        path = null;
        show();
    }

    private void fail(String msg) {
        Alert a = new Alert("Dosyalar", msg, null, AlertType.ERROR);
        a.setTimeout(2500);
        if (list != null) {
            display.setCurrent(a, list);
        } else {
            display.setCurrent(a);
        }
    }

    private boolean show() {
        List l = new List(path == null ? "Dosyalar" : path, List.IMPLICIT);
        Vector nm = new Vector();
        try {
            if (path == null) {
                Enumeration e = FileSystemRegistry.listRoots();
                while (e.hasMoreElements()) {
                    String r = (String) e.nextElement();
                    nm.addElement(r);
                    l.append("[S\u00fcr\u00fcc\u00fc] " + r, null);
                }
            } else {
                FileConnection fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
                try {
                    Enumeration e = fc.list();
                    Vector dirs = new Vector();
                    Vector files = new Vector();
                    while (e.hasMoreElements()) {
                        String n = (String) e.nextElement();
                        if (n.endsWith("/")) {
                            dirs.addElement(n);
                        } else {
                            files.addElement(n);
                        }
                    }
                    for (int i = 0; i < dirs.size(); i++) {
                        String n = (String) dirs.elementAt(i);
                        nm.addElement(n);
                        l.append("[Klas\u00f6r] " + n, null);
                    }
                    for (int i = 0; i < files.size(); i++) {
                        String n = (String) files.elementAt(i);
                        nm.addElement(n);
                        l.append(n, null);
                    }
                } finally {
                    fc.close();
                }
            }
        } catch (SecurityException se) {
            fail("\u0130zin verilmedi. Telefon sorarsa Evet de.");
            return false;
        } catch (Throwable t) {
            fail("A\u00e7\u0131lamad\u0131: " + t);
            return false;
        }
        if (nm.size() == 0) {
            l.append("(bo\u015f)", null);
        }
        l.addCommand(cUp);
        l.addCommand(cExit);
        l.setCommandListener(this);
        names = nm;
        list = l;
        display.setCurrent(list);
        return true;
    }

    private void up() {
        if (path == null) {
            onExit.run();
            return;
        }
        String p = path.substring(0, path.length() - 1); // drop trailing '/'
        int i = p.lastIndexOf('/');
        path = (i < 0) ? null : p.substring(0, i + 1);
        show();
    }

    private static boolean isText(String n) {
        String s = n.toLowerCase();
        return s.endsWith(".txt") || s.endsWith(".py") || s.endsWith(".log") || s.endsWith(".csv")
                || s.endsWith(".java") || s.endsWith(".xml") || s.endsWith(".html") || s.endsWith(".jad")
                || s.endsWith(".ini");
    }

    private void open(String name) {
        String full = path + name;
        FileConnection fc = null;
        InputStream is = null;
        try {
            fc = (FileConnection) Connector.open("file:///" + full, Connector.READ);
            long size = fc.fileSize();
            Form f = new Form(name);
            f.append("Boyut: " + size + " bayt\n");
            if (isText(name) && size <= 20000) {
                is = fc.openInputStream();
                int max = (int) Math.min(size, 3000);
                byte[] b = new byte[max];
                int n = 0;
                while (n < max) {
                    int r = is.read(b, n, max - n);
                    if (r < 0) {
                        break;
                    }
                    n += r;
                }
                String txt = new String(b, 0, n, "UTF-8");
                f.append(txt);
                if (size > max) {
                    f.append("\n... (devam\u0131 g\u00f6sterilmiyor)");
                }
            } else {
                f.append("Bu dosya t\u00fcr\u00fc g\u00f6sterilemiyor.");
            }
            f.addCommand(cBack);
            f.setCommandListener(this);
            view = f;
            display.setCurrent(f);
        } catch (Throwable t) {
            fail("A\u00e7\u0131lamad\u0131: " + t);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
                if (fc != null) {
                    fc.close();
                }
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cExit) {
            onExit.run();
        } else if (c == cUp) {
            up();
        } else if (c == cBack) {
            display.setCurrent(list);
        } else if (c == List.SELECT_COMMAND) {
            int i = list.getSelectedIndex();
            if (i < 0 || i >= names.size()) {
                return;
            }
            String n = (String) names.elementAt(i);
            String old = path;
            if (path == null) {
                path = n;
                if (!show()) {
                    path = old;
                }
            } else if (n.endsWith("/")) {
                path = path + n;
                if (!show()) {
                    path = old;
                }
            } else {
                open(n);
            }
        }
    }
}
