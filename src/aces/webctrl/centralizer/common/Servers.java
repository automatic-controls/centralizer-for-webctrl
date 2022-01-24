/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
/**
 * Thread-safe namespace which encapsulates information specific to each WebCTRL server.
 */
public class Servers {
  /** Path to the folder containing server data folders. */
  private volatile static Path p;
  /** List of servers indexed by {@code ID}. */
  private volatile static ArrayList<Server> servers = null;
  /** Lock which controls access to {@code servers}. */
  private volatile static ReentrantReadWriteLock lock;
  /** Keeps track of what ID should be given to the next newly created server. */
  private volatile static AtomicInteger nextID = new AtomicInteger();
  /**
   * Initializes parameters.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  protected static boolean init(Path serverFolder){
    lock = new ReentrantReadWriteLock();
    servers = new ArrayList<Server>();
    p = serverFolder;
    try{
      if (Files.exists(p)){
        return load();
      }else{
        Files.createDirectory(p);
        return true;
      }
    }catch(Exception e){
      Logger.log("Error occurred while creating servers directory.", e);
      return false;
    }
  }
  /**
   * Retrieves a server with the specified ID.
   * @param ID
   * @return the retrieved server if it exists; otherwise {@code null}.
   */
  public static Server get(int ID){
    lock.readLock().lock();
    try{
      return ID<0 || ID>=servers.size() ? null : servers.get(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Constructs and appends a server to the server list if the specified name is not already taken.
   * Used by the database to create new servers from information retrieved from clients.
   * @param ipAddress the address of the server to create.
   * @param name a user-friendly name for the server.
   * @param description contains descriptive text for the server.
   * @return the constructed {@code Server} under most circumstances; {@code null} if the given name is already taken by another server or if any other error occurs.
   */
  public static Server add(String ipAddress, String name, String description){
    if (!Database.validateName(name, true)){
      return null;
    }
    int ID = nextID.getAndIncrement();
    Server newServer = new Server(ID, ipAddress, name, description);
    lock.writeLock().lock();
    try{
      for (Server s:servers){
        if (s!=null && s.getName().equalsIgnoreCase(name)){
          return null;
        }
      }
      if (ID==servers.size()){
        servers.add(newServer);
      }else{
        servers.ensureCapacity(ID+1);
        while (servers.size()<=ID){
          servers.add(null);
        }
        servers.set(ID,newServer);
      }
      return newServer;
    }catch(Exception e){
      Logger.logAsync("Error occurred while creating an server with name "+name+'.', e);
      return null;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Updates a server.
   * Used to create/update deserialized servers retrieved from the database.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean update(Server s){
    if (s==null){
      return false;
    }
    final int ID = s.getID();
    if (ID<0){
      return false;
    }
    nextID.updateAndGet(new IntUnaryOperator(){
      public int applyAsInt(int x){
        if (x<=ID){
          return ID+1;
        }else{
          return x;
        }
      }
    });
    lock.writeLock().lock();
    try{
      servers.ensureCapacity(ID+1);
      while (servers.size()<=ID){
        servers.add(null);
      }
      Server prev = servers.get(ID);
      if (prev!=null){
        prev.dispose();
      }
      servers.set(ID,s);
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occurred while updating server: "+s.getName()+'.', e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Removes a server from the server list.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean remove(Server s){
    if (s==null){
      return true;
    }
    s.dispose();
    int ID = s.getID();
    if (ID<0){
      return true;
    }
    lock.writeLock().lock();
    try{
      if (ID>=servers.size()){
        return true;
      }
      servers.set(ID,null);
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occurred while removing an server with ID="+ID+'.', e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Loads all server files.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  private static boolean load(){
    try(
      DirectoryStream<Path> stream = Files.newDirectoryStream(p);
    ){
      boolean ret = true;
      ArrayList<Server> arr = new ArrayList<Server>();
      int size = -1;
      for (Path entry:stream){
        try{
          Server s = Server.deserialize(Files.readAllBytes(entry.resolve("data")), true);
          arr.add(s);
          size = Math.max(size, s.getID());
        }catch(Exception e){
          ret = false;
          Logger.log("Error occurred while loading server data: "+entry.toString(), e);
        }
      }
      nextID.set(size+1);
      size+=16;
      servers = new ArrayList<Server>(size);
      for (int i=0;i<size;++i){
        servers.add(null);
      }
      for (Server s:arr){
        int ID = s.getID();
        if (ID>=0 && servers.get(ID)==null){
          servers.set(ID,s);
        }else{
          ret = false;
          if (ID<0){
            Logger.log("Invalid server ID detected: "+ID+'.');
          }else{
            Logger.log("Duplicate server ID detected: "+ID+'.');
          }
        }
      }
      return ret;
    }catch(Exception e){
      Logger.log("Error occurred while loading servers.", e);
      return false;
    }
  }
  /**
   * Saves all server files.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public synchronized static boolean save(){
    try{
      lock.readLock().lock();
      @SuppressWarnings("unchecked")
      ArrayList<Server> arr = (ArrayList<Server>)servers.clone();
      lock.readLock().unlock();
      if (Files.exists(p)){
        try{
          Files.walkFileTree(p, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
              if (e==null){
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
              }else{
                throw e;
              }
            }
          });
        }catch(Exception e){
          Logger.log("Error occurred while clearing server data.", e);
        }
      }
      Files.createDirectory(p);
      boolean ret = true;
      for (Server s:arr){
        if (s!=null){
          ret&=s.save(p);
        }
      }
      return ret;
    }catch(Exception e){
      Logger.log("Error occurred while saving servers.", e);
      return false;
    }
  }
  /**
   * Applies the given predicate to each server in the server list.
   * The predicate's return value indicates whether to continue iterating over the server list.
   * <p>
   * WARNING - The predicate should not invoke methods of the {@code Servers} class which structurally modify the server list (otherwise this method will block indefinitely).
   * More precisely, the predicate must not make any attempt to acquire a write lock on the server list.
   * This method acquires a read lock to perform iteration, and read locks cannot be promoted to write locks.
   * @return {@code true} on success; {@code false} if some predicate returned {@code false} or if any other error occurred.
   */
  public static boolean forEach(Predicate<Server> func){
    lock.readLock().lock();
    try{
      for (Server s:servers){
        if (s!=null && !func.test(s)){
          return false;
        }
      }
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occured while iterating over the server list.", e);
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
}