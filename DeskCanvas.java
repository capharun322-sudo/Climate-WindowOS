import java.util.Calendar;
import java.util.Date;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Boot screen, desktop, taskbar and Start menu of Climate WindowOS. */
public class DeskCanvas extends Canvas implements Runnable, CommandListener {
    public interface Host {
        void launch(int app);

        void quit();
    }

    static final int BOOT = 0, DESK = 1, SHUT = 2;
    static final int BOOT_MS = 3000;
    static final String[] ICONS = {"Dosyalar", "Python", "Notlar", "Hesap", "Hakk\u0131nda"};
    static final String[] MENU = {"Dosyalar", "Python", "Notlar", "Hesap makinesi", "Hakk\u0131nda", "Kapat"};

    private Host host;
    private int state = BOOT;
    private boolean menu = false;
    private int sel = 0, msel = 0;
    private int progress = 0;
    private volatile boolean skip = false;
    private volatile boolean alive = true;
    private Image bg;
    private int bgW = -1, bgH = -1;
    private Font small = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private Font bold = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    private Command cStart = new Command("Ba\u015flat", Command.SCREEN, 1);
    private Command cOff = new Command("Kapat", Command.SCREEN, 2);

    public DeskCanvas(Host host) {
        this.host = host;
        addCommand(cStart);
        addCommand(cOff);
        setCommandListener(this);
    }

