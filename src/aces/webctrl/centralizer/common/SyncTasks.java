/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
/**
 * Thread-safe namespace which encapsulates a collection of {@code SyncTask} objects.
 * Invoke {@link #init(Path)} before calling any other method.
 */
public class SyncTasks {
  /** Path to primary data file. */
  private volatile static Path p;
  /** List of synchronization tasks indexed by {@code ID}. */
  private volatile static ArrayList<SyncTask> tasks = null;
  /** Lock which controls access to {@code tasks}. */
  private volatile static ReentrantReadWriteLock lock;
  /** Keeps track of how many synchronization tasks exist. */
  private volatile static AtomicInteger count;
  /**
   * @return how many synchronization tasks exist.
   */
  public static int count(){
    return count.get();
  }
  /**
   * Initializes parameters.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  protected static boolean init(Path p){
    lock = new ReentrantReadWriteLock();
    count = new AtomicInteger();
    SyncTasks.p = p;
    try{
      if (Files.exists(p)){
        return load();
      }else{
        tasks = new ArrayList<SyncTask>();
        return true;
      }
    }catch(Throwable e){
      Logger.log("Error occurred while loading synchronization tasks.", e);
      return false;
    }
  }
  /**
   * @return a {@code SyncTask} list deserialized from the remaining bytes in the given {@code SerializationStream}.
   */
  public static ArrayList<SyncTask> deserialize(SerializationStream s){
    ArrayList<SyncTask> list = new ArrayList<SyncTask>();
    try{
      while (!s.end()){
        list.add(SyncTask.deserialize(s));
      }
    }catch(Throwable e){
      Logger.log("Error deserializing SyncTask.", e);
    }
    return list;
  }
  /**
   * @return a {@code byte} array containing every {@code SyncTask} encapsulated by this namespace.
   */
  public static byte[] serialize(){
    int len = 0;
    ArrayList<byte[]> bytes;
    lock.readLock().lock();
    try{
      byte[] arr;
      bytes = new ArrayList<byte[]>(count.get());
      for (SyncTask t:tasks){
        if (t!=null){
          arr = t.serialize();
          len+=arr.length;
          bytes.add(arr);
        }
      }
    }finally{
      lock.readLock().unlock();
    }
    SerializationStream s = new SerializationStream(len);
    for (byte[] b:bytes){
      s.writeRaw(b);
    }
    return s.data;
  }
  /**
   * Loads the synchronization task data file.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  private synchronized static boolean load(){
    try{
      boolean ret = true;
      ArrayList<SyncTask> list = new ArrayList<SyncTask>();
      int size = 0;
      SerializationStream s = new SerializationStream(Files.readAllBytes(p));
      try{
        SyncTask t;
        while (!s.end()){
          t = SyncTask.deserialize(s);
          list.add(t);
          size = Math.max(size, t.getID());
        }
      }catch(Throwable e){
        ret = false;
        Logger.log("Error occurred while loading synchronization task from data file.", e);
      }
      size+=16;
      tasks = new ArrayList<SyncTask>(size);
      for (int i=0;i<size;++i){
        tasks.add(null);
      }
      int ID;
      for (SyncTask t:list){
        ID = t.getID();
        if (ID>=0 && tasks.get(ID)==null){
          t.checkIDs();
          tasks.set(ID,t);
          count.incrementAndGet();
        }else{
          ret = false;
          if (ID<0){
            Logger.log("Invalid SyncTask ID detected: "+ID+'.');
          }else{
            Logger.log("Duplicate SyncTask ID detected: "+ID+'.');
          }
        }
      }
      return ret;
    }catch(Throwable e){
      Logger.log("Error occurred while loading synchronization tasks.", e);
      return false;
    }
  }
  /**
   * Saves all synchronization tasks to the primary data file.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public synchronized static boolean save(){
    try{
      final ByteBuffer buf = ByteBuffer.wrap(serialize());
      try(
        FileChannel out = FileChannel.open(p, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        FileLock lock = out.tryLock();
      ){
        while (buf.hasRemaining()){
          out.write(buf);
        }
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occurred while saving synchronization tasks.", e);
      return false;
    }
  }
  /**
   * Retrieves the synchronization task with the specified ID.
   * @param ID
   * @return the retrieved {@code SyncTask} if it exists; otherwise {@code null}.
   */
  public static SyncTask get(int ID){
    lock.readLock().lock();
    try{
      return ID<0 || ID>=tasks.size() ? null : tasks.get(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Clears all synchronization tasks.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean clear(){
    lock.writeLock().lock();
    try{
      count.set(0);
      int s = tasks.size();
      for (int i=0;i<s;++i){
        tasks.set(i,null);
      }
      return true;
    }catch(Throwable e){
      Logger.logAsync("Error occurred while clearing synchronization tasks.", e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Removes a synchronization task.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean remove(SyncTask t){
    if (t==null){
      return true;
    }
    int ID = t.getID();
    if (ID<0){
      return true;
    }
    lock.writeLock().lock();
    try{
      if (ID>=tasks.size()){
        return true;
      }
      if (tasks.set(ID,null)!=null){
        count.decrementAndGet();
      }
      return true;
    }catch(Throwable e){
      Logger.logAsync("Error occurred while removing SyncTask with ID="+ID+'.', e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Creates a new synchronization task.
   * @param description describes the task.
   * @param syncInterval specifies the time in milliseconds between task executions, or {@code -1} for manual trigger only.
   * @param src specifies the path to the source file to synchronize on the database host machine.
   * @param dst specifies the path of the destination file resolved on each client machine.
   * @param allServers specifies whether this task should apply to all servers.
   * @return the created {@code SyncTask} or {@code null} if one could not be created.
   */
  public static SyncTask add(String description, long syncInterval, String src, String dst, boolean allServers){
    if (description==null || src==null || dst==null){
      return null;
    }
    lock.writeLock().lock();
    try{
      int len = tasks.size();
      int ID = len;
      for (int i=0;i<len;++i){
        if (tasks.get(i)==null){
          ID = i;
          break;
        }
      }
      SyncTask t = new SyncTask(ID, description, syncInterval, src, dst, allServers);
      if (ID==len){
        tasks.add(t);
      }else{
        tasks.set(ID,t);
      }
      count.incrementAndGet();
      return t;
    }catch(Throwable e){
      Logger.logAsync("Error occurred while creating synchronization task.", e);
      return null;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Applies the given predicate to each synchronization task.
   * <p>
   * WARNING - The predicate should not invoke methods which structurally modify the {@code SyncTask} list (otherwise this method will block indefinitely).
   * More precisely, the predicate must not make any attempt to acquire a write lock on the {@code SyncTask} list.
   * This method acquires a read lock to perform iteration, and read locks cannot be promoted to write locks.
   * @return {@code true} if some predicate returned true, in which case iteration prematurely halted; {@code false} if all predicates returned false or if any other error occurred.
   */
  public static boolean forEach(Predicate<SyncTask> func){
    lock.readLock().lock();
    try{
      for (SyncTask t:tasks){
        if (t!=null && func.test(t)){
          return true;
        }
      }
      return false;
    }catch(Throwable e){
      Logger.logAsync("Error occured while iterating over the synchronization task list.", e);
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
}