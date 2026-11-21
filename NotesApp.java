import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStore;

/** Simple notepad that keeps notes in RMS. */
public class NotesApp implements CommandListener {
    private static final String STORE = "cwos_notes";
    private Display display;
    private Runnable onExit;
    private List list;
    private TextBox box;
    private Vector ids = new Vector();
    private int editId = -1;
    private Command cNew = new Command("Yeni", Command.SCREEN, 1);
    private Command cDel = new Command("Sil", Command.SCREEN, 2);
    private Command cBack = new Command("Kapat", Command.BACK, 1);
    private Command cSave = new Command("Kaydet", Command.OK, 1);
    private Command cCancel = new Command("\u0130ptal", Command.CANCEL, 2);

    public NotesApp(Display d, Runnable onExit) {
        this.display = d;
        this.onExit = onExit;
    }

    public void start() {
        refresh();
        display.setCurrent(list);
    }

    private void refresh() {
        list = new List("Notlar", List.IMPLICIT);
        ids.removeAllElements();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            javax.microedition.rms.RecordEnumeration en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement()) {
                int id = en.nextRecordId();
                String t = new String(rs.getRecord(id), "UTF-8");
                list.append(title(t), null);
                ids.addElement(new Integer(id));
            }
            en.destroy();
        } catch (Throwable t) {
            // empty list on error
        }
        close(rs);
        if (ids.size() == 0) {
            list.append("(Hen\u00fcz not yok - Yeni ile ekle)", null);
        }
        list.addCommand(cNew);
        list.addCommand(cDel);
        list.addCommand(cBack);
        list.setCommandListener(this);
    }

    private static String title(String t) {
        int nl = t.indexOf('\n');
        if (nl >= 0) {
            t = t.substring(0, nl);
        }
        if (t.length() > 28) {
            t = t.substring(0, 28);
        }
        return t.length() == 0 ? "(bo\u015f)" : t;
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

    private int selectedId() {
        int i = list.getSelectedIndex();
        if (i < 0 || i >= ids.size()) {
            return -1;
        }
        return ((Integer) ids.elementAt(i)).intValue();
    }

    private void edit(int id) {
        String text = "";
        if (id >= 0) {
            RecordStore rs = null;
            try {
                rs = RecordStore.openRecordStore(STORE, true);
                text = new String(rs.getRecord(id), "UTF-8");
            } catch (Throwable t) {
                text = "";
            }
            close(rs);
        }
        editId = id;
        box = new TextBox("Not", text, 1500, TextField.ANY);
        box.addCommand(cSave);
        box.addCommand(cCancel);
        box.setCommandListener(this);
        display.setCurrent(box);
    }

    private void save() {
        RecordStore rs = null;
        try {
            byte[] b = box.getString().getBytes("UTF-8");
            rs = RecordStore.openRecordStore(STORE, true);
            if (editId < 0) {
                rs.addRecord(b, 0, b.length);
            } else {
                rs.setRecord(editId, b, 0, b.length);
            }
        } catch (Throwable t) {
            Alert a = new Alert("Not", "Kaydedilemedi", null, AlertType.ERROR);
            display.setCurrent(a, box);
            close(rs);
            return;
        }
        close(rs);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cBack) {
            onExit.run();
        } else if (c == cNew) {
            edit(-1);
        } else if (c == List.SELECT_COMMAND) {
            int id = selectedId();
            if (id >= 0) {
                edit(id);
            }
        } else if (c == cDel) {
            int id = selectedId();
            if (id >= 0) {
                RecordStore rs = null;
                try {
                    rs = RecordStore.openRecordStore(STORE, true);
                    rs.deleteRecord(id);
                } catch (Throwable t) {
                    // ignore
                }
                close(rs);
                start();
            }
        } else if (c == cSave) {
            save();
            start();
        } else if (c == cCancel) {
            start();
        }
    }
}
