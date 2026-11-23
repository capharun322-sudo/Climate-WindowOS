import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

/** Offline mini assistant brain: understands short Turkish sentences, asks when unsure. */
public class Brain {
    public static final int NONE = 0, OPEN_APP = 1, FIND = 2, CHECK_CODE = 3;

    public static class Reply {
        public String text = "";
        public int act = NONE;
        public int app = 0;
        public String[] exts = null;
        public String name = "";
    }

    private static final int P_NONE = 0, P_FINDTYPE = 1, P_KB = 2, P_UNKNOWN = 3, P_FINDNAME = 4, P_APP = 5;

    private Vector titles = new Vector();
    private Vector keys = new Vector();   // String[] of normalized stems
    private Vector answers = new Vector(); // String[] variants
    private int[] variantNo = new int[0];
    private int pend = P_NONE;
    private int[] pendKb = null;
    private int unknownRun = 0;
    /** Set when the last question was not understood (so the UI can log it). */
    public String lastUnknown = null;

    public Brain(String kb) {
        load(kb);
    }

    public int entryCount() {
        return titles.size();
    }

    // ------------------------------------------------------------ text helpers
    public static String normalize(String s) {
        StringBuffer sb = new StringBuffer();
        boolean sp = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u0130':
                case 'I':
                case '\u0131':
                    c = 'i';
                    break;
                case '\u00c7':
                case '\u00e7':
                    c = 'c';
                    break;
                case '\u011e':
                case '\u011f':
                    c = 'g';
                    break;
                case '\u00d6':
                case '\u00f6':
                    c = 'o';
                    break;
                case '\u015e':
                case '\u015f':
                    c = 's';
                    break;
                case '\u00dc':
                case '\u00fc':
                    c = 'u';
                    break;
                default:
                    c = Character.toLowerCase(c);
            }
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (ok) {
                sb.append(c);
                sp = false;
            } else if (!sp) {
                sb.append(' ');
                sp = true;
            }
        }
        return sb.toString().trim();
    }

    public static String[] tokens(String norm) {
        Vector v = new Vector();
        int i = 0, n = norm.length();
        while (i < n) {
            int j = norm.indexOf(' ', i);
            if (j < 0) {
                j = n;
            }
            if (j > i) {
                v.addElement(norm.substring(i, j));
            }
            i = j + 1;
        }
        String[] r = new String[v.size()];
        for (int k = 0; k < r.length; k++) {
            r[k] = (String) v.elementAt(k);
        }
        return r;
    }

    private static boolean has(String[] t, String stem) {
        for (int i = 0; i < t.length; i++) {
            if (t[i].startsWith(stem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean is(String[] t, String w) {
        for (int i = 0; i < t.length; i++) {
            if (t[i].equals(w)) {
                return true;
            }
        }
        return false;
    }

    private static String replaceAll(String s, String a, String b) {
        StringBuffer r = new StringBuffer();
        int i = 0, j;
        while ((j = s.indexOf(a, i)) >= 0) {
            r.append(s.substring(i, j)).append(b);
            i = j + a.length();
        }
        r.append(s.substring(i));
        return r.toString();
    }

    // ------------------------------------------------------------ knowledge base
    private void load(String kb) {
        int i = 0, n = kb.length();
        while (i < n) {
            int e = kb.indexOf('\n', i);
            if (e < 0) {
                e = n;
            }
            String line = kb.substring(i, e).trim();
            i = e + 1;
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            int a = line.indexOf('|');
            int b = a < 0 ? -1 : line.indexOf('|', a + 1);
            if (b < 0) {
                continue;
            }
            titles.addElement(line.substring(0, a).trim());
            keys.addElement(tokens(normalize(line.substring(a + 1, b))));
            String ans = replaceAll(line.substring(b + 1).trim(), "\\n", "\n");
            Vector vv = new Vector();
            int p = 0, q;
            while ((q = ans.indexOf('~', p)) >= 0) {
                vv.addElement(ans.substring(p, q).trim());
                p = q + 1;
            }
            vv.addElement(ans.substring(p).trim());
            String[] va = new String[vv.size()];
            for (int k = 0; k < va.length; k++) {
                va[k] = (String) vv.elementAt(k);
            }
            answers.addElement(va);
        }
        variantNo = new int[titles.size()];
    }

    private int score(int entry, String[] t) {
        String[] ks = (String[]) keys.elementAt(entry);
        int sc = 0;
        for (int k = 0; k < ks.length; k++) {
            for (int j = 0; j < t.length; j++) {
                if (t[j].equals(ks[k]) || (ks[k].length() >= 3 && t[j].startsWith(ks[k]))) {
                    sc++;
                    break;
                }
            }
        }
        return sc;
    }

    private String answerOf(int entry) {
        String[] v = (String[]) answers.elementAt(entry);
        String r = v[variantNo[entry] % v.length];
        variantNo[entry]++;
        return r;
    }

    // ------------------------------------------------------------ main entry
    public Reply ask(String input) {
        Reply r = new Reply();
        lastUnknown = null;
        String raw = input.trim();
        if (raw.length() == 0) {
            r.text = "Bir \u015fey yaz, \u00f6rne\u011fin: yard\u0131m";
            return r;
        }
        String norm = normalize(raw);
        String[] t = tokens(norm);

        // answer to my own question
        if (pend != P_NONE) {
            int p = pend;
            if (p == P_FINDNAME) {
                pend = P_NONE;
                if (norm.length() > 0) {
                    return find(null, norm);
                }
            } else if (t.length == 1 && isNum(t[0])) {
                Reply c = choose(p, Integer.parseInt(t[0]));
                if (c != null) {
                    return c;
                }
            }
            pend = P_NONE;
        }

        // time and date
        if (has(t, "saat") && (has(t, "kac") || has(t, "ka") || is(t, "saati"))) {
            r.text = "Saat " + DeskCanvas.clock();
            return r;
        }
        if (has(t, "tarih") || (has(t, "bugun") && (has(t, "gun") || has(t, "ay") || has(t, "ne"))) || (has(t, "hangi") && has(t, "gun"))) {
            r.text = dateText();
            return r;
        }

        // maths
        String expr = mathExpr(raw);
        if (expr != null) {
            String v = CalcApp.eval(expr, null);
            if (v.length() > 0 && !v.startsWith("Hata")) {
                r.text = expr.trim() + " = " + v + "\n(Ondal\u0131k yok, sadece tam say\u0131)";
                return r;
            }
        }

        // check my code
        if (wantsCodeCheck(t)) {
            r.act = CHECK_CODE;
            r.text = "Kay\u0131tl\u0131 programlar\u0131ndan birini se\u00e7, \u00e7al\u0131\u015ft\u0131r\u0131p bakar\u0131m.";
            return r;
        }

        // find a file
        Reply f = tryFind(t, norm);
        if (f != null) {
            return f;
        }

        // open an app
        Reply o = tryOpen(t);
        if (o != null) {
            return o;
        }

        // knowledge base
        int best = 0;
        int[] sc = new int[titles.size()];
        for (int i = 0; i < sc.length; i++) {
            sc[i] = score(i, t);
            if (sc[i] > best) {
                best = sc[i];
            }
        }
        if (best > 0) {
            Vector c = new Vector();
            for (int i = 0; i < sc.length; i++) {
                if (sc[i] == best) {
                    c.addElement(new Integer(i));
                }
            }
            unknownRun = 0;
            if (c.size() == 1) {
                r.text = answerOf(((Integer) c.elementAt(0)).intValue());
                return r;
            }
            int m = c.size() > 4 ? 4 : c.size();
            pendKb = new int[m];
            StringBuffer sb = new StringBuffer("\u015eunlardan hangisini kastettin? Numara yaz:\n");
            for (int i = 0; i < m; i++) {
                pendKb[i] = ((Integer) c.elementAt(i)).intValue();
                sb.append(i + 1).append(") ").append((String) titles.elementAt(pendKb[i])).append('\n');
            }
            pend = P_KB;
            r.text = sb.toString().trim();
            return r;
        }

        // not understood: ask
        lastUnknown = raw;
        unknownRun++;
        pend = P_UNKNOWN;
        r.text = (unknownRun > 1 ? "Yine anlayamad\u0131m, \u00f6z\u00fcr dilerim. " : "Bunu tam anlayamad\u0131m. ")
                + "Hangisi?\n1) Dosya / ses bulmak\n2) Python sorusu\n3) Uygulama a\u00e7mak\n4) G\u00fcnl\u00fck soru\n5) Kodumu kontrol etmek";
        return r;
    }

    private static boolean isNum(String s) {
        if (s.length() == 0 || s.length() > 2) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private Reply choose(int p, int n) {
        Reply r = new Reply();
        pend = P_NONE;
        if (p == P_KB && pendKb != null && n >= 1 && n <= pendKb.length) {
            r.text = answerOf(pendKb[n - 1]);
            return r;
        }
        if (p == P_FINDTYPE) {
            if (n == 7) {
                pend = P_FINDNAME;
                r.text = "Dosyan\u0131n ad\u0131n\u0131 (ya da bir k\u0131sm\u0131n\u0131) yaz:";
                return r;
            }
            String[][] ex = {null, {"mp3", "wav", "amr", "mid", "midi", "aac", "m4a", "ogg"}, {"jpg", "jpeg", "png", "gif", "bmp"},
                {"3gp", "mp4", "avi", "mkv"}, {"txt", "doc", "docx", "pdf", "xls", "ppt"}, {"jar", "jad"}, {"py"}};
            if (n >= 1 && n <= 6) {
                return find(ex[n], "");
            }
        }
        if (p == P_APP && n >= 1 && n <= 4) {
            r.act = OPEN_APP;
            r.app = n - 1;
            r.text = "A\u00e7\u0131yorum...";
            return r;
        }
        if (p == P_UNKNOWN) {
            if (n == 1) {
                return askFindType();
            }
            if (n == 2) {
                r.text = "Python'da neyi \u00f6\u011frenmek istiyorsun? \u00d6rnek: print, if, for d\u00f6ng\u00fcs\u00fc, while, def, liste, hata t\u00fcrleri.";
                return r;
            }
            if (n == 3) {
                return askApp();
            }
            if (n == 4) {
                r.text = "\u00d6rnek sorular: saat ka\u00e7, bug\u00fcn ne g\u00fcn\u00fc, 25*4 ka\u00e7, f\u0131kra anlat, T\u00fcrkiye'nin ba\u015fkenti. Bildi\u011fim kadar\u0131n\u0131 cevaplar\u0131m.";
                return r;
            }
            if (n == 5) {
                r.act = CHECK_CODE;
                r.text = "Kay\u0131tl\u0131 programlar\u0131ndan birini se\u00e7.";
                return r;
            }
        }
        return null;
    }

    private Reply askFindType() {
        Reply r = new Reply();
        pend = P_FINDTYPE;
        r.text = "Ne ar\u0131yorsun?\n1) Ses / m\u00fczik\n2) Resim\n3) Video\n4) Belge\n5) Uygulama / oyun (jar)\n6) Python kodu\n7) Ad\u0131n\u0131 yazarak";
        return r;
    }

    private Reply askApp() {
        Reply r = new Reply();
        pend = P_APP;
        r.text = "Hangisini a\u00e7ay\u0131m?\n1) Dosyalar\n2) Python\n3) Notlar\n4) Hesap makinesi";
        return r;
    }

    private Reply find(String[] exts, String name) {
        Reply r = new Reply();
        r.act = FIND;
        r.exts = exts;
        r.name = name;
        r.text = "Aran\u0131yor" + (name.length() > 0 ? ": " + name : "") + " ...";
        return r;
    }

    // ------------------------------------------------------------ intents
    private static String dateText() {
        String[] days = {"", "Pazar", "Pazartesi", "Sal\u0131", "\u00c7ar\u015famba", "Per\u015fembe", "Cuma", "Cumartesi"};
        String[] mon = {"Ocak", "\u015eubat", "Mart", "Nisan", "May\u0131s", "Haziran", "Temmuz", "A\u011fustos", "Eyl\u00fcl", "Ekim", "Kas\u0131m", "Aral\u0131k"};
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            return c.get(Calendar.DAY_OF_MONTH) + " " + mon[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR) + ", "
                    + days[c.get(Calendar.DAY_OF_WEEK)];
        } catch (Throwable e) {
            return "Tarihi okuyamad\u0131m.";
        }
    }

    /** Returns an arithmetic expression found in the text, or null. */
    static String mathExpr(String raw) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '(' || c == ')'
                    || c == '^' || c == ' ') {
                sb.append(c);
            } else if (c == 'x' || c == 'X' || c == '\u00d7') {
                sb.append('*');
            } else if (c == ':' || c == '\u00f7') {
                sb.append('/');
            }
        }
        String e = sb.toString().trim();
        if (e.length() < 3) {
            return null;
        }
        // needs a digit, an operator between two operands
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^') {
                int p = i - 1;
                while (p >= 0 && e.charAt(p) == ' ') {
                    p--;
                }
                int q = i + 1;
                while (q < e.length() && e.charAt(q) == ' ') {
                    q++;
                }
                if (p >= 0 && q < e.length() && (Character.isDigit(e.charAt(p)) || e.charAt(p) == ')')
                        && (Character.isDigit(e.charAt(q)) || e.charAt(q) == '(' || e.charAt(q) == '-')) {
                    return e;
                }
            }
        }
        return null;
    }

    private static boolean wantsCodeCheck(String[] t) {
        boolean mine = has(t, "kodum") || has(t, "kodlarim") || has(t, "programim") || has(t, "programlarim")
                || (has(t, "kod") && has(t, "kontrol"));
        boolean verb = has(t, "hata") || has(t, "kontrol") || has(t, "calismiyor") || has(t, "bozuk") || has(t, "duzelt")
                || has(t, "bak") || has(t, "neden") || has(t, "sorun") || has(t, "denetle") || has(t, "test")
                || has(t, "calistir");
        return mine && verb && !has(t, "nedir") && !has(t, "demek");
    }

    private Reply tryFind(String[] t, String norm) {
        boolean trig = is(t, "ara") || has(t, "arama") || has(t, "arar") || has(t, "bul") || has(t, "nerde") || has(t, "nerede")
                || has(t, "goster") || has(t, "getir") || has(t, "listele") || has(t, "konum");
        String[] ex = null;
        boolean typed = false;
        if (has(t, "ses") || has(t, "muzik") || has(t, "sarki") || has(t, "melodi") || has(t, "mp3") || has(t, "zil")) {
            ex = new String[] {"mp3", "wav", "amr", "mid", "midi", "aac", "m4a", "ogg"};
            typed = true;
        } else if (has(t, "resim") || has(t, "foto") || has(t, "gorsel")) {
            ex = new String[] {"jpg", "jpeg", "png", "gif", "bmp"};
            typed = true;
        } else if (has(t, "video") || has(t, "film")) {
            ex = new String[] {"3gp", "mp4", "avi", "mkv"};
            typed = true;
        } else if (has(t, "belge") || has(t, "dokuman") || has(t, "pdf") || has(t, "word") || has(t, "excel")) {
            ex = new String[] {"txt", "doc", "docx", "pdf", "xls", "ppt"};
            typed = true;
        } else if (has(t, "oyun") || has(t, "uygulama") || has(t, "jar") || has(t, "program")) {
            ex = new String[] {"jar", "jad"};
            typed = true;
        } else if (trig && (has(t, "python") || is(t, "py") || has(t, "kod"))) {
            ex = new String[] {"py"};
            typed = true;
        }
        boolean fileWord = has(t, "dosya") || has(t, "klasor");
        if (!trig && !(typed && fileWord)) {
            return null;
        }
        // name = the words that are not command words
        StringBuffer nm = new StringBuffer();
        for (int i = 0; i < t.length; i++) {
            String w = t[i];
            if (w.startsWith("ses") || w.startsWith("muzik") || w.startsWith("sarki") || w.startsWith("melodi")
                    || w.startsWith("resim") || w.startsWith("foto") || w.startsWith("video") || w.startsWith("belge")
                    || w.startsWith("dokuman") || w.startsWith("oyun") || w.startsWith("uygulama") || w.startsWith("dosya")
                    || w.startsWith("klasor") || w.startsWith("python") || w.startsWith("bul") || w.startsWith("ara")
                    || w.startsWith("nerde") || w.startsWith("nerede") || w.startsWith("goster") || w.startsWith("getir") || w.startsWith("listele")
                    || w.startsWith("konum") || w.startsWith("bana") || w.startsWith("bir") || w.startsWith("benim")
                    || w.startsWith("adli") || w.startsWith("ismi") || w.startsWith("icinde") || w.equals("mi") || w.equals("mu")
                    || w.equals("ve") || w.equals("kod") || w.equals("py") || w.startsWith("zil") || w.startsWith("film")
                    || w.startsWith("gorsel") || w.startsWith("kaydet") || w.startsWith("telefon")) {
                continue;
            }
            if (nm.length() > 0) {
                nm.append(' ');
            }
            nm.append(w);
        }
        String name = nm.toString();
        if (ex == null && name.length() == 0) {
            return askFindType();
        }
        return find(ex, name);
    }

    private Reply tryOpen(String[] t) {
        boolean open = is(t, "ac") || has(t, "acar") || has(t, "acsana") || has(t, "acin") || has(t, "baslat")
                || is(t, "git") || has(t, "calistir");
        if (!open) {
            return null;
        }
        Reply r = new Reply();
        r.act = OPEN_APP;
        if (has(t, "dosyalar") || (has(t, "dosya") && has(t, "yonet"))) {
            r.app = 0;
        } else if (has(t, "python") || has(t, "pyphone") || has(t, "editor")) {
            r.app = 1;
        } else if (has(t, "not") || has(t, "defter")) {
            r.app = 2;
        } else if (has(t, "hesap")) {
            r.app = 3;
        } else {
            return askApp();
        }
        r.text = "A\u00e7\u0131yorum...";
        return r;
    }
}
