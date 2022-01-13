package aces.webctrl.centralizer.addon.core;
import java.util.function.Consumer;
/**
 * Provides behavior similar to {@code java.util.concurrent.Future<T>}.
 */
public class Result<T> {
  private volatile T result = null;
  private volatile boolean finished = false;
  private volatile Consumer<T> consumer = null;
  /**
   * Whenever the result is ready, the given consumer will be invoked.
   * If the result is ready at the time of this method's invokation,
   * then the given consumer is immediately invoked.
   */
  public synchronized void onResult(Consumer<T> consumer){
    this.consumer = consumer;
    if (finished && consumer!=null){
      consumer.accept(result);
    }
  }
  /**
   * Sets {@code finished} to {@code false}, which means {@code waitForResult(-1)} will block until the next invokation of {@code setResult(T)}.
   */
  public void reset(){
    finished = false;
  }
  /**
   * Sets the result and invokes {@code notifyAll()}.
   */
  public void setResult(T result){
    this.result = result;
    synchronized (this){
      finished = true;
      notifyAll();
      if (consumer!=null){
        consumer.accept(result);
      }
    }
  }
  /**
   * You should use {@code waitForResult(long)} before invoking this method.
   * @return the result of the asynchronous operation or {@code null} if the result is not ready.
   */
  public T getResult(){
    return result;
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