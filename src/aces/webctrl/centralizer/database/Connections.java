/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.database;
import aces.webctrl.centralizer.common.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.locks.*;
import java.nio.channels.*;
public class Connections {
  private final static TreeSet<Connection> connections = new TreeSet<Connection>();
  private final static ReentrantReadWriteLock conLock = new ReentrantReadWriteLock();
  /**
   * Appends and initializes a connection.
   */
  public static void add(AsynchronousSocketChannel ch){
    Connection con = new Connection(ch);
    conLock.writeLock().lock();
    try{
      connections.add(con);
    }finally{
      conLock.writeLock().unlock();
    }
    con.init();
  }
  /**
   * Removes a connection from the connection list.
   */
  public static boolean remove(Connection con){
    conLock.writeLock().lock();
    try{
      return connections.remove(con);
    }finally{
      conLock.writeLock().unlock();
    }
  }
  /**
   * Closes all connections.
   * @return {@code true} if everything closes successfully; {@code false} if any error is encountered.
   */
  public static boolean close(){
    Logger.log("Closing all connections...");
    conLock.writeLock().lock();
    try{
      boolean ret = true;
      for (Connection con:connections){
        ret&=con.close(false);
      }
      connections.clear();
      return ret;
    }finally{
      conLock.writeLock().unlock();
    }
  }
  /**
   * Applies the given predicate to each connection.
   * The predicate's return value indicates whether to continue iterating over the connection list.
   * <p>
   * WARNING - The predicate should not invoke methods of the {@code Connections} class which structurally modify the connection list (otherwise this method will block indefinitely).
   * More precisely, the predicate must not make any attempt to acquire a write lock on the connection list.
   * This method acquires a read lock to perform iteration, and read locks cannot be promoted to write locks.
   * @return {@code true} on success; {@code false} if some predicate returned {@code false} or if any other error occurred.
   */
  public static boolean forEach(Predicate<Connection> func){
    conLock.readLock().lock();
    try{
      for (Connection con:connections){
        if (con!=null && !func.test(con)){
          return false;
        }
      }
      return true;
    }catch(Throwable e){
      Logger.logAsync("Error occured while iterating over the connection list.", e);
      return false;
    }finally{
      conLock.readLock().unlock();
    }
  }
}