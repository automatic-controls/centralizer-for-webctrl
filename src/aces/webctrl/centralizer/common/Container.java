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