package javax.microedition.io.file;
import java.io.*;
import java.util.Enumeration;
public interface FileConnection extends javax.microedition.io.Connection {
  boolean isOpen(); InputStream openInputStream() throws IOException; OutputStream openOutputStream() throws IOException;
  long totalSize(); long availableSize(); boolean canRead(); boolean canWrite(); boolean exists(); boolean isDirectory();
  long fileSize() throws IOException; Enumeration list() throws IOException; Enumeration list(String filter, boolean hidden) throws IOException;
  void mkdir() throws IOException; void create() throws IOException; void delete() throws IOException; String getName(); String getPath(); void setFileConnection(String fileName) throws IOException;
  void close() throws IOException; }
