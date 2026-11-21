import java.util.Vector;

/**
 * Debugger helper: turns an interpreter error into a Turkish explanation,
 * the exact place to mark in the line, and (when it is safe) a fixed line.
 */
public class PyHint {
    public static class Info {
        public String type = "";     // e.g. NameError
        public String problem = "";  // what is wrong
        public String fix = "";      // how to fix it
        public String shortMsg = ""; // one line for the status bar
        public int c0 = 0, c1 = 0;   // columns to underline in the line
        public String newLine = null; // fixed line or null
    }

    private static final String[] BUILTINS = {"print", "input", "int", "str", "bool", "len", "abs",
        "min", "max", "sum", "range", "list", "chr", "ord"};
    private static final String[] METHODS = {"upper", "lower", "strip", "startswith", "endswith", "find",
        "count", "isdigit", "replace", "split", "join", "append", "pop", "insert", "remove", "index",
        "reverse", "sort"};

    // ------------------------------------------------------------ helpers

    private static int lead(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static int dist(String a, String b) {
        int[] p = new int[b.length() + 1];
        int[] q = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            p[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            q[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = p[j - 1] + c;
                if (p[j] + 1 < v) {
                    v = p[j] + 1;
                }
                if (q[j - 1] + 1 < v) {
                    v = q[j - 1] + 1;
                }
                q[j] = v;
            }
            int[] t = p;
            p = q;
            q = t;
        }
        return p[b.length()];
    }

    private static boolean isId(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || (c >= '0' && c <= '9');
    }

    /** closest candidate (case-insensitive exact match first), or null */
    private static String closest(String w, Vector cands) {
        String lw = w.toLowerCase();
        for (int i = 0; i < cands.size(); i++) {
            String c = (String) cands.elementAt(i);
            if (!c.equals(w) && c.toLowerCase().equals(lw)) {
                return c;
            }
        }
        int max = w.length() <= 4 ? 1 : 2;
        String best = null;
        int bd = 99;
        for (int i = 0; i < cands.size(); i++) {
            String c = (String) cands.elementAt(i);
            if (c.equals(w) || c.length() < 2) {
                continue;
            }
            int d = dist(lw, c.toLowerCase());
            if (d <= max && d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static void addWords(Vector v, String[] a) {
        for (int i = 0; i < a.length; i++) {
            v.addElement(a[i]);
        }
    }

    /** user-written names in the program (everything PySyntax leaves uncoloured) */
    private static Vector userNames(String src, String skip) {
        Vector v = new Vector();
        int st = 0;
        for (int i = 0; i <= src.length(); i++) {
            if (i == src.length() || src.charAt(i) == '\n') {
                String l = src.substring(st, i);
                st = i + 1;
                byte[] cl = PySyntax.classify(l);
                int k = 0;
                while (k < l.length()) {
                    if (isId(l.charAt(k)) && (cl[k] == PySyntax.N || cl[k] == PySyntax.DEF)
                            && !(l.charAt(k) >= '0' && l.charAt(k) <= '9')) {
                        int e = k;
                        while (e < l.length() && isId(l.charAt(e))) {
                            e++;
                        }
                        String w = l.substring(k, e);
                        if (!w.equals(skip) && !v.contains(w)) {
                            v.addElement(w);
                        }
                        k = e;
                    } else {
                        k++;
                    }
                }
            }
        }
        return v;
    }

    /** first whole-word occurrence of w that is code (not inside a string or comment) */
    private static int findWord(String line, String w) {
        byte[] cl = PySyntax.classify(line);
        int from = 0;
        while (true) {
            int p = line.indexOf(w, from);
            if (p < 0) {
                return -1;
            }
            boolean l = p == 0 || !isId(line.charAt(p - 1));
            boolean r = p + w.length() >= line.length() || !isId(line.charAt(p + w.length()));
            if (l && r && cl[p] != PySyntax.STR && cl[p] != PySyntax.COM) {
                return p;
            }
            from = p + 1;
        }
    }

    /** index where a trailing comment starts (or line length) */
    private static int codeEnd(String line) {
        byte[] cl = PySyntax.classify(line);
        for (int i = 0; i < line.length(); i++) {
            if (cl[i] == PySyntax.COM) {
                int e = i;
                while (e > 0 && line.charAt(e - 1) == ' ') {
                    e--;
                }
                return e;
            }
        }
        int e = line.length();
        while (e > 0 && line.charAt(e - 1) == ' ') {
            e--;
        }
        return e;
    }

    private static String between(String s, String a, String b) {
        int i = s.indexOf(a);
        if (i < 0) {
            return "";
        }
        i += a.length();
        int j = s.indexOf(b, i);
        return j < 0 ? s.substring(i) : s.substring(i, j);
    }

    private static String replaceAt(String s, int c0, int c1, String with) {
        return s.substring(0, c0) + with + s.substring(c1);
    }

    private static int curly(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\u201c' || c == '\u201d' || c == '\u2018' || c == '\u2019') {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithWord(String t, String w) {
        return t.startsWith(w) && (t.length() == w.length() || !isId(t.charAt(w.length())));
    }

    // ------------------------------------------------------------ main entry

    public static Info analyze(String msg, String line, String src) {
        Info in = new Info();
        int colon = msg.indexOf(':');
        in.type = colon > 0 ? msg.substring(0, colon) : "Hata";
        String rest = colon > 0 ? msg.substring(colon + 1).trim() : msg;
        int ld = lead(line);
        int ce = codeEnd(line);
        in.c0 = ld;
        in.c1 = ce > ld ? ce : line.length();
        in.problem = "Python bu sat\u0131r\u0131 \u00e7al\u0131\u015ft\u0131ramad\u0131: " + rest;
        in.fix = "Sat\u0131r\u0131 dikkatle tekrar kontrol et.";
        in.shortMsg = in.type;

        int cq = curly(line);
        if (cq >= 0 && (in.type.equals("NameError") || in.type.equals("SyntaxError"))) {
            in.c0 = cq;
            in.c1 = cq + 1;
            in.problem = "Yaz\u0131da telefonun e\u011fri t\u0131rna\u011f\u0131 (\u201c \u201d) var, Python bunu tan\u0131maz.";
            in.fix = "D\u00fcz t\u0131rnak (\") kullan. A\u015fa\u011f\u0131daki d\u00fczeltilmi\u015f sat\u0131r bunu yapar.";
            in.shortMsg = "E\u011fri t\u0131rnak: d\u00fcz \" kullan";
            StringBuffer fx = new StringBuffer();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\u201c' || c == '\u201d') {
                    fx.append('"');
                } else if (c == '\u2018' || c == '\u2019') {
                    fx.append('\'');
                } else {
                    fx.append(c);
                }
            }
            in.newLine = fx.toString();
        } else if (msg.indexOf("is not defined") >= 0 && in.type.equals("NameError")) {
            String x = between(msg, "name '", "'");
            int p = findWord(line, x);
            if (p >= 0) {
                in.c0 = p;
                in.c1 = p + x.length();
            }
            Vector c = new Vector();
            addWords(c, BUILTINS);
            Vector un = userNames(src, x);
            for (int i = 0; i < un.size(); i++) {
                c.addElement(un.elementAt(i));
            }
            String g = closest(x, c);
            in.problem = "'" + x + "' diye bir \u015fey tan\u0131ml\u0131 de\u011fil.";
            if (g != null) {
                in.fix = "Belki '" + g + "' demek istedin. Python b\u00fcy\u00fck/k\u00fc\u00e7\u00fck harfe duyarl\u0131d\u0131r.";
                in.shortMsg = "'" + x + "' yok. '" + g + "' m\u0131?";
                if (p >= 0) {
                    in.newLine = replaceAt(line, p, p + x.length(), g);
                }
            } else {
                in.fix = "Ad\u0131 yanl\u0131\u015f yazm\u0131\u015f olabilirsin. De\u011fi\u015fkene kullanmadan \u00f6nce de\u011fer ver: "
                        + x + " = 0";
                in.shortMsg = "'" + x + "' tan\u0131ml\u0131 de\u011fil";
            }
        } else if (rest.indexOf("expected ':'") >= 0) {
            in.c0 = ce;
            in.c1 = ce;
            in.problem = "Sat\u0131r\u0131n sonunda ':' eksik.";
            in.fix = "if, elif, else, for, while ve def sat\u0131rlar\u0131 ':' ile biter. Sonuna : koy "
                    + "(Karakterler listesinde var).";
            in.shortMsg = "Sonda ':' eksik";
            in.newLine = line.substring(0, ce) + ":" + line.substring(ce);
        } else if (rest.indexOf("unterminated string") >= 0) {
            byte[] cl = PySyntax.classify(line);
            int p = -1;
            for (int i = 0; i < line.length(); i++) {
                if (cl[i] == PySyntax.STR) {
                    p = i;
                    break;
                }
            }
            if (p < 0) {
                p = ld;
            }
            char q = line.charAt(p);
            if (q != '"' && q != '\'') {
                for (int i = p; i < line.length(); i++) {
                    if (line.charAt(i) == '"' || line.charAt(i) == '\'') {
                        q = line.charAt(i);
                        break;
                    }
                }
            }
            in.c0 = p;
            in.c1 = ce;
            in.problem = "Yaz\u0131n\u0131n t\u0131rna\u011f\u0131 kapanmam\u0131\u015f.";
            in.fix = "Yaz\u0131 ba\u015fka ve sonda ayn\u0131 t\u0131rnakla kapanmal\u0131: " + q + "merhaba" + q;
            in.shortMsg = "T\u0131rnak kapanmam\u0131\u015f";
            if (q == '"' || q == '\'') {
                int e = ce;
                while (e > p + 1 && (line.charAt(e - 1) == ')' || line.charAt(e - 1) == ']'
                        || line.charAt(e - 1) == ':')) {
                    e--;
                }
                in.newLine = line.substring(0, e) + q + line.substring(e);
            }
        } else if (rest.indexOf("invalid syntax") >= 0) {
            syntax(in, line, ld, ce);
        } else if (rest.indexOf("invalid character") >= 0) {
            String ch = between(msg, "character '", "'");
            int p = ch.length() > 0 ? line.indexOf(ch.charAt(0)) : -1;
            if (p >= 0) {
                in.c0 = p;
                in.c1 = p + 1;
            }
            in.problem = "Kodun i\u00e7inde Python'un tan\u0131mad\u0131\u011f\u0131 bir karakter var: " + ch;
            in.fix = "Bu karakteri sil ya da d\u00fczg\u00fcn karakterle de\u011fi\u015ftir. Yaz\u0131lar d\u0131\u015f\u0131nda T\u00fcrk\u00e7e harf kullanma.";
            in.shortMsg = "Ge\u00e7ersiz karakter " + ch;
            if (p >= 0) {
                char c = line.charAt(p);
                if (c == '\u201c' || c == '\u201d') {
                    in.newLine = replaceAt(line, p, p + 1, "\"");
                    in.fix = "E\u011fri t\u0131rnak yerine d\u00fcz t\u0131rnak (\") kullan.";
                } else if (c == '\u2018' || c == '\u2019') {
                    in.newLine = replaceAt(line, p, p + 1, "'");
                    in.fix = "E\u011fri t\u0131rnak yerine d\u00fcz t\u0131rnak (') kullan.";
                }
            }
        } else if (rest.indexOf("decimal numbers") >= 0) {
            int p = line.indexOf('.');
            if (p > 0) {
                in.c0 = p > 0 ? p - 1 : p;
                in.c1 = p + 2;
            }
            in.problem = "Ondal\u0131kl\u0131 say\u0131 (3.5 gibi) bu uygulamada yok.";
            in.fix = "Tam say\u0131 kullan. 3.5 yerine 35 yaz ve gerekiyorsa sonra 10'a b\u00f6l.";
            in.shortMsg = "Ondal\u0131k say\u0131 yok";
        } else if (rest.indexOf("expected an indented block") >= 0) {
            in.problem = "':' ile biten sat\u0131r\u0131n alt\u0131nda i\u00e7eride yaz\u0131lm\u0131\u015f sat\u0131r yok.";
            in.fix = "Bir alttaki sat\u0131r\u0131n ba\u015f\u0131na 4 bo\u015fluk koy (Karakterler > TAB).";
            in.shortMsg = "Alt sat\u0131r i\u00e7eride olmal\u0131";
        } else if (rest.indexOf("unexpected indent") >= 0) {
            in.c0 = 0;
            in.c1 = ld > 0 ? ld : in.c1;
            in.problem = "Bu sat\u0131r gereksiz i\u00e7eride.";
            in.fix = "Ba\u015ftaki bo\u015fluklar\u0131 sil. E\u011fer bir if/for/while'in i\u00e7i olacaksa \u00fcstteki sat\u0131r\u0131n sonuna ':' koy.";
            in.shortMsg = "Gereksiz girinti";
        } else if (rest.indexOf("without matching 'if'") >= 0) {
            in.problem = "else/elif, kendi if'inin hemen alt\u0131nda ve ayn\u0131 hizada olmal\u0131.";
            in.fix = "else/elif sat\u0131r\u0131n\u0131n ba\u015f\u0131ndaki bo\u015fluk say\u0131s\u0131n\u0131 if ile ayn\u0131 yap.";
            in.shortMsg = "else/elif if'siz";
        } else if (rest.indexOf("outside") >= 0) {
            in.problem = "break/continue sadece for ya da while d\u00f6ng\u00fcs\u00fcn\u00fcn i\u00e7inde, return sadece def i\u00e7inde kullan\u0131l\u0131r.";
            in.fix = "Sat\u0131r\u0131 ilgili d\u00f6ng\u00fcn\u00fcn ya da fonksiyonun i\u00e7ine (4 bo\u015fluk i\u00e7eri) al.";
            in.shortMsg = "Yanl\u0131\u015f yerde " + (line.trim().length() > 0 ? firstWord(line) : "komut");
        } else if (in.type.equals("TypeError") && rest.indexOf("concatenate str with str") >= 0) {
            in.problem = "Yaz\u0131 ile say\u0131y\u0131 + ile birle\u015ftiremezsin.";
            in.fix = "Say\u0131y\u0131 str() i\u00e7ine al: \"Ya\u015f: \" + str(yas)   ya da f\"Ya\u015f: {yas}\" yaz.";
            in.shortMsg = "Yaz\u0131 + say\u0131 olmaz: str() kullan";
        } else if (in.type.equals("TypeError") && rest.indexOf("unsupported operand") >= 0) {
            in.problem = "Bu iki t\u00fcr de\u011fer aras\u0131nda bu i\u015flem yap\u0131lamaz.";
            in.fix = "Yaz\u0131y\u0131 say\u0131ya int(), say\u0131y\u0131 yaz\u0131ya str() ile \u00e7evir.";
            in.shortMsg = "T\u00fcrler uyumsuz";
        } else if (in.type.equals("TypeError") && rest.indexOf("not supported between") >= 0) {
            in.problem = "Yaz\u0131 ile say\u0131 kar\u015f\u0131la\u015ft\u0131r\u0131lamaz.";
            in.fix = "input() yaz\u0131 verir. Say\u0131 laz\u0131msa int(input(\"...\")) yaz.";
            in.shortMsg = "Yaz\u0131 ile say\u0131 kar\u015f\u0131la\u015ft\u0131r\u0131lmaz";
        } else if (in.type.equals("ValueError") && rest.indexOf("invalid literal for int()") >= 0) {
            String bad = between(rest, "'", "'");
            int p = findWord(line, "int");
            if (p >= 0) {
                in.c0 = p;
                in.c1 = p + 3;
            }
            in.problem = "int() yaln\u0131z rakamlardan olu\u015fan yaz\u0131y\u0131 say\u0131ya \u00e7evirir. Gelen de\u011fer: '" + bad + "'";
            in.fix = "Kullan\u0131c\u0131 harf ya da bo\u015flukla yazm\u0131\u015f olabilir. \u00d6nce .strip() kullan ya da if s.isdigit(): ile kontrol et.";
            in.shortMsg = "'" + bad + "' say\u0131 de\u011fil";
        } else if (in.type.equals("ZeroDivisionError")) {
            in.problem = "0'a b\u00f6lemezsin, b\u00f6len 0 olmu\u015f.";
            in.fix = "B\u00f6lmeden \u00f6nce kontrol et: if bolen != 0:   (ya da kullan\u0131c\u0131ya tekrar sor)";
            in.shortMsg = "0'a b\u00f6lme";
        } else if (in.type.equals("IndexError")) {
            in.problem = "Listede (ya da yaz\u0131da) o s\u0131rada bir \u015fey yok.";
            in.fix = "S\u0131ra 0'dan ba\u015flar. 5 eleman varsa son s\u0131ra 4't\u00fcr (ya da -1). len(liste) ile kontrol et.";
            in.shortMsg = "Olmayan s\u0131ra";
        } else if (in.type.equals("AttributeError")) {
            String m = between(msg, "method '", "'");
            int p = m.length() > 0 ? line.indexOf("." + m) : -1;
            if (p >= 0) {
                in.c0 = p + 1;
                in.c1 = p + 1 + m.length();
            }
            Vector c = new Vector();
            addWords(c, METHODS);
            String g = closest(m, c);
            in.problem = "'" + m + "' diye bir komut yok.";
            if (g != null) {
                in.fix = "Belki '" + g + "' demek istedin.";
                in.shortMsg = "." + m + " yok, ." + g + " m\u0131?";
                if (p >= 0) {
                    in.newLine = replaceAt(line, p + 1, p + 1 + m.length(), g);
                }
            } else {
                in.fix = "Yaz\u0131lar i\u00e7in: upper lower strip replace split join find count. Listeler i\u00e7in: append pop insert remove sort reverse.";
                in.shortMsg = "." + m + " komutu yok";
            }
        } else if (in.type.equals("TypeError") && rest.indexOf("is not a function") >= 0) {
            String x = between(msg, "'", "'");
            int p = findWord(line, x);
            if (p >= 0) {
                in.c0 = p;
                in.c1 = p + x.length();
            }
            in.problem = "'" + x + "' bir fonksiyon de\u011fil, onu () ile \u00e7a\u011f\u0131ramazs\u0131n.";
            in.fix = "Parantezi sil ya da ad\u0131n bir def ile tan\u0131mland\u0131\u011f\u0131ndan emin ol.";
            in.shortMsg = "'" + x + "' fonksiyon de\u011fil";
        } else if (rest.indexOf("takes") >= 0 && in.type.equals("TypeError")) {
            in.problem = "Fonksiyon\u0131 yanl\u0131\u015f say\u0131da de\u011ferle \u00e7a\u011f\u0131rd\u0131n. (" + rest + ")";
            in.fix = "\u00c7a\u011f\u0131rd\u0131\u011f\u0131n yerdeki de\u011fer say\u0131s\u0131 def sat\u0131r\u0131ndakiyle ayn\u0131 olmal\u0131.";
            in.shortMsg = "Yanl\u0131\u015f de\u011fer say\u0131s\u0131";
        } else if (in.type.equals("RecursionError")) {
            in.problem = "Fonksiyon kendini bitmeden s\u00fcrekli \u00e7a\u011f\u0131r\u0131yor.";
            in.fix = "Fonksiyona bir durma ko\u015fulu ekle: if n <= 1: return 1";
            in.shortMsg = "Sonsuz \u00e7a\u011fr\u0131";
        } else if (in.type.equals("MemoryError")) {
            in.problem = "Bu i\u015flem \u00e7ok b\u00fcy\u00fck sonu\u00e7 \u00fcretiyor, telefonun belle\u011fi yetmez.";
            in.fix = "Say\u0131lar\u0131 k\u00fc\u00e7\u00fclt (\u00f6rn. range'i k\u0131salt).";
            in.shortMsg = "Bellek yetmiyor";
        } else if (in.type.equals("TypeError") && rest.indexOf("not iterable") >= 0) {
            in.problem = "for d\u00f6ng\u00fcs\u00fc say\u0131 \u00fczerinde d\u00f6nemez.";
            in.fix = "for i in range(5): yaz ya da bir liste / yaz\u0131 ver.";
            in.shortMsg = "for sayida donemez";
        }
        return in;
    }

    private static String firstWord(String line) {
        String t = line.trim();
        int e = 0;
        while (e < t.length() && isId(t.charAt(e))) {
            e++;
        }
        return t.substring(0, e);
    }

    private static void syntax(Info in, String line, int ld, int ce) {
        byte[] cl = PySyntax.classify(line);
        int po = 0, pc = 0, bo = 0, bc = 0;
        int firstSingleEq = -1;
        for (int i = 0; i < line.length(); i++) {
            if (cl[i] != PySyntax.OP) {
                continue;
            }
            char c = line.charAt(i);
            if (c == '(') {
                po++;
            } else if (c == ')') {
                pc++;
            } else if (c == '[') {
                bo++;
            } else if (c == ']') {
                bc++;
            } else if (c == '=' && firstSingleEq < 0) {
                char p = i > 0 ? line.charAt(i - 1) : ' ';
                char n = i + 1 < line.length() ? line.charAt(i + 1) : ' ';
                if (p != '=' && p != '!' && p != '<' && p != '>' && n != '=') {
                    firstSingleEq = i;
                }
            }
        }
        String t = line.trim();
        in.shortMsg = "Yaz\u0131m hatas\u0131";
        if (po > pc || bo > bc) {
            String close = po > pc ? ")" : "]";
            StringBuffer add = new StringBuffer();
            int n = po > pc ? po - pc : bo - bc;
            for (int i = 0; i < n; i++) {
                add.append(close);
            }
            in.c0 = ce;
            in.c1 = ce;
            in.problem = "A\u00e7\u0131lan parantez kapanmam\u0131\u015f.";
            in.fix = "Sat\u0131r\u0131n sonuna " + add + " ekle. Her ( i\u00e7in bir ) olmal\u0131.";
            in.shortMsg = "Parantez kapanmam\u0131\u015f";
            String l2 = line.substring(0, ce) + add + line.substring(ce);
            if (t.endsWith(":")) {
                int cp = ce - 1;
                l2 = line.substring(0, cp) + add + ":" + line.substring(ce);
            }
            in.newLine = l2;
        } else if (pc > po || bc > bo) {
            in.problem = "Fazladan kapanan parantez var.";
            in.fix = "Fazla ) ya da ] karakterini sil; a\u00e7\u0131lan ve kapanan say\u0131s\u0131 e\u015fit olmal\u0131.";
            in.shortMsg = "Fazla parantez";
        } else if (firstSingleEq >= 0 && (startsWithWord(t, "if") || startsWithWord(t, "elif") || startsWithWord(t, "while"))) {
            in.c0 = firstSingleEq;
            in.c1 = firstSingleEq + 1;
            in.problem = "if/while i\u00e7inde kar\u015f\u0131la\u015ft\u0131rma i\u00e7in '=' de\u011fil '==' kullan\u0131l\u0131r.";
            in.fix = "Tek = de\u011fer atamak i\u00e7indir. E\u015fit mi diye sormak i\u00e7in == yaz.";
            in.shortMsg = "'=' yerine '=='";
            in.newLine = replaceAt(line, firstSingleEq, firstSingleEq + 1, "==");
        } else {
            in.problem = "Sat\u0131r\u0131n yaz\u0131l\u0131\u015f\u0131 Python'a uymuyor.";
            in.fix = "T\u0131rnaklara, parantezlere, ':' i\u015fareti ve komut yaz\u0131l\u0131\u015flar\u0131na (print, if, for...) bak. "
                    + "Eksik bir i\u015flem (+, -) ya da fazladan bir karakter olabilir.";
        }
    }

    // ------------------------------------------------------------ report text

    /** Multi-line text for the output screen. */
    public static String format(int lineNo, String line, Info in) {
        StringBuffer sb = new StringBuffer();
        sb.append("HATA - sat\u0131r ").append(lineNo).append("\n");
        int ld = lead(line);
        int c0 = in.c0 < ld ? ld : in.c0;
        int c1 = in.c1 < c0 ? c0 : in.c1;
        if (c0 > line.length()) {
            c0 = line.length();
        }
        if (c1 > line.length()) {
            c1 = line.length();
        }
        String shown = line.substring(ld, c0 > ld ? c0 : ld);
        sb.append(shown);
        sb.append(">>").append(line.substring(c0, c1)).append("<<");
        sb.append(line.substring(c1).trim().length() > 0 ? line.substring(c1) : "");
        sb.append("\n\nSorun: ").append(in.problem);
        sb.append("\n\n\u00c7\u00f6z\u00fcm: ").append(in.fix);
        if (in.newLine != null) {
            sb.append("\n\nD\u00fczeltilmi\u015f sat\u0131r:\n").append(in.newLine.trim());
        }
        sb.append("\n\n(").append(in.type).append(")");
        return sb.toString();
    }
}
