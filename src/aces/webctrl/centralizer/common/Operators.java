package aces.webctrl.centralizer.common;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
/**
 * Thread-safe namespace which encapsulates all operators.
 * Invoke {@link #init(Path)} before calling any other method.
 */
public class Operators {
  /** Path to the folder containing operator data files. */
  private volatile static Path p;
  /** List of operators indexed by {@code ID}. */
  private volatile static ArrayList<Operator> operators = null;
  /** Lock which controls access to {@code operators}. */
  private volatile static ReentrantReadWriteLock lock;
  /** Keeps track of how many operators exist. */
  private volatile static AtomicInteger count;
  /**
   * @return how many operators exist.
   */
  public static int count(){
    return count.get();
  }
  /**
   * Initializes parameters.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  protected static boolean init(Path operatorFolder){
    lock = new ReentrantReadWriteLock();
    count = new AtomicInteger();
    p = operatorFolder;
    try{
      if (Files.exists(p)){
        return load();
      }else{
        operators = new ArrayList<Operator>();
        Files.createDirectory(p);
        return true;
      }
    }catch(Exception e){
      Logger.log("Error occurred while creating operators directory.", e);
      return false;
    }
  }
  /**
   * Loads all operator files.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  private static boolean load(){
    try(
      DirectoryStream<Path> stream = Files.newDirectoryStream(p);
    ){
      boolean ret = true;
      ArrayList<Operator> ops = new ArrayList<Operator>();
      int size = 0;
      for (Path entry:stream){
        try{
          Operator op = Operator.deserialize(Files.readAllBytes(entry));
          ops.add(op);
          size = Math.max(size, op.getID());
        }catch(Exception e){
          ret = false;
          Logger.log("Error occurred while loading operator from data file: "+entry.toString(), e);
        }
      }
      size+=16;
      operators = new ArrayList<Operator>(size);
      for (int i=0;i<size;++i){
        operators.add(null);
      }
      for (Operator op:ops){
        int ID = op.getID();
        if (ID>=0 && operators.get(ID)==null){
          operators.set(ID,op);
          count.incrementAndGet();
        }else{
          ret = false;
          if (ID<0){
            Logger.log("Invalid operator ID detected: "+ID+'.');
          }else{
            Logger.log("Duplicate operator ID detected: "+ID+'.');
          }
        }
      }
      return ret;
    }catch(Exception e){
      Logger.log("Error occurred while loading operators.", e);
      return false;
    }
  }
  /**
   * Retrieves an operator with the specified ID.
   * @param ID
   * @return the retrieved operator if it exists; otherwise {@code null}.
   */
  public static Operator get(int ID){
    lock.readLock().lock();
    try{
      return ID<0 || ID>=operators.size() ? null : operators.get(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Retrieves an operator with the specified username if it exists.
   * @param username
   * @return the retrieved operator if it exists; otherwise {@code null}.
   */
  public static Operator get(String username){
    lock.readLock().lock();
    try{
      for (Operator op:operators){
        if (op!=null && op.getUsername().equalsIgnoreCase(username)){
          return op;
        }
      }
      return null;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Constructs and appends an operator to the operator list if the specified username is not already taken.
   * Used by the database to create operators using information received from clients.
   * @param username the login name to be used for this operator.
   * @param password byte array representation of a password for this operator.
   * @param permissions bit-mask of constants from the {@link Permissions} class.
   * @param displayName user-friendly name for this operator.
   * @param navigationTimeout specifies how many seconds of inactivity it takes for a WebCTRL server to automatically logoff this operator.
   * @param description any additional data to be attached to this operator (e.g. contact information).
   * @return the constructed {@code Operator} under most circumstances; {@code null} if the given username is already taken or if any other error occurs.
   */
  public static Operator add(String username, byte[] password, int permissions, String displayName, int navigationTimeout, String description){
    username = username.toLowerCase();
    if (!Database.validateName(username, false) || !Database.validatePassword(password)){
      Arrays.fill(password,(byte)0);
      return null;
    }
    Operator newOp = new Operator(username, password, permissions, displayName, navigationTimeout, description);
    lock.writeLock().lock();
    try{
      for (Operator op:operators){
        if (op!=null && op.getUsername().equals(username)){
          return null;
        }
      }
      int len = operators.size();
      int ID = len;
      for (int i=0;i<len;++i){
        if (operators.get(i)==null){
          ID = i;
          break;
        }
      }
      newOp.setID(ID);
      if (ID==len){
        operators.add(newOp);
      }else{
        operators.set(ID,newOp);
      }
      count.incrementAndGet();
      return newOp;
    }catch(Exception e){
      Logger.logAsync("Error occurred while creating an operator with username "+username+'.', e);
      return null;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Updates an operator.
   * Used by clients to add/update deserialized operators retrieved from the database.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean update(Operator op){
    if (op==null){
      return false;
    }
    int ID = op.getID();
    if (ID<0){
      return false;
    }
    lock.writeLock().lock();
    try{
      operators.ensureCapacity(ID+1);
      while (operators.size()<=ID){
        operators.add(null);
      }
      Operator prev = operators.get(ID);
      if (prev==null){
        count.incrementAndGet();
      }else{
        prev.dispose();
      }
      operators.set(ID,op);
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occurred while updating operator: "+op.getUsername()+'.', e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Clears all operators.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean clear(){
    lock.writeLock().lock();
    try{
      count.set(0);
      Operator op;
      int s = operators.size();
      for (int i=0;i<s;++i){
        op = operators.get(i);
        if (op!=null){
          op.dispose();
          operators.set(i,null);
        }
      }
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occurred while clearing operators.", e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Removes an operator from the operator list.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public static boolean remove(Operator op){
    if (op==null){
      return true;
    }
    op.dispose();
    int ID = op.getID();
    if (ID<0){
      return true;
    }
    lock.writeLock().lock();
    try{
      if (ID>=operators.size()){
        return true;
      }
      if (operators.set(ID,null)!=null){
        count.decrementAndGet();
      }
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occurred while removing an operator with ID="+ID+'.', e);
      return false;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Saves all operator files.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public synchronized static boolean save(){
    try{
      lock.readLock().lock();
      @SuppressWarnings("unchecked")
      ArrayList<Operator> ops = (ArrayList<Operator>)operators.clone();
      lock.readLock().unlock();
      if (Files.exists(p)){
        try(
          DirectoryStream<Path> stream = Files.newDirectoryStream(p);
        ){
          for (Path entry:stream){
            Files.delete(entry);
          }
        }catch(Exception e){
          Logger.log("Error occurred while clearing operator data.", e);
        }
      }else{
        Files.createDirectory(p);
      }
      boolean ret = true;
      for (Operator op:ops){
        if (op!=null){
          ret&=op.save(p);
        }
      }
      return ret;
    }catch(Exception e){
      Logger.log("Error occurred while saving operators.", e);
      return false;
    }
  }
  /**
   * Applies the given predicate to each operator in the operator list.
   * The predicate's return value indicates whether to continue iterating over the operator list.
   * <p>
   * WARNING - The predicate should not invoke methods of the {@code Operators} class which structurally modify the operator list (otherwise this method will block indefinitely).
   * More precisely, the predicate must not make any attempt to acquire a write lock on the operator list.
   * This method acquires a read lock to perform iteration, and read locks cannot be promoted to write locks.
   * @return {@code true} on success; {@code false} if some predicate returned {@code false} or if any other error occurred.
   */
  public static boolean forEach(Predicate<Operator> func){
    lock.readLock().lock();
    try{
      for (Operator op:operators){
        if (op!=null && !func.test(op)){
          return false;
        }
      }
      return true;
    }catch(Exception e){
      Logger.logAsync("Error occured while iterating over the operator list.", e);
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
}