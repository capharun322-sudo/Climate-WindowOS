import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/** Searches the phone's drives for files (JSR-75). Own class so phones without it only fail here. */
public class FileFind {
    static final int MAX_RESULTS = 40;
    static final int MAX_VISIT = 6000;
    static final int MAX_DEPTH = 6;

    /** Returns full paths such as "E:/Music/a.mp3". stop[0] != 0 cancels. */
    public static Vector search(String[] exts, String name, int[] stop) throws IOException {
        Vector out = new Vector();
        int[] visited = new int[1];
        String[] words = Brain.tokens(Brain.normalize(name));
        Enumeration roots = FileSystemRegistry.listRoots();
        while (roots != null && roots.hasMoreElements() && stop[0] == 0 && out.size() < MAX_RESULTS) {
            String root = (String) roots.nextElement();
            FileConnection fc = null;
            try {
                fc = (FileConnection) Connector.open("file:///" + root, Connector.READ);
                walk(fc, root, 0, exts, words, out, visited, stop);
            } catch (SecurityException se) {
                throw se;
            } catch (Throwable t) {
                // unreadable drive: go on with the next one
            } finally {
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }
        return out;
    }

    private static void walk(FileConnection fc, String path, int depth, String[] exts, String[] words, Vector out,
            int[] visited, int[] stop) throws IOException {
        Enumeration e = fc.list();
        Vector dirs = new Vector();
        while (e.hasMoreElements()) {
            String n = (String) e.nextElement();
            visited[0]++;
            if (n.endsWith("/")) {
                dirs.addElement(n);
            } else if (match(n, exts, words)) {
                out.addElement(path + n);
            }
            if (out.size() >= MAX_RESULTS || visited[0] > MAX_VISIT || stop[0] != 0) {
                return;
            }
        }
        if (depth >= MAX_DEPTH) {
            return;
        }
        for (int i = 0; i < dirs.size(); i++) {
            if (out.size() >= MAX_RESULTS || visited[0] > MAX_VISIT || stop[0] != 0) {
                return;
            }
            String d = (String) dirs.elementAt(i);
            boolean entered = false;
            try {
                fc.setFileConnection(d);
                entered = true;
                walk(fc, path + d, depth + 1, exts, words, out, visited, stop);
            } catch (IOException ex) {
                // folder not readable
            } finally {
                if (entered) {
                    try {
                        fc.setFileConnection("..");
                    } catch (IOException ex2) {
                        // ignore
                    }
                }
            }
        }
    }

    static boolean match(String fileName, String[] exts, String[] words) {
        String lower = fileName.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : "";
        if (exts != null) {
            boolean ok = false;
            for (int i = 0; i < exts.length; i++) {
                if (exts[i].equals(ext)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        if (words.length > 0) {
            String base = Brain.normalize(dot > 0 ? fileName.substring(0, dot) : fileName);
            for (int i = 0; i < words.length; i++) {
                if (base.indexOf(words[i]) < 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
