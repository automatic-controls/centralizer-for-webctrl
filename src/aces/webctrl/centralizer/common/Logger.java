/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.time.format.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import java.util.function.*;
//TODO (Performance Improvement) - Use java.nio.channels.FileChannel.transferTo() for the part of Logger.delete() which copies the remaining valid log entries to a temporary file
/**
 * Thread-safe namespace which controls logging operations.
 * <p>Appends a timestamp to most log entries.
 */
public class Logger {
  public final static DateTimeFormatter format = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
  public final static String separator = " - ";
  public volatile static Consumer<DelayedRunnable> asyncLogConsumer = null;
  private volatile static PrintWriter out = null;
  private volatile static File f = null;
  private volatile static File tmp = null;
  /**
   * Initialization method.
   * @param file is the log file to write to.
   * @param asyncLogConsumer is used for asynchronous logging of messages (can be {@code null} if all logs should be synchronous).
   */
  public synchronized static void init(Path logFile, Consumer<DelayedRunnable> asyncLogConsumer) throws IOException {
    Logger.asyncLogConsumer = asyncLogConsumer;
    if (!Files.exists(logFile)){
      logFile = Files.createFile(logFile);
    }
    f = logFile.toFile();
    tmp = logFile.resolveSibling("tmp_"+f.getName()).toFile();
    out = new PrintWriter(new FileWriter(f,true));
    PacketLogger.init(logFile.getParent().resolve("packet_capture.txt"));
  }
  public synchronized static void transferTo(PrintWriter o){
    try{
      try{
        out.close();
        try(
          FileReader r = new FileReader(f);
        ){
          char[] buffer = new char[8192];
          int nRead;
          while ((nRead = r.read(buffer, 0, 8192)) >= 0) {
              o.write(buffer, 0, nRead);
          }
        }
      }finally{
        out = new PrintWriter(new FileWriter(f,true));
      }
    }catch(Throwable e){
      log("Failed to transfer data in log file.", e);
    }
  }
  /**
   * Logs and timestamps a message.
   * @param str is the message to log
   */
  public static void log(String str){
    log(str, Instant.now());
  }
  /**
   * Logs and timestamps a message asynchronously (if supported).
   * @param str is the message to log
   */
  public static void logAsync(final String str){
    if (asyncLogConsumer==null){
      log(str);
    }else{
      final Instant d = Instant.now();
      asyncLogConsumer.accept(new DelayedRunnable(d.toEpochMilli()){
        public void run(){
          log(str, d);
        }
      });
    }
  }
  /**
   * Logs and timestamps a message.
   * @param str is the message to log
   * @param d is the timestamp for the message
   */
  public synchronized static void log(String str, Instant d){
    format.formatTo(d,out);
    out.print(separator);
    out.print(str);
    out.println();
    out.flush();
  }
  /**
   * Logs and timestamps an error with the given description.
   * @param desc is the description of the error to log.
   * @param e is the error to log.
   */
  public static void log(String desc, Throwable e){
    log(desc,e,Instant.now());
  }
  /**
   * Logs and timestamps an error with the given description asynchronously (if supported).
   * @param desc is the description of the error to log.
   * @param e is the error to log.
   */
  public static void logAsync(final String desc, final Throwable e){
    if (asyncLogConsumer==null){
      log(desc,e);
    }else{
      final Instant d = Instant.now();
      asyncLogConsumer.accept(new DelayedRunnable(d.toEpochMilli()){
        public void run(){
          log(desc, e, d);
        }
      });
    }
  }
  /**
   * Logs and timestamps an error with the given description.
   * @param desc is the description of the error to log.
   * @param e is the error to log.
   * @param d is the timestamp for the message
   */
  public synchronized static void log(String desc, Throwable e, Instant d){
    format.formatTo(d,out);
    out.print(separator);
    out.print(desc);
    out.println();
    e.printStackTrace(out);
    out.flush();
  }
  /**
   * Logs an error.
   * If you want a timestamp, you should use {@link #log(String,Throwable)} instead.
   * @param e is the error to log.
   */
  public synchronized static void log(Throwable e){
    e.printStackTrace(out);
    out.flush();
  }
   /**
   * Logs an error asynchronously (if supported).
   * If you want a timestamp, you should use {@link #logAsync(String,Throwable)} instead.
   * @param e is the error to log.
   */
  public static void logAsync(Throwable e){
    if (asyncLogConsumer==null){
      log(e);
    }else{
      asyncLogConsumer.accept(new DelayedRunnable(System.currentTimeMillis()){
        public void run(){
          log(e);
        }
      });
    }
  }
  /**
   * Indicates whether {@link #init(File)} has been called.
   */
  public static boolean isInitialized(){
    return out!=null;
  }
  /**
   * Closed the logger output stream.
   */
  public synchronized static void close(){
    out.close();
  }
  /**
   * Deletes log entries which occurred more than {@code deleteLogAfter} milliseconds ago.
   */
  public synchronized static void trim(long deleteLogAfter){
    try{
      long time = System.currentTimeMillis()-deleteLogAfter;
      if (!tmp.exists() && !tmp.createNewFile()){
        throw new IOException("Unable to create temporary log file.");
      }
      out.close();
      try{
        try (
          BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
          BufferedOutputStream outTMP = new BufferedOutputStream(new FileOutputStream(tmp, false));
        ){
          StringBuilder line = new StringBuilder(20);
          String str;
          int i;
          char c;
          boolean b;
          while (true){
            i = in.read();
            if (i==-1){
              break;
            }
            c = (char)i;
            if (c=='-'){
              b = true;
              str = line.toString().trim();
              line.setLength(0);
              try{
                b = time<Instant.from(format.parse(str)).toEpochMilli();
              }catch(DateTimeException e){
                e.printStackTrace();
              }
              if (b){
                outTMP.write(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outTMP.write(i);
                byte[] buf = new byte[2048];
                while (true){
                  i = in.read(buf);
                  if (i==-1){
                    break;
                  }
                  outTMP.write(buf,0,i);
                }
                break;
              }else{
                do {
                  i = in.read();
                  c = (char)i;
                } while(i!=-1 && c!='\n');
                if (i==-1){
                  break;
                }
              }
            }else if (c=='\n'){
              line.setLength(0);
            }else{
              line.append(c);
            }
          }
        }
        if (!f.delete()){
          throw new IOException("Unable to delete log file.");
        }
        if (!tmp.renameTo(f)){
          throw new IOException("Unable to rename temporary log file.");
        }
      } finally {
        out = new PrintWriter(new FileWriter(f,true));
      }
    }catch(Throwable e){
      Logger.log("Error occurred while deleting expired log entries.", e);
    }
  }
}