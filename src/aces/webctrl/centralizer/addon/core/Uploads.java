package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.locks.*;
/**
 * Thread-safe namespace encapsulating a collection of {@code Upload} objects.
 */
public class Uploads {
  /** Stores {@code Upload} objects indexed by ID. */
  private final static TreeMap<Integer,Upload> map = new TreeMap<Integer,Upload>();
  /** Controls access to {@code map}. */
  private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  /** Specifies where to load and save data. */
  private volatile static Path file = null;
  /**
   * @return the number of {@code Upload} objects in this collection.
   */
  public static int size(){
    return map.size();
  }
  /**
   * Triggers all uploads.
   * @return a collection of results from the upload operations.
   */
  public static ArrayList<Result<Byte>> trigger(){
    lock.readLock().lock();
    try{
      final ArrayList<Result<Byte>> rets = new ArrayList<Result<Byte>>(map.size());
      for (Upload x:map.values()){
        rets.add(x.trigger());
      }
      return rets;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Forcibly triggers all uploads.
   * @return a collection of results from the upload operations.
   */
  public static ArrayList<Result<Byte>> forceTrigger(){
    lock.readLock().lock();
    try{
      final ArrayList<Result<Byte>> rets = new ArrayList<Result<Byte>>(map.size());
      for (Upload x:map.values()){
        rets.add(x.forceTrigger());
      }
      return rets;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Removes the {@code Upload} with the given ID.
   * @return the removed value, or {@code null} if the given ID does not exist.
   */
  public static Upload remove(Integer ID){
    lock.writeLock().lock();
    try{
      return map.remove(ID);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * @return the {@code Upload} object with the given ID, or {@code null} if none exists.
   */
  public static Upload get(Integer ID){
    lock.readLock().lock();
    try{
      return map.get(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Adds an {@code Upload} object to the list.
   */
  public static void add(Upload x){
    lock.writeLock().lock();
    try{
      map.put(x.getID(),x);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Initializes and loads {@code Upload} objects.
   * @param file specifies where to load and save data.
   */
  public synchronized static void init(Path file){
    Uploads.file = file;
    try{
      if (Files.isRegularFile(file)){
        SerializationStream s = new SerializationStream(Files.readAllBytes(file));
        Upload x;
        while (!s.end()){
          x = Upload.deserialize(s);
          if (map.put(x.getID(),x)!=null){
            Logger.log("Duplicate upload ID detected.");
          }
        }
      }
    }catch(Throwable t){
      Logger.log("Error occurred while initializing uploads.", t);
    }
  }
  /**
   * Saves all {@code Upload} objects to file-system.
   */
  public synchronized static boolean save(){
    if (file!=null){
      try{
        int len = 0;
        ArrayList<byte[]> list;
        lock.readLock().lock();
        try{
          list = new ArrayList<byte[]>(map.size());
          byte[] arr;
          for (Upload x:map.values()){
            arr = x.serialize();
            list.add(arr);
            len+=arr.length;
          }
        }finally{
          lock.readLock().unlock();
        }
        SerializationStream s = new SerializationStream(len);
        for (byte[] arr:list){
          s.writeRaw(arr);
        }
        list = null;
        ByteBuffer buf = ByteBuffer.wrap(s.data);
        s = null;
        try(
          FileChannel out = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        ){
          while (buf.hasRemaining()){
            out.write(buf);
          }
        }
        return true;
      }catch(Throwable t){
        Logger.log("Error occurred while saving uploads.", t);
      }
    }
    return false;
  }
  /**
   * Applies the given predicate to each upload.
   * <p>
   * WARNING - The predicate should not invoke methods which structurally modify the {@code Upload} list (otherwise this method will block indefinitely).
   * More precisely, the predicate must not make any attempt to acquire a write lock on the {@code Upload} list.
   * This method acquires a read lock to perform iteration, and read locks cannot be promoted to write locks.
   * @return {@code true} if some predicate returned true, in which case iteration prematurely halted; {@code false} if all predicates returned false or if any other error occurred.
   */
  public static boolean forEach(java.util.function.Predicate<Upload> func){
    lock.readLock().lock();
    try{
      for (Upload x:map.values()){
        if (func.test(x)){
          return true;
        }
      }
      return false;
    }catch(Throwable e){
      Logger.logAsync("Error occured while iterating over the upload list.", e);
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
}