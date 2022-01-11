package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.Calendar;
public class ClientConfig {
  /**
   * The filepath specifying where to load and save the primary configuration file.
   */
  private volatile static Path configFile;
  /**
   * The IP address of the database.
   */
  public volatile static String host = null;
  /**
   * The public key of the central database.
   */
  public volatile static Key databaseKey = null;
  /**
   * This server's unique identification number.
   */
  public volatile static int ID = -1;
  /**
   * Sent to the database as this server's unique identifier.
   */
  public volatile static byte[] identifier = null;
  /**
   * This server's name.
   * Any changes should be first checked with {@code Database.validateName(name,true)}.
   */
  public volatile static String name = null;
  /**
   * This server's description.
   */
  public volatile static String description = null;
  /**
   * Specifies where the database binds to listen for connections
   * Default value is 1978, the year Automatic Controls Equipment Systems, Inc. was founded.
   */
  public volatile static int port = 1978;
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
   * If this timeout (in milliseconds) is exceeded while waiting for a response, the connection will be terminated.
   * The default value is 1 minute.
   */
  public volatile static long timeout = 60000L;
  /**
   * Specifies how often (in milliseconds) to ping the central database.
   */
  public volatile static long pingInterval = 10000L;
  /**
   * If this addon is disconnected from the central database, then it will try to reconnect after this timeout (specified in milliseconds).
   * The default value is 5 seconds.
   */
  public volatile static long reconnectTimeout = 5000L;
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
   * Sets the path to the primary configuration file.
   */
  public static void init(Path configFile){
    ClientConfig.configFile = configFile;
  }
  /**
   * Load the primary configuration file.
   */
  public static boolean load(){
    try{
      if (Files.exists(configFile)){
        byte[] arr;
        synchronized (ClientConfig.class){
          arr = Files.readAllBytes(configFile);
        }
        deserialize(arr);
      }
      return true;
    }catch(Exception e){
      Logger.log("Error occurred while loading the primary configuration file.", e);
      return false;
    }
  }
  /**
   * Save the primary configuration file.
   */
  public static boolean save(){
    try{
      ByteBuffer buf = ByteBuffer.wrap(serialize());
      synchronized(ClientConfig.class){
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
    }catch(Exception e){
      Logger.log("Error occurred while saving the primary configuration file.", e);
      return false;
    }
  }
  private static byte[] serialize(){
    byte[] hostBytes = (host==null?"NULL":host).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] nameBytes = (name==null?"NULL":name).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] descriptionBytes = (description==null?"NULL":description).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    int len = hostBytes.length+nameBytes.length+descriptionBytes.length+57;
    Key k = databaseKey;
    if (k!=null){
      len+=k.length(false);
    }
    if (ID>=0){
      len+=identifier.length+4;
    }
    SerializationStream s = new SerializationStream(len);
    s.write(hostBytes);
    s.write(nameBytes);
    s.write(descriptionBytes);
    s.write(port);
    s.write(backupHr);
    s.write(backupMin);
    s.write(backupSec);
    s.write(deleteLogAfter);
    s.write(timeout);
    s.write(reconnectTimeout);
    s.write(PacketLogger.isWorking());
    s.write(ID);
    if (ID>=0){
      s.write(identifier);
    }
    if (k!=null){
      k.serialize(s,false);
    }
    return s.data;
  }
  private static void deserialize(byte[] arr){
    SerializationStream s = new SerializationStream(arr);
    host = s.readString();
    if (host.equals("NULL")){
      host = null;
    }
    name = s.readString();
    if (name.equals("NULL")){
      name = null;
    }
    description = s.readString();
    if (description.equals("NULL")){
      description = null;
    }
    port = s.readInt();
    backupHr = s.readInt();
    backupMin = s.readInt();
    backupSec = s.readInt();
    deleteLogAfter = s.readLong();
    timeout = s.readLong();
    reconnectTimeout = s.readLong();
    if (s.readBoolean()){
      PacketLogger.start();
    }else{
      PacketLogger.stop();
    }
    ID = s.readInt();
    if (ID>=0){
      identifier = s.readBytes();
    }else{
      identifier = null;
    }
    if (s.end()){
      databaseKey = null;
    }else{
      try{
        databaseKey = Key.deserialize(s,false);
        if (!s.end()){
          Logger.log("The primary configuration file \""+configFile.toString()+"\" may have been corrupted.");
        }
      }catch(Exception e){
        databaseKey = null;
        Logger.log("Error occurred while deserializing the central database's public key.", e);
      }
    }
  }
}