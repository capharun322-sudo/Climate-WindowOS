import java.io.InputStream;
import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

/** Chat screen of the offline mini assistant. */
public class AsistanApp implements CommandListener {
    public interface Host {
        void launch(int app);

        void goToFolder(String dir);
    }

    private static final String UNK = "cwos_unk";
    private static final String CODES = "pyfiles";

    private Display display;
    private Runnable onExit;
    private Host host;
    private Brain brain;
    private Form chat;
    private TextField in;
    private StringBuffer log = new StringBuffer();
    private List results;
    private Vector resultPaths = new Vector();
    private List codeList;
    private Vector codeNames = new Vector();
    private Form busy;
    private int[] stop = new int[1];

    private Command cSend = new Command("G\u00f6nder", Command.OK, 1);
    private Command cClear = new Command("Temizle", Command.SCREEN, 2);
    private Command cUnk = new Command("Bilmediklerim", Command.SCREEN, 3);
    private Command cBack = new Command("Kapat", Command.BACK, 1);
    private Command cCancel = new Command("\u0130ptal", Command.CANCEL, 1);
    private Command cToChat = new Command("Geri", Command.BACK, 1);

    public AsistanApp(Display d, Runnable onExit, Host host) {
        this.display = d;
        this.onExit = onExit;
        this.host = host;
    }

    public void start() {
        if (brain == null) {
            brain = new Brain(readKb());
            log.append("Asistan: Merhaba! Dosya ara, Python sor, kodunu kontrol ettir. 'yard\u0131m' yaz.\n\n");
            chat = new Form("Asistan");
            in = new TextField("Sen:", "", 120, TextField.ANY);
            chat.addCommand(cSend);
            chat.addCommand(cClear);
            chat.addCommand(cUnk);
            chat.addCommand(cBack);
            chat.setCommandListener(this);
        }
        showChat();
    }

