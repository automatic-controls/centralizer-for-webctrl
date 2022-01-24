/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.Logger;
import java.nio.channels.*;
public abstract class Handler<T> implements CompletionHandler<T,Void> {
  /**
   * @return {@code true} indicates that another {@code Handler} has been asynchronously initiated.
   *         You should also return {@code true} when {@code Initializer.forceDisconnect()} is invoked.
   *         {@code false} indicates that no other {@code Handler} has been asynchronously initiated.
   */
  public abstract boolean onSuccess(T ret);
  @Override public void completed(T ret, Void v){
    try{
      if (!onSuccess(ret)){
        Initializer.taskExecuting = false;
      }
    }catch(Exception e){
      Logger.log("Error occurred in Handler.", e);
    }
  }
  @Override public void failed(Throwable e, Void v){
    Initializer.disconnect(e);
  }
}