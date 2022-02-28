package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.*;
import org.springframework.scheduling.support.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
/**
 * Thread-safe class which encapsulates a regularly scheduled file-upload to the database.
 */
public class Upload implements Comparable<Upload> {
  private final static AtomicInteger nextID = new AtomicInteger(0);
  private volatile int ID = nextID.getAndIncrement();
  private volatile String src;
  private volatile String dst;
  private volatile String expr;
  private volatile Path srcPath;
  private volatile CronSequenceGenerator cron;
  private volatile long nextRunTime;
  /**
   * Compares internal identification numbers for sorting purposes.
   */
  public int compareTo(Upload x){
    return ID-x.ID;
  }
  /**
   * @return the identification number for this upload task.
   */
  public int getID(){
    return ID;
  }
  /**
   * Construct a new upload task with the given parameters.
   * @param src is the source file to upload.
   * @param dst is the desination path on the database.
   * @param expr is a cron expression which specifies the scheduling interval.
   */
  public Upload(String src, String dst, String expr){
    setSource(src);
    setDestination(dst);
    setExpression(expr);
    reset();
  }
  /**
   * Initiates and resets this upload task if it is ready according to the scheduling cron expression.
   * @return a {@code Result<Byte>} encapsulating the result of this operation if it was ready to be triggered; {@code null} if the operation is not ready to be triggered.
   */
  public synchronized Result<Byte> trigger(){
    try{
      final CronSequenceGenerator cron = this.cron;
      final Path srcPath = this.srcPath;
      final long nextRunTime = this.nextRunTime;
      if (cron!=null && srcPath!=null && nextRunTime!=-1 && nextRunTime<=System.currentTimeMillis() && Files.exists(srcPath)){
        reset();
        return Initializer.uploadFile(srcPath, dst);
      }
      return null;
    }catch(Throwable t){
      Result<Byte> ret = new Result<Byte>();
      ret.setResult(Protocol.FAILURE);
      return ret;
    }
  }
  /**
   * @return the next run time of this upload task. If {@code -1}, then this task should not ever execute.
   */
  public long getNext(){
    return nextRunTime;
  }
  /**
   * Resets the next run time of this upload task.
   */
  public void reset(){
    CronSequenceGenerator cron = this.cron;
    if (cron==null){
      nextRunTime = -1;
    }else{
      try{
        nextRunTime = cron.next(new Date()).getTime();
      }catch(Throwable t){
        nextRunTime = -1;
      }
    }
  }
  /**
   * @return a deserialized upload task.
   */
  public static Upload deserialize(SerializationStream s){
    return new Upload(s.readString(), s.readString(), s.readString());
  }
  /**
   * @return a {@code byte} array containing serialized data for this upload task.
   */
  public byte[] serialize(){
    byte[] a = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] b = dst.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] c = expr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    SerializationStream s = new SerializationStream(a.length+b.length+c.length+12);
    s.write(a);
    s.write(b);
    s.write(c);
    return s.data;
  }
  /**
   * @return the destination path on the database for this upload task.
   */
  public String getDestination(){
    return dst;
  }
  /**
   * @return the source file path for this upload task. May be {@code null}.
   */
  public Path getSource(){
    return srcPath;
  }
  /**
   * @return the cron expression which controls scheduling for this upload task.
   */
  public String getExpression(){
    return expr;
  }
  /**
   * Sets the destination path on the database for this upload task.
   */
  public void setDestination(String dst){
    this.dst = dst;
  }
  /**
   * Sets the source path for this upload task.
   */
  public void setSource(String src){
    this.src = src;
    srcPath = SyncTask.resolve(null, src);
  }
  /**
   * Sets the source path for this upload task.
   */
  public void setSource(Path src){
    this.src = src.toString();
    srcPath = src;
  }
  /**
   * Sets the cron expression used for scheduling purposes.
   * @return {@code true} on success; {@code false} if the given expression cannot be parsed.
   */
  public boolean setExpression(String expr){
    this.expr = expr;
    try{
      cron = new CronSequenceGenerator(expr);
      return true;
    }catch(Throwable t){
      cron = null;
      return false;
    }finally{
      reset();
    }
  }
}