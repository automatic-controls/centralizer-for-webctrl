/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.security.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
//TODO (Performance Improvement) - Make every IO operation non-blocking
/**
 * Thread-safe namespace for initializing and saving the database.
 */
public class Database {
  /** Used to specify the transformation for {@code javax.crypto.Cipher}. */
  public final static String CIPHER = "RSA/ECB/OAEPWITHSHA-512ANDMGF1PADDING";
  /** All-purpose random number generator for use anywhere its needed. This object is thread-safe. */
  public volatile static SecureRandom entropy;
  /** Variable which helps to optimize {@link #save()}. */
  private volatile static AtomicBoolean saving;
  /** Specifies whether or not this application is meant to act as a server to host the database (some behavior gets changed slightly). */
  private volatile static boolean server;
  /** Used for certain non-blocking IO operations. */
  public volatile static java.util.concurrent.ExecutorService exec = null;
  /**
   * Initializes all components of the database.
   * Invoked only once at the start of the application.
   * Note the {@code Logger} should be initialized separately, before this method is invoked.
   * @return {@code true} on success; {@code false} if any component fails to initialize.
   */
  public static boolean init(Path rootFolder, boolean server){
    entropy = new SecureRandom();
    saving = new AtomicBoolean();
    Database.server = server;
    boolean ret = true;
    if (server){
      ret&=Keys.init(rootFolder.resolve("keys"));
      ret&=Config.init(rootFolder.resolve("config.txt"));
      ret&=Servers.init(rootFolder.resolve("Servers"));
      ret&=SyncTasks.init(rootFolder.resolve("file_synchronization"));
      SocketWrapper.config = new SocketWrapperConfig(){
        public long getTimeout(){
          return Config.timeout;
        }
        public long getPingInterval(){
          return Config.pingInterval;
        }
      };
    }else{
      try{
        Keys.initCrypto(2048);
      }catch(Throwable e){
        ret = false;
        Logger.log("Error occurred while initializing cryptographic objects.", e);
      }
    }
    ret&=Operators.init(rootFolder.resolve("Operators"));
    return ret;
  }
  /**
   * Saves all database components.
   * Optimized to return immediately if another invokation of this method is concurrently executing.
   */
  public static boolean save(){
    if (saving.compareAndSet(false, true)){
      boolean ret = true;
      if (server){
        ret&=Config.save();
        ret&=Servers.save();
        ret&=Keys.save();
        ret&=SyncTasks.save();
      }
      ret&=Operators.save();
      saving.set(false);
      return ret;
    }else{
      return true;
    }
  }
  /**
   * Used to determine whether a given name is valid.
   * @param name the string to validate.
   * @param spaces whether {@code name} is allowed to include spaces.
   * @return {@code true} if {@code name} is non-empty, not equal to {@code NULL} (ignoring case), has length less than or equal to {@code 32}, and contains only letters, numbers, underscores, and (possibly) spaces; {@code false} otherwise.
   */
  public static boolean validateName(String name, boolean spaces){
    char c;
    int len = name.length();
    if (len<1 || len>32 || name.charAt(0)==' ' || name.charAt(len-1)==' '){
      return false;
    }
    for (int i=0;i<len;++i){
      c = name.charAt(i);
      if ((c<'a' || c>'z') && (c<'A' || c>'Z') && c!='_' && (c<'0' || c>'9') && (!spaces || c!=' ')){
        return false;
      }
    }
    if (name.equalsIgnoreCase("NULL")){
      return false;
    }
    return true;
  }
  /**
   * Used to determine whether a given password is sufficiently secure.
   * @param password the char array to validate.
   * @return {@code true} if {@code 8<=password.length<=128} and the code-point range is {@code >=13}; {@code false} otherwise.
   */
  public static boolean validatePassword(char[] password){
    if (password.length<8 || password.length>128){
      return false;
    }
    char min = Character.MAX_VALUE;
    char max = Character.MIN_VALUE;
    char b;
    for (int i=0;i<password.length;++i){
      b = password[i];
      if (b<min){
        min = b;
      }
      if (b>max){
        max = b;
      }
    }
    return max-min>12;
  }
  /**
   * Used to determine whether a given password is sufficiently secure.
   * @param password the byte array to validate.
   * @return {@code true} if {@code 8<=password.length<=128} and the code-point range is {@code >=13}; {@code false} otherwise.
   */
  public static boolean validatePassword(byte[] password){
    return validatePassword(java.nio.charset.StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(password)).array());
  }
  /**
   * Specifies whether or not this application is meant to act as a server to host the database (some behavior gets changed slightly).
   */
  public static boolean isServer(){
    return server;
  }
}