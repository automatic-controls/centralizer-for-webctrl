package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.*;
/**
 * Used to differentiate between {@code DelayedRunnable}'s which initiate asynchronous communications to the central database and those that do not.
 */
public abstract class Task extends DelayedRunnable {
  public SocketWrapper wrapper;
  private boolean valid = true;
  /**
   * Specifying an {@code expiry} of {@code 0} means the task should be executed immediately.
   * @param expiry is the target value of {@code System.currentTimeMillis()} when this task should be executed.
   */
  public Task(SocketWrapper wrapper, long expiry){
    super(expiry);
    this.wrapper = wrapper;
  }
  /**
   * @return whether or not this task has been cancelled.
   */
  public boolean isValid(){
    return valid;
  }
  /**
   * Cancels this task.
   */
  public void cancel(){
    valid = false;
  }
}