/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.Calendar;
/**
 * Thread-safe namespace which encapsulates all primary configuration parameters.
 * Parameters are saved to the filesystem in a user-friendly format, so the configuration file can be modified in any text editor.
 */
public class Config {
  /**
   * Hardcoded internal version string for the application.
   * Used to determine compatibility when connecting remote hosts.
   */
  public final static String VERSION = "0.1.0";
  /**
   * Used for evaluating compatible version strings.
   */
  private final static String VERSION_SUBSTRING = VERSION.substring(0,VERSION.lastIndexOf('.'));
  /**
   * Raw version bytes.
   */
  public final static byte[] VERSION_RAW = VERSION.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  /**
   * This application's display name
   */
  public final static String NAME = "Centralizer Database for WebCTRL";
  /**
   * The filepath specifying where to load and save the primary configuration file.
   */
  private volatile static Path configFile;
  /**
   * Specifies where the database binds to listen for connections
   * Default value is 1978, the year Automatic Controls Equipment Systems, Inc. was founded.
   */
  public volatile static int port = 1978;
  /**
   * The maximum waitlist size for client connection processing.
   * Default value is 1028.
   */
  public volatile static int backlog = 1028;
  /**
   * Specifies the hour (0-23) to backup data in RAM to the hard-drive.
   * Default value is 3.
   */
  public volatile static int backupHr = 3;
  /**
   * Specifies the minute (0-59) to backup data in RAM to the hard-drive.
   * Default value is 0.
   */
  public volatile static int backupMin = 0;
  /**
   * Specifies the second (0-59) to backup data in RAM to the hard-drive.
   * Default value is 0.
   */
  public volatile static int backupSec = 0;
  /**
   * Specifies how long (in milliseconds) to keep log entries before deleting them.
   * Default value is one year.
   */
  public volatile static long deleteLogAfter = 31557600000L;
  /**
   * If this timeout (in milliseconds) is exceeded while waiting for a client response, the connection will be terminated.
   * The default value is 1 minute.
   */
  public volatile static long timeout = 60000L;
  /**
   * The database sends this value to clients, which specifies how often (in milliseconds) they should send status updates.
   * The default value is 10 seconds.
   */
  public volatile static long pingInterval = 10000L;
  /**
   * Specifies how long (in milliseconds) it takes for an operator to be logged off the database due to inactivity.
   * The default value is 1 hour.
   * @see Operator#getNavigationTimeout()
   */
  public volatile static long operatorTimeout = 3600000L;
  /**
   * Operators are allowed a maximum of {@code loginAttempts} failed logins during any period of {@code loginTimePeriod}.
   * If exceeded, an operator lockout of {@code loginLockoutTime} is incurred.
   * The default value for {@code loginAttempts} is 50.
   * Setting {@code loginAttempts} to {@code -1} disables this lockout mechanism.
   */
  public volatile static int loginAttempts = 50;
  /**
   * Operators are allowed a maximum of {@code loginAttempts} failed logins during any period of {@code loginTimePeriod}.
   * If exceeded, an operator lockout of {@code loginLockoutTime} is incurred.
   * The default value for {@code loginTimePeriod} is 5 minutes.
   */
  public volatile static long loginTimePeriod = 300000L;
  /**
   * Operators are allowed a maximum of {@code loginAttempts} failed logins during any period of {@code loginTimePeriod}.
   * If exceeded, an operator lockout of {@code loginLockoutTime} is incurred.
   * The default value for {@code loginLockoutTime} is 1 hour.
   */
  public volatile static long loginLockoutTime = 3600000L;
  /**
   * Used in the add-on to record whether the database is doing packetCapture.
   */
  public volatile static boolean packetCapture = false;
  /**
   * Clients must possess this secret key to register as a new server in this database.
   */
  public volatile static long connectionKey = 0;
  /**
   * Specifies how often to check whether any synchronization tasks need to be triggered.
   */
  public final static long syncCheckInterval = 600000L;
  /**
   * Compares the given version string to the hardcoded version string of this application.
   * Assuming each version string is of the form "MAJOR.MINOR.PATCH",
   * two version strings are compatible whenever the MAJOR and MINOR versions agree.
   * The PATCH version number may differ between compatible instances.
   * @param ver is the given version string.
   * @return whether or not the given version string is compatible with the hardcoded version string.
   */
  public static boolean isCompatibleVersion(String ver){
    if (ver==null){
      return false;
    }
    int i = ver.lastIndexOf('.');
    if (i==-1){
      return false;
    }
    return VERSION_SUBSTRING.equals(ver.substring(0,i));
  }
  /**
   * Initializes parameters.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean init(Path configFile){
    Config.configFile = configFile;
    return load();
  }
  /**
   * @return the target value of {@code System.currentTimeMillis()} at the time of the next database backup.
   */
  public static long nextBackupTime(){
    Calendar c = Calendar.getInstance();
    int dif = ((backupHr-c.get(Calendar.HOUR_OF_DAY))*60+backupMin-c.get(Calendar.MINUTE))*60+backupSec-c.get(Calendar.SECOND);
    while (dif<=0){
      dif+=86400;
    }
    return c.getTimeInMillis()+dif*1000L;
  }
  /**
   * Used to precompute serialization length.
   */
  public final static int LENGTH = 73;
  /**
   * Encodes parameters which may be remotely configured from clients.
   */
  public static void serialize(SerializationStream s){
    s.write(port);
    s.write(backlog);
    s.write(deleteLogAfter);
    s.write(timeout);
    s.write(operatorTimeout);
    s.write(pingInterval);
    s.write(loginAttempts);
    s.write(loginTimePeriod);
    s.write(loginLockoutTime);
    s.write(backupHr);
    s.write(backupMin);
    s.write(backupSec);
    s.write(Database.isServer()?PacketLogger.isWorking():packetCapture);
  }
  /**
   * Decodes parameters which may be remotely configured by clients.
   * @param s the {@code SerializationStream} containing the information to be deserialized.
   * @return whether or not {@code pingInterval} changed.
   * @throws IndexOutOfBoundsException if {@code data} has been corrupted.
   */
  public static boolean deserialize(SerializationStream s) throws IndexOutOfBoundsException {
    port = s.readInt();
    backlog = s.readInt();
    deleteLogAfter = s.readLong();
    timeout = s.readLong();
    operatorTimeout = s.readLong();
    long tmp = pingInterval;
    pingInterval = s.readLong();
    loginAttempts = s.readInt();
    loginTimePeriod = s.readLong();
    loginLockoutTime = s.readLong();
    backupHr = s.readInt();
    backupMin = s.readInt();
    backupSec = s.readInt();
    packetCapture = s.readBoolean();
    if (Database.isServer()){
      if (packetCapture){
        PacketLogger.startAsync();
      }else{
        PacketLogger.stopAsync();
      }
    }
    return tmp!=pingInterval;
  }
  /**
   * Helper method for {@link #loadConfig()}.
   * @param key
   * @param value
   * @return {@code true} on success; {@code false} if any error occurs.
   */
  private static boolean setConfigParameter(String key, String value){
    try{
      value = value.trim();
      switch (key.toUpperCase()){
        case "VERSION":{
          if (!isCompatibleVersion(value)){
            Logger.log("Configuration file version ("+value+") is not compatible with the application's internal version ("+VERSION+").");
            return false;
          }
          break;
        }
        case "PORT":{
          port = Integer.parseInt(value);
          break;
        }
        case "CONNECTIONKEY":{
          connectionKey = Long.parseUnsignedLong(value, 16);
          break;
        }
        case "PACKETCAPTURE":{
          if (Boolean.parseBoolean(value)){
            PacketLogger.start();
          }else{
            PacketLogger.stop();
          }
          break;
        }
        case "DELETELOGAFTER":{
          deleteLogAfter = Long.parseLong(value);
          break;
        }
        case "BACKLOG":{
          backlog = Integer.parseInt(value);
          break;
        }
        case "TIMEOUT":{
          timeout = Long.parseLong(value);
          break;
        }
        case "PINGINTERVAL":{
          pingInterval = Long.parseLong(value);
          break;
        }
        case "OPERATORTIMEOUT":{
          operatorTimeout = Long.parseLong(value);
          break;
        }
        case "LOGINATTEMPTS":{
          loginAttempts = Integer.parseInt(value);
          break;
        }
        case "LOGINTIMEPERIOD":{
          loginTimePeriod = Long.parseLong(value);
          break;
        }
        case "LOGINLOCKOUTTIME":{
          loginLockoutTime = Long.parseLong(value);
          break;
        }
        case "BACKUPTIME":{
          String[] arr = value.split(":");
          if (arr.length==3){
            backupHr = Integer.parseInt(arr[0]);
            backupMin = Integer.parseInt(arr[1]);
            backupSec = Integer.parseInt(arr[2]);
          }else{
            Logger.log("BackupTime configuration value has invalid format.");
            return false;
          }
          break;
        }
        default:{
          Logger.log("Unrecognized key-value pair in the primary configuration file ("+key+':'+value+')');
          return false;
        }
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occured while parsing configuration file.", e);
      return false;
    }
  }
  /**
   * Loads data from the primary configuration file into memory.
   * Invokes {@link #save()} if the configuration file does not exist.
   * Guaranteed to generate log message whenever this method returns false.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean load(){
    try{
      byte[] arr;
      synchronized (Config.class){
        if (!Files.exists(configFile)){
          connectionKey = Database.entropy.nextLong();
          return save();
        }
        arr = Files.readAllBytes(configFile);
      }
      boolean ret = true;
      StringBuilder key = new StringBuilder();
      StringBuilder value = new StringBuilder();
      char c;
      for (int i=0;i<arr.length;++i){
        c = (char)arr[i];
        if (c=='='){
          for (++i;i<arr.length;++i){
            c = (char)arr[i];
            if (c=='\n'){
              ret&=setConfigParameter(key.toString(),value.toString());
              key.setLength(0);
              value.setLength(0);
              break;
            }else if (c!='\r'){
              value.append(c);
            }
          }
        }else if (c==';'){
          for (++i;i<arr.length;++i){
            c = (char)arr[i];
            if (c=='\n'){
              break;
            }
          }
        }else if (c!='\n' && c!='\r'){
          key.append(c);
        }
      }
      if (key.length()>0){
        ret&=setConfigParameter(key.toString(),value.toString());
      }
      return ret;
    }catch(Throwable e){
      Logger.log("Error occured while loading primary configuration file.", e);
      return false;
    }
  }
  /**
   * Overwrites the primary configuration file with parameters stored in the application's memory.
   * Guaranteed to generate log message whenever this method returns false.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean save(){
    try{
      StringBuilder sb = new StringBuilder(1024);
      sb.append(';'+NAME+" - Primary Configuration File\n");
      sb.append(";PublicKeyHash=").append(Keys.getPreferredKey().getHashString());
      sb.append("\n;Note all time intervals are specified in milliseconds.");
      sb.append("\n\n;Used to determine compatibility\n");
      sb.append("Version=").append(VERSION);
      sb.append("\n\n;Specifies where the database binds to listen for connections\n");
      sb.append("Port=").append(port);
      sb.append("\n\n;This secret key is required for registration of new servers\n");
      sb.append("ConnectionKey=").append(Long.toHexString(connectionKey));
      sb.append("\n\n;Specifies whether data packets should be captured and logged\n");
      sb.append("PacketCapture=").append(PacketLogger.isWorking());
      sb.append("\n\n;Specifies the time of day (HR[0-23]:MIN[0-59]:SEC[0-59]) to backup data in RAM to the hard-drive\n");
      sb.append("BackupTime=").append(backupHr).append(':').append(backupMin).append(':').append(backupSec);
      sb.append("\n\n;The maximum waitlist size for client connection processing\n");
      sb.append("BackLog=").append(backlog);
      sb.append("\n\n;Specifies how long to wait for a client response before assuming the connection has been lost\n");
      sb.append("Timeout=").append(timeout);
      sb.append("\n\n;Specifies how long it takes for an operator to be logged off the database due to inactivity\n");
      sb.append("OperatorTimeout=").append(operatorTimeout);
      sb.append("\n\n;Specifes how often clients should send status updates to the database\n");
      sb.append("PingInterval=").append(pingInterval);
      sb.append("\n\n;Specifies how long to keep log entries before erasing them\n");
      sb.append("DeleteLogAfter=").append(deleteLogAfter);
      sb.append("\n\n;Operators are allowed a maximum of {LoginAttempts} failed logins during any period of {LoginTimePeriod}.\n");
      sb.append(";If exceeded, an operator lockout of {LoginLockoutTime} is incurred.");
      sb.append("\nLoginAttempts=").append(loginAttempts);
      sb.append("\nLoginTimePeriod=").append(loginTimePeriod);
      sb.append("\nLoginLockoutTime=").append(loginLockoutTime);
      ByteBuffer buf = ByteBuffer.wrap(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      synchronized(Config.class){
        try(
          FileChannel out = FileChannel.open(configFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
          FileLock lock = out.tryLock();
        ){
          while (buf.hasRemaining()){
            out.write(buf);
          }
        }
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occured while saving primary configuration file.", e);
      return false;
    }
  }
}