import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/** Calculator. The expression is evaluated with the PyPhone interpreter. */
public class CalcApp implements CommandListener {
    private Display display;
    private Runnable onExit;
    private Form form;
    private TextField in;
    private StringItem out;
    private String last = null;
    private StringBuffer hist = new StringBuffer();
    private Command cEq = new Command("Hesapla", Command.OK, 1);
    private Command cClr = new Command("Temizle", Command.SCREEN, 2);
    private Command cBack = new Command("Kapat", Command.BACK, 1);

    public CalcApp(Display d, Runnable onExit) {
        this.display = d;
        this.onExit = onExit;
    }

    public void start() {
        if (form == null) {
            form = new Form("Hesap makinesi");
            in = new TextField("\u0130fade (\u00f6rn 12*(3+4)). ans = son sonu\u00e7", "", 60, TextField.ANY);
            out = new StringItem("", "");
            form.append(in);
            form.append(out);
            form.addCommand(cEq);
            form.addCommand(cClr);
            form.addCommand(cBack);
            form.setCommandListener(this);
        }
        display.setCurrent(form);
    }

    /** Evaluates an expression; returns the result text or "Hata: ...". */
    public static String eval(String expr, String ans) {
        String e = expr.trim();
        if (e.length() == 0) {
            return "";
        }
        e = replace(e, "^", "**");
        int first = e.indexOf("**");
        if (first >= 0 && e.indexOf("**", first + 2) >= 0) {
            return "Hata: \u00fcst i\u015fareti bir kez kullan";
        }
        final StringBuffer sb = new StringBuffer();
        PyIO io = new PyIO() {
            public void print(String s) {
                sb.append(s);
            }

            public String input(String p) {
                return null;
            }
        };
        String src = (ans != null ? "ans = " + ans + "\n" : "") + "print(" + e + ")\n";
        try {
            new PyInterp(io).run(src);
        } catch (PyError pe) {
            return "Hata: " + pe.getMessage();
        } catch (Throwable t) {
            return "Hata";
        }
        return sb.toString().trim();
    }

    private static String replace(String s, String a, String b) {
        StringBuffer r = new StringBuffer();
        int i = 0, j;
        while ((j = s.indexOf(a, i)) >= 0) {
            r.append(s.substring(i, j));
            r.append(b);
            i = j + a.length();
        }
        r.append(s.substring(i));
        return r.toString();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cBack) {
            onExit.run();
        } else if (c == cClr) {
            in.setString("");
            out.setText("");
            hist.setLength(0);
            last = null;
        } else if (c == cEq) {
            String e = in.getString();
            String r = eval(e, last);
            if (r.length() == 0) {
                return;
            }
            hist.append(e).append(" = ").append(r).append('\n');
            if (hist.length() > 600) {
                hist = new StringBuffer(hist.toString().substring(hist.length() - 600));
            }
            if (!r.startsWith("Hata")) {
                last = r;
            }
            out.setText(hist.toString());
            in.setString("");
        }
    }
}
