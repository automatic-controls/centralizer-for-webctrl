/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
/**
 * Used as a type parameter for {@code DelayQueue}'s.
 */
public abstract class DelayedRunnable implements Delayed, Runnable {
  private volatile long expiry;
  /**
   * Specifying an {@code expiry} of {@code 0} means the task should be executed immediately.
   * @param expiry is the target value of {@code System.currentTimeMillis()} when this task should be executed.
   */
  public DelayedRunnable(long expiry){
    this.expiry = expiry;
  }
  public long getExpiry(){
    return expiry;
  }
  public long getDelay(TimeUnit u){
    return expiry==0?0:expiry-System.currentTimeMillis();
  }
  public int compareTo(Delayed d){
    if (d==this){
      return 0;
    }
    if (d instanceof DelayedRunnable){
      DelayedRunnable dr = (DelayedRunnable)d;
      return expiry<dr.expiry?-1:(expiry==dr.expiry?0:1);
    }else{
      long d1 = getDelay(TimeUnit.MILLISECONDS);
      long d2 = d.getDelay(TimeUnit.MILLISECONDS);
      return d1<d2?-1:(d1==d2?0:1);
    }
  }
}