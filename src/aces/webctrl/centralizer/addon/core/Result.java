/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
import java.util.function.Consumer;
/**
 * Provides behavior comparable to {@code java.util.concurrent.Future<T>} and {@code java.nio.channels.CompletionHandler<T,Void>}.
 */
public class Result<T> {
  private volatile T result = null;
  private volatile boolean finished = false;
  private volatile Consumer<T> consumer = null;
  private volatile long timestamp = -1;
  /**
   * Convenience method which calls {@code onResult(consumer, true)}.
   */
  public void onResult(Consumer<T> consumer){
    onResult(consumer, true);
  }
  /**
   * Whenever the result is ready, the given consumer will be invoked.
   * If the result is ready at the time of this method's invokation, and {@code executeNow} is {@code true},
   * then the given consumer is immediately invoked.
   * The given consumer is guaranteed to be invoked at most one time.
   * If you want the consumer to be invoked multiple times on many results, then
   * you should use {@code onResult(this,false)} within the body of the consumer.
   */
  public synchronized void onResult(Consumer<T> consumer, boolean executeNow){
    if (consumer==null){
      this.consumer = null;
    }else if (executeNow && finished){
      consumer.accept(result);
    }else{
      this.consumer = consumer;
    }
  }
  /**
   * Sets {@code finished} to {@code false}, which means {@code waitForResult(-1)} will block until the next invokation of {@code setResult(T)}.
   */
  public synchronized void reset(){
    finished = false;
    result = null;
    timestamp = -1;
  }
  /**
   * Sets the result and invokes {@code notifyAll()}.
   */
  public synchronized void setResult(T result){
    this.result = result;
    timestamp = System.currentTimeMillis();
    finished = true;
    if (consumer!=null){
      Consumer<T> tmp = consumer;
      consumer = null;
      tmp.accept(result);
    }
    notifyAll();
  }
  /**
   * You should use {@code waitForResult(long)} before invoking this method.
   * @return the result of the asynchronous operation or {@code null} if the result is not ready.
   */
  public T getResult(){
    return result;
  }
  /**
   * @return whether the result has been set.
   */
  public boolean isFinished(){
    return finished;
  }
  /**
   * @return the value of {@code System.currentTimeMillis()} as recorded at the time of the last invokation of {@code setResult(T)}, or {@code -1} if no result has been set.
   */
  public long getTimestamp(){
    return timestamp;
  }
  /**
   * <ul>
   * <li>If {@code expiry>0}, this method blocks until either the result is ready or {@code System.currentTimeMillis()>=expiry}.</li>
   * <li>If {@code expiry==0}, this method returns immediately.</li>
   * <li>If {@code expiry<0}, this method blocks until the result is ready.</li>
   * </ul>
   * @param expiry is the time limit specified in milliseconds.
   * @return {@code true} if the result is ready; {@code false} if the result is not ready (and the timeout has expired).
   */
  public boolean waitForResult(long expiry) throws InterruptedException {
    if (expiry==0 || finished){
      return finished;
    }
    if (expiry<0){
      while (!finished){
        synchronized (this){
          wait(1000L);
        }
      }
    }else{
      long dif;
      while (!finished){
        dif = Math.min(expiry-System.currentTimeMillis(),1000L);
        if (dif<=0){
          break;
        }
        synchronized (this){
          wait(dif);
        }
      }
    }
    return finished;
  }
}