    private String readKb() {
        InputStream is = null;
        try {
            is = AsistanApp.class.getResourceAsStream("/bilgi.txt");
            if (is == null) {
                return "";
            }
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[512];
            int n;
            while ((n = is.read(b)) > 0) {
                bo.write(b, 0, n);
            }
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    private void showChat() {
        chat.deleteAll();
        String s = log.toString();
        if (s.length() > 1400) {
            s = s.substring(s.length() - 1400);
            int nl = s.indexOf('\n');
            if (nl >= 0) {
                s = s.substring(nl + 1);
            }
        }
        chat.append(new StringItem(null, s));
        chat.append(in);
        display.setCurrent(chat);
        try {
            display.setCurrentItem(in);
        } catch (Throwable t) {
            // not essential
        }
    }

    private void say(String who, String text) {
        log.append(who).append(": ").append(text).append("\n\n");
    }

    // ------------------------------------------------------------ commands
    public void commandAction(Command c, Displayable d) {
        if (c == cBack) {
            onExit.run();
        } else if (c == cClear) {
            log.setLength(0);
            in.setString("");
            showChat();
        } else if (c == cSend) {
            send();
        } else if (c == cUnk) {
            showUnknown();
        } else if (c == cToChat) {
            showChat();
        } else if (c == cCancel) {
            stop[0] = 1;
        } else if (d == results && c == List.SELECT_COMMAND) {
            int i = results.getSelectedIndex();
            if (i >= 0 && i < resultPaths.size()) {
                String p = (String) resultPaths.elementAt(i);
                host.goToFolder(p.substring(0, p.lastIndexOf('/') + 1));
            }
        } else if (d == codeList && c == List.SELECT_COMMAND) {
            int i = codeList.getSelectedIndex();
            if (i >= 0 && i < codeNames.size()) {
                checkCode((String) codeNames.elementAt(i));
            }
        }
    }

    private void send() {
        String q = in.getString().trim();
        if (q.length() == 0) {
            return;
        }
        in.setString("");
        say("Sen", q);
        Brain.Reply r;
        try {
            r = brain.ask(q);
        } catch (Throwable t) {
            say("Asistan", "Bir hata oldu: " + t);
            showChat();
            return;
        }
        say("Asistan", r.text);
        if (brain.lastUnknown != null) {
            saveUnknown(brain.lastUnknown);
        }
        showChat();
        if (r.act == Brain.OPEN_APP) {
            host.launch(r.app);
        } else if (r.act == Brain.FIND) {
            startSearch(r.exts, r.name);
        } else if (r.act == Brain.CHECK_CODE) {
            showCodes();
        }
    }

    // ------------------------------------------------------------ file search
    private void startSearch(final String[] exts, final String name) {
        String err = null;
        try {
            err = FileApp.check();
        } catch (Throwable t) {
            err = String.valueOf(t);
        }
        if (err != null) {
            say("Asistan", "Dosyalara bakamad\u0131m: " + err);
            showChat();
            return;
        }
        stop[0] = 0;
        busy = new Form("Aran\u0131yor");
        busy.append("Telefon taran\u0131yor, biraz s\u00fcrebilir...");
        busy.addCommand(cCancel);
        busy.setCommandListener(this);
        display.setCurrent(busy);
        new Thread(new Runnable() {
            public void run() {
                Vector res = null;
                String e = null;
                try {
                    res = FileFind.search(exts, name, stop);
                } catch (SecurityException se) {
                    e = "\u0130zin verilmedi.";
                } catch (Throwable t) {
                    e = String.valueOf(t);
                }
                searchDone(res, e);
            }
        }).start();
    }

    private void searchDone(Vector res, String err) {
        if (err != null) {
            say("Asistan", "Arama olmad\u0131: " + err);
            showChat();
            return;
        }
        if (stop[0] != 0) {
            say("Asistan", "Aramay\u0131 durdurdun.");
            showChat();
            return;
        }
        if (res == null || res.size() == 0) {
            say("Asistan", "Bulamad\u0131m. Ad\u0131n\u0131n bir k\u0131sm\u0131n\u0131 ya da ba\u015fka bir ad dener misin? (\u00d6rnek: a\u00e7\u0131l\u0131\u015f sesini bul)");
            showChat();
            return;
        }
        resultPaths = res;
        results = new List("Bulunanlar (" + res.size() + ")", List.IMPLICIT);
        for (int i = 0; i < res.size(); i++) {
            String p = (String) res.elementAt(i);
            int k = p.lastIndexOf('/');
            results.append(p.substring(k + 1) + "  (" + p.substring(0, k + 1) + ")", null);
        }
        results.addCommand(cToChat);
        results.setCommandListener(this);
        say("Asistan", res.size() + " tane buldum. Birini se\u00e7ersen klas\u00f6r\u00fcne g\u00f6t\u00fcr\u00fcr\u00fcm.");
        display.setCurrent(results);
    }

    // ------------------------------------------------------------ code check
    private Vector savedPrograms() {
        Vector v = new Vector();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(CODES, true);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    String s = new String(rs.getRecord(en.nextRecordId()), "UTF-8");
                    int nl = s.indexOf('\n');
                    if (nl >= 0) {
                        v.addElement(s.substring(0, nl));
                    }
                }
            } finally {
                en.destroy();
            }
        } catch (Throwable t) {
            // empty
        } finally {
            close(rs);
        }
        return v;
    }

    private String programText(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(CODES, true);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    String s = new String(rs.getRecord(en.nextRecordId()), "UTF-8");
                    int nl = s.indexOf('\n');
                    if (nl >= 0 && s.substring(0, nl).equals(name)) {
                        return s.substring(nl + 1);
                    }
                }
            } finally {
                en.destroy();
            }
        } catch (Throwable t) {
            // not found
        } finally {
            close(rs);
        }
        return null;
    }

    private void showCodes() {
        codeNames = savedPrograms();
        if (codeNames.size() == 0) {
            say("Asistan", "Python'da kay\u0131tl\u0131 program\u0131n yok. \u00d6nce bir program kaydet.");
            showChat();
            return;
        }
        codeList = new List("Hangi program?", List.IMPLICIT);
        for (int i = 0; i < codeNames.size(); i++) {
            codeList.append((String) codeNames.elementAt(i), null);
        }
        codeList.addCommand(cToChat);
        codeList.setCommandListener(this);
        display.setCurrent(codeList);
    }

    /** Runs a saved program (max 3 seconds) and explains what happened. */
    public static String analyze(String src) {
        final StringBuffer out = new StringBuffer();
        final boolean[] asked = new boolean[1];
        PyIO io = new PyIO() {
            public void print(String s) {
                if (out.length() < 300) {
                    out.append(s);
                }
            }

            public String input(String p) {
                asked[0] = true;
                return null;
            }
        };
        final PyInterp interp = new PyInterp(io);
        final boolean[] done = new boolean[1];
        Thread wd = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 30 && !done[0]; i++) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (!done[0]) {
                    interp.stopped = true;
                }
            }
        });
        wd.start();
        try {
            interp.run(src);
            return "Hata bulamad\u0131m, program sorunsuz bitti." + (out.length() > 0 ? "\n\n\u00c7\u0131kt\u0131:\n" + out.toString().trim() : "");
        } catch (PyError e) {
            if (e.line == 0 || interp.stopped) {
                if (asked[0]) {
                    return "Program senden bir giri\u015f (input) bekliyor. Buraya kadar hata g\u00f6rmedim." + (out.length() > 0 ? "\n\n\u00c7\u0131kt\u0131:\n" + out.toString().trim() : "");
                }
                return "Program 3 saniyede bitmedi. Sonsuz d\u00f6ng\u00fc olabilir: while ko\u015fulunun bir g\u00fcn yanl\u0131\u015f olaca\u011f\u0131ndan emin ol.";
            }
            String[] ls = splitLines(src);
            String lt = (e.line >= 1 && e.line <= ls.length) ? ls[e.line - 1] : "";
            PyHint.Info info = PyHint.analyze(e.getMessage(), lt, src);
            return PyHint.format(e.line, lt, info);
        } catch (Throwable t) {
            return "Kontrol s\u0131ras\u0131nda hata: " + t;
        } finally {
            done[0] = true;
        }
    }

    private static String[] splitLines(String s) {
        Vector v = new Vector();
        int i = 0;
        while (i <= s.length()) {
            int j = s.indexOf('\n', i);
            if (j < 0) {
                j = s.length();
            }
            v.addElement(s.substring(i, j));
            i = j + 1;
        }
        String[] r = new String[v.size()];
        for (int k = 0; k < r.length; k++) {
            r[k] = (String) v.elementAt(k);
        }
        return r;
    }

    private void checkCode(final String name) {
        final String src = programText(name);
        if (src == null) {
            say("Asistan", "Program\u0131 okuyamad\u0131m.");
            showChat();
            return;
        }
        busy = new Form("Kontrol");
        busy.append("\u00c7al\u0131\u015ft\u0131r\u0131p bak\u0131yorum...");
        display.setCurrent(busy);
        new Thread(new Runnable() {
            public void run() {
                String res = analyze(src);
                Form f = new Form("Kontrol: " + name);
                f.append(res);
                f.addCommand(cToChat);
                f.setCommandListener(AsistanApp.this);
                say("Asistan", name + " program\u0131n\u0131 kontrol ettim.");
                display.setCurrent(f);
            }
        }).start();
    }

    // ------------------------------------------------------------ unknown questions
    private void saveUnknown(String q) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(UNK, true);
            if (rs.getNumRecords() >= 30) {
                RecordEnumeration en = rs.enumerateRecords(null, null, false);
                try {
                    if (en.hasNextElement()) {
                        rs.deleteRecord(en.nextRecordId());
                    }
                } finally {
                    en.destroy();
                }
            }
            byte[] b = q.getBytes("UTF-8");
            rs.addRecord(b, 0, b.length);
        } catch (Throwable t) {
            // not important
        } finally {
            close(rs);
        }
    }

    private void showUnknown() {
        StringBuffer sb = new StringBuffer();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(UNK, true);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    sb.append("- ").append(new String(rs.getRecord(en.nextRecordId()), "UTF-8")).append('\n');
                }
            } finally {
                en.destroy();
            }
        } catch (Throwable t) {
            // empty
        } finally {
            close(rs);
        }
        Form f = new Form("Bilmediklerim");
        f.append(sb.length() == 0 ? "Hen\u00fcz yok." : "Anlayamad\u0131\u011f\u0131m sorular (bana yaz, \u00f6\u011freteyim):\n" + sb.toString());
        f.addCommand(cToChat);
        f.setCommandListener(this);
        display.setCurrent(f);
    }

    private void close(RecordStore rs) {
        if (rs != null) {
            try {
                rs.closeRecordStore();
            } catch (Throwable t) {
                // ignore
            }
        }
    }
}
