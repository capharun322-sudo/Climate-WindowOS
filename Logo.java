import javax.microedition.lcdui.Graphics;

/** Climate WindowOS logo: two house windows stacked and glued together. */
public class Logo {
    private static int mix(int a, int b, int k, int n) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = ar + (br - ar) * k / n;
        int gg = ag + (bg - ag) * k / n;
        int bl = ab + (bb - ab) * k / n;
        return (r << 16) | (gg << 8) | bl;
    }

    /** Draws the logo into the box (x, y, w, h). */
    public static void draw(Graphics g, int x, int y, int w, int h) {
        int fr = w / 10;
        if (fr < 2) {
            fr = 2;
        }
        int wh = (h + fr) / 2; // the two windows share one frame bar
        for (int i = 0; i < 2; i++) {
            int y0 = (i == 0) ? y : y + h - wh;
            // frame
            g.setColor(0xF4F7FB);
            g.fillRect(x, y0, w, wh);
            g.setColor(0xB9C4D2);
            g.drawRect(x, y0, w - 1, wh - 1);
            int gx = x + fr, gy = y0 + fr, gw = w - 2 * fr, gh = wh - 2 * fr;
            if (gw < 2 || gh < 2) {
                continue;
            }
            // glass: light sky to deeper blue
            for (int k = 0; k < gh; k++) {
                g.setColor(mix(0x8FD3FF, 0x1C78DD, k, gh));
                g.drawLine(gx, gy + k, gx + gw - 1, gy + k);
            }
            // soft reflection in the upper-left corner
            int rw = gw * 2 / 5;
            int rh = gh * 2 / 5;
            g.setColor(0xBFE6FF);
            g.fillTriangle(gx, gy, gx + rw, gy, gx, gy + rh);
            // thin vertical bar in the middle (casement)
            int mw = fr / 3;
            if (mw < 1) {
                mw = 1;
            }
            g.setColor(0xF4F7FB);
            g.fillRect(gx + gw / 2 - mw / 2, gy, mw, gh);
        }
        // little sill under the whole logo
        g.setColor(0xB9C4D2);
        g.fillRect(x - 1, y + h, w + 2, 2);
    }
}
