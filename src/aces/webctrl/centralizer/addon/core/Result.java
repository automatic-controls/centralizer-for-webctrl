package aces.webctrl.centralizer.addon.core;
/**
 * Provides behavior similar to {@code java.util.concurrent.Future<T>}.
 */
public class Result<T> {
  private volatile T result = null;
  private volatile boolean finished = false;
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
    finished = true;
    synchronized (this){
      notifyAll();
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