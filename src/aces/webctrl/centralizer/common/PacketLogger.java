/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/**
 * Thread-safe namespace which controls packet capture logging.
 * <p>Appends a timestamp to log entries.
 */
public class PacketLogger {
  private volatile static PrintWriter out = null;
  private volatile static Path p = null;
  private volatile static File f = null;
  private volatile static boolean working = false;
  /**
   * Used to set the log file.
   */
  public synchronized static void init(Path p){
    if (working){
      stop();
    }
    PacketLogger.p = p;
    f = p.toFile();
  }
  /**
   * @return whether packets are being captured.
   */
  public static boolean isWorking(){
    return working;
  }
  /**
   * Starts packet capture.
   */
  public static void start(){
    start(new Date());
  }
  /**
   * Starts packet capture asynchronously (if supported).
   */
  public static void startAsync(){
    if (Logger.asyncLogConsumer==null){
      start();
    }else{
      final Date d = new Date();
      Logger.asyncLogConsumer.accept(new DelayedRunnable(0){
        public void run(){
          start(d);
        }
      });
    }
  }
  /**
   * Starts packet capture.
   * @param d is the timestamp.
   */
  public synchronized static void start(Date d){
    if (!working){
      working = true;
      try{
        if (!Files.exists(p)){
          p = Files.createFile(p);
        }
        out = new PrintWriter(new FileWriter(f,true));
        log("Packet capture started.", d);
      }catch(Throwable e){
        working = false;
        Logger.log("Error occurred while initiating packet capture.", e);
      }
    }
  }
  /**
   * Stops packet capture.
   */
  public static void stop(){
    stop(new Date());
  }
  /**
   * Stops packet capture asynchronously (if supported).
   */
  public static void stopAsync(){
    if (Logger.asyncLogConsumer==null){
      stop();
    }else{
      final Date d = new Date();
      Logger.asyncLogConsumer.accept(new DelayedRunnable(0){
        public void run(){
          stop(d);
        }
      });
    }
  }
  /**
   * Stops packet capture.
   * @param d is the timestamp.
   */
  public synchronized static void stop(Date d){
    if (working){
      try{
        log("Packet capture stopped.", d);
        out.close();
        out = null;
      }catch(Throwable e){
        Logger.log("Error occurred while terminating packet capture.", e);
      }
      working = false;
    }
  }
  /**
   * Logs and timestamps a message.
   * @param str is the message to log
   */
  public static void log(String str){
    log(str, new Date());
  }
  /**
   * Logs and timestamps a message asynchronously (if supported).
   * @param str is the message to log
   */
  public static void logAsync(final String str){
    if (Logger.asyncLogConsumer==null){
      log(str);
    }else{
      final Date d = new Date();
      Logger.asyncLogConsumer.accept(new DelayedRunnable(0){
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
  public synchronized static void log(String str, Date d){
    if (working){
      out.print(Logger.format.format(d));
      out.print(Logger.separator);
      out.print(str);
      out.println();
      out.flush();
    }
  }
  /**
   * Logs and timestamps an error with the given description.
   * @param desc is the description of the error to log.
   * @param e is the error to log.
   */
  public static void log(String desc, Throwable e){
    log(desc,e,new Date());
  }
  /**
   * Logs and timestamps an error with the given description asynchronously (if supported).
   * @param desc is the description of the error to log.
   * @param e is the error to log.
   */
  public static void logAsync(final String desc, final Throwable e){
    if (Logger.asyncLogConsumer==null){
      log(desc,e);
    }else{
      final Date d = new Date();
      Logger.asyncLogConsumer.accept(new DelayedRunnable(0){
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
  public synchronized static void log(String desc, Throwable e, Date d){
    if (working){
      out.print(Logger.format.format(d));
      out.print(Logger.separator);
      out.print(desc);
      out.println();
      e.printStackTrace(out);
      out.flush();
    }
  }
  /**
   * Logs an error.
   * If you want a timestamp, you should use {@link #log(String,Throwable)} instead.
   * @param e is the error to log.
   */
  public synchronized static void log(Throwable e){
    if (working){
      e.printStackTrace(out);
      out.flush();
    }
  }
   /**
   * Logs an error asynchronously (if supported).
   * If you want a timestamp, you should use {@link #logAsync(String,Throwable)} instead.
   * @param e is the error to log.
   */
  public static void logAsync(Throwable e){
    if (Logger.asyncLogConsumer==null){
      log(e);
    }else{
      Logger.asyncLogConsumer.accept(new DelayedRunnable(0){
        public void run(){
          log(e);
        }
      });
    }
  }
  /**
   * Logs a data packet.
   * @param IP is the IP address of the remote host.
   * @param data is the data packet to be logged.
   * @param write indicates whether this packet was transmitted ({@code true}) or received ({@code false}).
   */
  public static void log(String IP, byte[] data, boolean write){
    log(IP, data, write, new Date());
  }
  /**
   * Logs a data packet asynchronously (if supported).
   * @param IP is the IP address of the remote host.
   * @param data is the data packet to be logged.
   * @param write indicates whether this packet was transmitted ({@code true}) or received ({@code false}).
   */
  public static void logAsync(final String IP, byte[] data, boolean write){
    if (Logger.asyncLogConsumer==null){
      log(IP, data, write);
    }else{
      final Date d = new Date();
      Logger.asyncLogConsumer.accept(new DelayedRunnable(0){
        public void run(){
          log(IP, data, write, d);
        }
      });
    }
  }
  /**
   * Logs a data packet.
   * @param IP is the IP address of the remote host.
   * @param data is the data packet to be logged.
   * @param write indicates whether this packet was transmitted ({@code true}) or received ({@code false}).
   * @param d is the timestamp.
   */
  public synchronized static void log(String IP, byte[] data, boolean write, Date d){
    if (working){
      out.print(Logger.format.format(d));
      out.print(Logger.separator);
      out.print(IP);
      out.print(Logger.separator);
      out.print(write?"Write":"Read");
      out.print(Logger.separator);
      out.print(java.util.Base64.getEncoder().encodeToString(data));
      out.println();
      out.flush();
    }
  }
}