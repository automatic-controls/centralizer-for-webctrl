/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.nio.file.*;
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
  /** Used for deserialization. */
  private SyncTask(){}
  /**
   * Serializes this object into an array of bytes.
   */
  public byte[] serialize(){
    byte[] a = description.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] b = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] c = dst.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    SerializationStream s = new SerializationStream(a.length+b.length+c.length+24);
    s.write(ID);
    s.write(syncInterval);
    s.write(a);
    s.write(b);
    s.write(c);
    return s.data;
  }
  /**
   * @return a synchronization task deserialized from the given stream.
   */
  public static SyncTask deserialize(SerializationStream s){
    SyncTask t = new SyncTask();
    t.ID = s.readInt();
    t.setSyncInterval(s.readLong());
    t.description = s.readString();
    t.src = s.readString();
    t.dst = s.readString();
    return t;
  }
  /**
   * Sets the synchronization interval for this task.
   * Changes the next trigger time accordingly.
   */
  public synchronized void setSyncInterval(long syncInterval){
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