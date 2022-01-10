package aces.webctrl.centralizer.common;
import java.security.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
//TODO (New Feature) - Synchronization - Synchronizes files/directories across clients
//TODO (New Feature) - Data Retrieval - Retrieves data from clients
//TODO (New Feature) - Script Executor - Executes scripts on client machines
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
      ret&=Config.init(rootFolder.resolve("config.txt"));
      ret&=Servers.init(rootFolder.resolve("Servers"));
      ret&=Keys.init(rootFolder.resolve("keys"));
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
      }catch(Exception e){
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
   * @return {@code true} if {@code name} is non-empty, has length less than or equal to {@code 32}, and contains only letters, numbers, underscores, and (possibly) spaces; {@code false} otherwise.
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
    return true;
  }
  /**
   * Used to determine whether a given password is sufficiently secure.
   * @param password the byte array to validate.
   * @return {@code true} if {@code 8<=password.length<=128} and the code-point range is {@code >=13}; {@code false} otherwise.
   */
  public static boolean validatePassword(byte[] password){
    if (password.length<8 || password.length>128){
      return false;
    }
    byte min = Byte.MAX_VALUE;
    byte max = Byte.MIN_VALUE;
    byte b;
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
   * Specifies whether or not this application is meant to act as a server to host the database (some behavior gets changed slightly).
   */
  public static boolean isServer(){
    return server;
  }
}