    /** Starts the boot sequence (sound + animation). */
    public void begin() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!Snd.play("/acilis.wav")) {
                        Snd.tune(true);
                    }
                } catch (Throwable t) {
                    // no sound support: stay silent
                }
            }
        }).start();
        new Thread(this).start();
    }

    public void stopThreads() {
        alive = false;
    }

    // ------------------------------------------------------------ thread
    public void run() {
        long t0 = System.currentTimeMillis();
        while (alive) {
            try {
                if (state == BOOT) {
                    long dt = System.currentTimeMillis() - t0;
                    if (skip) {
                        dt = BOOT_MS;
                    }
                    progress = dt >= BOOT_MS ? 100 : (int) (dt * 100 / BOOT_MS);
                    if (dt >= BOOT_MS) {
                        state = DESK;
                    }
                    repaint();
                    Thread.sleep(70);
                } else if (state == DESK) {
                    if (isShown()) {
                        repaint(0, getHeight() - taskH(), getWidth(), taskH());
                    }
                    Thread.sleep(4000);
                } else {
                    Thread.sleep(200);
                }
            } catch (Throwable t) {
                // keep running
            }
        }
    }

    private void shutdown() {
        if (state == SHUT) {
            return;
        }
        state = SHUT;
        menu = false;
        repaint();
        new Thread(new Runnable() {
            public void run() {
                try {
                    Snd.stop();
                    if (!Snd.play("/kapat.wav")) {
                        Snd.tune(false);
                    }
                } catch (Throwable t) {
                    // silent
                }
                try {
                    Thread.sleep(1600);
                } catch (InterruptedException e) {
                    // ignore
                }
                try {
                    Snd.stop();
                } catch (Throwable t) {
                    // ignore
                }
                alive = false;
                host.quit();
            }
        }).start();
    }

    // ------------------------------------------------------------ input
    public void commandAction(Command c, Displayable d) {
        if (state != DESK) {
            return;
        }
        if (c == cStart) {
            toggleMenu();
        } else if (c == cOff) {
            shutdown();
        }
    }

    private void toggleMenu() {
        menu = !menu;
        msel = 0;
        repaint();
    }

    private int cols() {
        int c = (getWidth() - 8) / 64;
        return c < 1 ? 1 : c;
    }

    protected void keyPressed(int k) {
        if (state == BOOT) {
            skip = true;
            return;
        }
        if (state != DESK) {
            return;
        }
        if (k == Canvas.KEY_POUND || k == Canvas.KEY_NUM0) {
            toggleMenu();
            return;
        }
        if (k >= Canvas.KEY_NUM1 && k <= Canvas.KEY_NUM5) {
            menu = false;
            sel = k - Canvas.KEY_NUM1;
            repaint();
            host.launch(sel);
            return;
        }
        int ga = 0;
        try {
            ga = getGameAction(k);
        } catch (Throwable t) {
            ga = 0;
        }
        if (menu) {
            int n = MENU.length;
            if (ga == Canvas.UP) {
                msel = (msel + n - 1) % n;
            } else if (ga == Canvas.DOWN) {
                msel = (msel + 1) % n;
            } else if (ga == Canvas.LEFT) {
                menu = false;
            } else if (ga == Canvas.FIRE) {
                menu = false;
                repaint();
                runItem(msel);
                return;
            }
            repaint();
            return;
        }
        int n = ICONS.length;
        int cs = cols();
        if (ga == Canvas.LEFT) {
            sel = (sel + n - 1) % n;
        } else if (ga == Canvas.RIGHT) {
            sel = (sel + 1) % n;
        } else if (ga == Canvas.UP) {
            if (sel - cs >= 0) {
                sel -= cs;
            }
        } else if (ga == Canvas.DOWN) {
            if (sel + cs < n) {
                sel += cs;
            }
        } else if (ga == Canvas.FIRE) {
            host.launch(sel);
            return;
        }
        repaint();
    }

    protected void keyRepeated(int k) {
        if (state == DESK) {
            keyPressed(k);
        }
    }

    private void runItem(int i) {
        if (i >= ICONS.length) {
            shutdown();
        } else {
            host.launch(i);
        }
    }

    // ------------------------------------------------------------ drawing
    private int taskH() {
        return small.getHeight() + 8;
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setClip(0, 0, w, h);
        if (state == BOOT) {
            paintBoot(g, w, h);
        } else if (state == SHUT) {
            paintShut(g, w, h);
        } else {
            paintDesk(g, w, h);
        }
    }

    private void center(Graphics g, Font f, String s, int w, int y, int color) {
        g.setFont(f);
        g.setColor(color);
        g.drawString(s, (w - f.stringWidth(s)) / 2, y, Graphics.TOP | Graphics.LEFT);
    }

    private void paintBoot(Graphics g, int w, int h) {
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);
        int lh = h * 36 / 100;
        int lw = lh * 3 / 4;
        int top = h * 10 / 100;
        Logo.draw(g, (w - lw) / 2, top, lw, lh);
        center(g, bold, "Climate WindowOS", w, top + lh + 12, 0xFFFFFF);
        int bw = w * 5 / 10, bx = (w - bw) / 2, by = h - h * 20 / 100;
        g.setColor(0x4A5563);
        g.drawRect(bx, by, bw, 7);
        int fw = (bw - 3) * progress / 100;
        g.setColor(0x2F9BFF);
        g.fillRect(bx + 2, by + 2, fw, 4);
        center(g, small, "Ba\u015flat\u0131l\u0131yor...", w, by + 12, 0x9AA7B6);
    }

    private void paintShut(Graphics g, int w, int h) {
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);
        int lh = h * 26 / 100;
        int lw = lh * 3 / 4;
        Logo.draw(g, (w - lw) / 2, h * 22 / 100, lw, lh);
        center(g, bold, "Kapat\u0131l\u0131yor...", w, h * 22 / 100 + lh + 14, 0xFFFFFF);
    }

    private static int mix(int a, int b, int k, int n) {
        if (k < 0) {
            k = 0;
        }
        if (k > n) {
            k = n;
        }
        int r = ((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * k / n);
        int gr = ((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * k / n);
        int bl = (a & 255) + (((b & 255) - (a & 255)) * k / n);
        return (r << 16) | (gr << 8) | bl;
    }

    // light beams: slope range (dy per 100 dx), lightness percent
    private static final int[][] BEAMS = {
        {6, 16, 9}, {24, 40, 14}, {52, 80, 9}, {110, 260, 12},
        {-16, -6, 9}, {-40, -24, 14}, {-80, -52, 9}, {-260, -110, 12}};

    private void drawWall(Graphics g, int w, int h) {
        int ax = w + w / 8, ay = h * 45 / 100;
        for (int y = 0; y < h; y++) {
            int d = y > ay ? y - ay : ay - y;
            int far = y > ay ? h - ay : ay;
            int base = mix(0x2086E0, 0x061C4C, d, far);
            g.setColor(base);
            g.drawLine(0, y, w - 1, y);
            int dy = y - ay;
            if (dy == 0) {
                continue;
            }
            for (int i = 0; i < BEAMS.length; i++) {
                int t1 = BEAMS[i][0], t2 = BEAMS[i][1];
                if ((dy > 0) != (t1 > 0)) {
                    continue;
                }
                int x1 = ax - dy * 100 / t1;
                int x2 = ax - dy * 100 / t2;
                if (x1 > x2) {
                    int tmp = x1;
                    x1 = x2;
                    x2 = tmp;
                }
                if (x2 < 0 || x1 >= w) {
                    continue;
                }
                if (x1 < 0) {
                    x1 = 0;
                }
                if (x2 > w - 1) {
                    x2 = w - 1;
                }
                g.setColor(mix(base, 0xFFFFFF, BEAMS[i][2], 100));
                g.drawLine(x1, y, x2, y);
            }
        }
    }

    private void paintDesk(Graphics g, int w, int h) {
        int tb = taskH();
        // wallpaper (cached in an image if memory allows)
        if (bg == null || bgW != w || bgH != h) {
            bg = null;
            try {
                Image im = Image.createImage(w, h);
                drawWall(im.getGraphics(), w, h);
                bg = im;
                bgW = w;
                bgH = h;
            } catch (Throwable t) {
                bg = null;
            }
        }
        if (bg != null) {
            g.drawImage(bg, 0, 0, Graphics.TOP | Graphics.LEFT);
        } else {
            drawWall(g, w, h);
        }
        // small logo mark in the lower right corner
        int lh = 40, lw = 30;
        Logo.draw(g, w - lw - 10, h - tb - lh - 10, lw, lh);
        // icons
        int cs = cols();
        for (int i = 0; i < ICONS.length; i++) {
            int cx = 6 + (i % cs) * 64;
            int cy = 6 + (i / cs) * 62;
            if (i == sel && !menu) {
                g.setColor(0x3C8EDF);
                g.fillRoundRect(cx + 1, cy, 62, 58, 8, 8);
                g.setColor(0xCFE8FF);
                g.drawRoundRect(cx + 1, cy, 62, 58, 8, 8);
            }
            drawIcon(g, i, cx + 16, cy + 3);
            g.setFont(small);
            String s = ICONS[i];
            int tx = cx + (64 - small.stringWidth(s)) / 2;
            g.setColor(0x000000);
            g.drawString(s, tx + 1, cy + 39 + 1, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xFFFFFF);
            g.drawString(s, tx, cy + 39, Graphics.TOP | Graphics.LEFT);
        }
        paintTaskbar(g, w, h, tb);
        if (menu) {
            paintMenu(g, w, h, tb);
        }
    }

    private void paintTaskbar(Graphics g, int w, int h, int tb) {
        int y = h - tb;
        g.setColor(0x0E1620);
        g.fillRect(0, y, w, tb);
        g.setColor(0x2C3E52);
        g.drawLine(0, y, w, y);
        // start button
        int bw = 14 + small.stringWidth("Ba\u015flat") + 12;
        g.setColor(menu ? 0x2F7FD0 : 0x18263A);
        g.fillRect(2, y + 2, bw, tb - 3);
        Logo.draw(g, 6, y + 4, 9, tb - 9);
        g.setFont(small);
        g.setColor(0xFFFFFF);
        g.drawString("Ba\u015flat", 20, y + 4, Graphics.TOP | Graphics.LEFT);
        // clock
        String t = clock();
        g.setColor(0xFFFFFF);
        g.drawString(t, w - small.stringWidth(t) - 6, y + 4, Graphics.TOP | Graphics.LEFT);
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : "" + n;
    }

    static String clock() {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            return two(c.get(Calendar.HOUR_OF_DAY)) + ":" + two(c.get(Calendar.MINUTE));
        } catch (Throwable t) {
            return "--:--";
        }
    }

    private void paintMenu(Graphics g, int w, int h, int tb) {
        int ih = small.getHeight() + 6;
        int mw = 150;
        if (mw > w - 4) {
            mw = w - 4;
        }
        int head = ih + 2;
        int mh = head + MENU.length * ih + 4;
        int mx = 2, my = h - tb - mh;
        g.setColor(0x16222F);
        g.fillRect(mx, my, mw, mh);
        g.setColor(0x3C8EDF);
        g.drawRect(mx, my, mw - 1, mh - 1);
        g.setColor(0x1F6FD0);
        g.fillRect(mx + 1, my + 1, mw - 2, head);
        g.setFont(small);
        g.setColor(0xFFFFFF);
        g.drawString("Climate WindowOS", mx + 6, my + 4, Graphics.TOP | Graphics.LEFT);
        for (int i = 0; i < MENU.length; i++) {
            int iy = my + head + 2 + i * ih;
            if (i == msel) {
                g.setColor(0x2F7FD0);
                g.fillRect(mx + 2, iy, mw - 4, ih);
            }
            g.setColor(0xFFFFFF);
            g.drawString(MENU[i], mx + 12, iy + 3, Graphics.TOP | Graphics.LEFT);
        }
    }

    // 32x32 icons, drawn with simple shapes
    private void drawIcon(Graphics g, int i, int x, int y) {
        if (i == 0) { // folder
            g.setColor(0xE9A81C);
            g.fillRoundRect(x, y + 4, 14, 8, 3, 3);
            g.setColor(0xFFC83D);
            g.fillRoundRect(x, y + 8, 32, 22, 4, 4);
            g.setColor(0xFFDE7A);
            g.fillRect(x + 2, y + 11, 28, 3);
        } else if (i == 1) { // python
            g.setColor(0x3776AB);
            g.fillRoundRect(x, y, 32, 32, 8, 8);
            g.setColor(0xFFD43B);
            g.fillRoundRect(x + 16, y + 16, 16, 16, 8, 8);
            g.setColor(0xFFFFFF);
            g.setFont(bold);
            g.drawString("Py", x + 4, y + 8, Graphics.TOP | Graphics.LEFT);
        } else if (i == 2) { // notes
            g.setColor(0xFFF6B0);
            g.fillRect(x + 3, y, 26, 32);
            g.setColor(0xE8C93A);
            g.fillRect(x + 3, y, 26, 6);
            g.setColor(0x9A8F5A);
            for (int k = 0; k < 4; k++) {
                g.drawLine(x + 7, y + 12 + k * 5, x + 25, y + 12 + k * 5);
            }
        } else if (i == 3) { // calculator
            g.setColor(0x4B5563);
            g.fillRoundRect(x + 3, y, 26, 32, 5, 5);
            g.setColor(0x9BE59B);
            g.fillRect(x + 7, y + 4, 18, 7);
            g.setColor(0xE5E7EB);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    g.fillRect(x + 7 + c * 7, y + 15 + r * 5, 4, 3);
                }
            }
        } else { // info
            g.setColor(0x2F9BFF);
            g.fillArc(x, y, 32, 32, 0, 360);
            g.setColor(0xFFFFFF);
            g.fillRect(x + 14, y + 13, 4, 12);
            g.fillRect(x + 14, y + 6, 4, 4);
        }
    }
}
