/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.locks.*;
/**
 * Thread-safe class encapsulating a single synchronization task.
 */
public class SyncTask {
  /** This task's identification number. */
  private volatile int ID;
  /** A description of this synchronization task. */
  private volatile String description = null;
  /** The time in milliseconds between scheduled executions of this action. */
  private volatile long syncInterval = -1;
  /** The next execution time for this task as compared to {@code System.currentTimeMillis()}. */
  private volatile long nextTriggerTime = -1;
  /** Source path of files to synchronize on the database host machine. */
  private volatile String src = null;
  /** Destination path to send files to on client machines. */
  private volatile String dst = null;
  /** Whether this task applies to all servers. */
  private volatile boolean allServers = false;
  /** A set of specific server IDs this task applies to in the case that {@code allServers} is {@code false}. */
  private volatile TreeSet<Integer> servers = new TreeSet<Integer>();
  /** Controls access to {@code servers}. */
  private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  /**
   * Used for deserialization.
   */
  private SyncTask(){}
  /**
   * Creates a new synchronization task.
   */
  protected SyncTask(int ID, String description, long syncInterval, String src, String dst, boolean allServers){
    this.ID = ID;
    this.description = description;
    setSyncInterval(syncInterval);
    this.src = src;
    this.dst = dst;
    this.allServers = allServers;
  }
  /**
   * Applies the given predicate to each synchronization task.
   * <p>
   * WARNING - The predicate should not invoke methods which structurally modify the {@code SyncTask} list (otherwise this method will block indefinitely).
   * More precisely, the predicate must not make any attempt to acquire a write lock on the {@code SyncTask} list.
   * This method acquires a read lock to perform iteration, and read locks cannot be promoted to write locks.
   * @return {@code true} if some predicate returned true, in which case iteration prematurely halted; {@code false} if all predicates returned false or if any other error occurred.
   */
  public boolean forEach(Predicate<Integer> func){
    lock.readLock().lock();
    try{
      for (Integer x:servers){
        if (x!=null && func.test(x)){
          return true;
        }
      }
      return false;
    }catch(Throwable e){
      Logger.logAsync("Error occured while iterating over the server ID list.", e);
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Removes the specified server from the server list.
   * @return {@code true} if the server was removed successfully; {@code false} if the server never existed.
   */
  public boolean remove(int ID){
    lock.writeLock().lock();
    try{
      return servers.remove(ID);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Adds the specified server to the server list.
   * @return {@code true} if the server was added successfully; {@code false} if the server was already present.
   */
  public boolean add(int ID){
    lock.writeLock().lock();
    try{
      return servers.add(ID);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * @return whether this task applies to the server with the given {@code ID}.
   */
  public boolean appliesTo(int ID){
    if (allServers){
      return true;
    }
    lock.readLock().lock();
    try{
      return servers.contains(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Specifies whether this task should apply to all servers.
   */
  public void setAllServers(boolean allServers){
    this.allServers = allServers;
  }
  /**
   * @return whether this task should apply to all servers.
   */
  public boolean isAllServers(){
    return allServers;
  }
  /**
   * Serializes this object into an array of bytes.
   */
  public byte[] serialize(){
    byte[] a = description.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] b = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] c = dst.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    lock.readLock().lock();
    int len = servers.size();
    SerializationStream s = new SerializationStream(a.length+b.length+c.length+29+(len<<2));
    s.write(len);
    for (Integer x:servers){
      s.write(x);
    }
    lock.readLock().unlock();
    s.write(ID);
    s.write(syncInterval);
    s.write(a);
    s.write(b);
    s.write(c);
    s.write(allServers);
    return s.data;
  }
  /**
   * @return a synchronization task deserialized from the given stream.
   */
  public static SyncTask deserialize(SerializationStream s){
    SyncTask t = new SyncTask();
    int len = s.readInt();
    for (int i=0;i<len;++i){
      t.servers.add(s.readInt());
    }
    t.ID = s.readInt();
    t.setSyncInterval(s.readLong());
    t.description = s.readString();
    t.src = s.readString();
    t.dst = s.readString();
    t.allServers = s.readBoolean();
    return t;
  }
  /**
   * Moves resources and properties from the given {@code SyncTask} into this object.
   * The given {@code SyncTask} should not be touched after using this method.
   */
  public void copy(SyncTask t){
    description = t.description;
    src = t.src;
    dst = t.dst;
    allServers = t.allServers;
    setSyncInterval(t.syncInterval);
    lock.writeLock().lock();
    servers = t.servers;
    lock.writeLock().unlock();
    t.servers = null;
  }
  /**
   * Sets the synchronization interval for this task.
   * Changes the next trigger time accordingly.
   */
  public synchronized void setSyncInterval(long syncInterval){
    if (syncInterval<-1){
      syncInterval = -1;
    }
    if (syncInterval==-1){
      nextTriggerTime = -1;
    }else{
      if (nextTriggerTime>0){
        nextTriggerTime+=syncInterval-this.syncInterval;
      }else{
        nextTriggerTime = 0;
      }
    }
    this.syncInterval = syncInterval;
  }
  /**
   * If enabled, resets the next trigger time to occur in {@link #getSyncInterval syncInterval} milliseconds.
   */
  public synchronized void resetTrigger(){
    if (syncInterval==-1){
      nextTriggerTime = -1;
    }else{
      nextTriggerTime = System.currentTimeMillis()+syncInterval;
    }
  }
  /**
   * @return the identification number for this synchronization task.
   */
  public int getID(){
    return ID;
  }
  /**
   * @return the source path of this synchronization task.
   */
  public String getSource(){
    return src;
  }
  /**
   * @return the destination path of this synchronization task.
   */
  public String getDestination(){
    return dst;
  }
  /**
   * Sets the source path of this synchronization task.
   */
  public void setSource(String src){
    this.src = src;
  }
  /**
   * Sets the destination path of this synchronization task.
   */
  public void setDestination(String dst){
    this.dst = dst;
  }
  /**
   * Sets the description for this task.
   */
  public void setDescription(String description){
    this.description = description;
  }
  /**
   * @return the description of this task.
   */
  public String getDescription(){
    return description;
  }
  /**
   * @return the synchronization interval of this task in milliseconds. {@code -1} indicates manual sync only.
   */
  public long getSyncInterval(){
    return syncInterval;
  }
  /**
   * @return the next trigger time of this task as compared to {@code System.currentTimeMillis()}. {@code -1} indicates manual sync only.
   */
  public long getNextTriggerTime(){
    return nextTriggerTime;
  }
  /**
   * Resolves a string against another path.
   * The first character of the passed string must be {@code /} or {@code \} if you want resolution relative to the given path.
   * Resolution will occur absolutely in any other case.
   * {@code ..} may be used to jump to the parent directory.
   * Environment variables enclosed by {@code %} are expanded.
   */
  public static Path resolve(Path base, String s){
    try{
      if (s==null){ return base; }
      s = expandEnvironmentVariables(s).replace('\\','/');
      int len = s.length();
      if (len==0){ return base; }
      int i=0;
      if (s.charAt(0)=='/'){
        ++i;
      }else{
        base = null;
      }
      int j;
      String str;
      while (i<len){
        j = s.indexOf('/', i);
        if (j==-1){
          j = len;
        }else if (i==j){
          ++i;
          continue;
        }
        str = s.substring(i,j);
        if (base==null){
          if (!str.equals("..")){
            base = Paths.get(str);
          }
        }else{
          base = str.equals("..")?base.getParent():base.resolve(str);
        }
        i = j+1;
      }
      return base;
    }catch(Throwable t){
      Logger.logAsync("Error occurred while resolving Path.", t);
      return null;
    }
  }
  /**
   * Replaces matches of the regular expression {@code %.*?%} with the corresponding environment variable.
   */
  public static String expandEnvironmentVariables(String str){
    int len = str.length();
    StringBuilder out = new StringBuilder(len+16);
    StringBuilder var = new StringBuilder();
    String tmp;
    boolean env = false;
    char c;
    for (int i=0;i<len;++i){
      c = str.charAt(i);
      if (c=='%'){
        if (env){
          tmp = System.getenv(var.toString());
          if (tmp!=null){
            out.append(tmp);
            tmp = null;
          }
          var.setLength(0);
        }
        env^=true;
      }else if (env){
        var.append(c);
      }else{
        out.append(c);
      }
    }
    return out.toString();
  }
}