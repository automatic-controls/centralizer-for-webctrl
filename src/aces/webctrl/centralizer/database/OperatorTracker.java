package aces.webctrl.centralizer.database;
import aces.webctrl.centralizer.common.*;
public class OperatorTracker {
  /** The operator being tracked. */
  private volatile Operator op;
  /** Controls when this tracker expires. */
  private volatile long expiry;
  /**
   * Constructs a new tracker.
   */
  public OperatorTracker(Operator op){
    this.op = op;
    reset();
  }
  /**
   * Copy constructor.
   */
  public OperatorTracker(OperatorTracker t){
    op = t.op;
    expiry = t.expiry;
  }
  /**
   * Resets the expiry timeout.
   */
  public void reset(){
    expiry = System.currentTimeMillis()+Config.operatorTimeout;
  }
  /**
   * @return the operator being tracked.
   */
  public Operator getOperator(){
    return op;
  }
  /**
   * Determines whether this tracker should be removed from the tracker list.
   * @return {@code true} if the timeout has not expired and the tracked operator has not been removed from the operator list; {@code false} otherwise.
   */
  public synchronized boolean validate(){
    return op!=null && System.currentTimeMillis()<expiry && !op.isDisposed();
  }
}