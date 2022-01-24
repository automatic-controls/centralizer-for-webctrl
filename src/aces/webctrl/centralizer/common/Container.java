/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
/**
 * Intended for thread-safe encapsulation of another object.
 */
public class Container<T> {
  public volatile T x = null;
  public Container(){}
  public Container(T x){
    this.x = x;
  }
}