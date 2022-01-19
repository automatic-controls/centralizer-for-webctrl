package aces.webctrl.centralizer.common;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.locks.*;
/**
 * Thread-safe class encapsulating a single operator.
 */
public class Operator {
  /**
   * Specifies how many iterations to use for password hashing with {@link StreamCipher#hash(byte[],byte[],int)}.
   */
  private final static int hashIterations = 10000;
  /**
   * Specifices the length of password hashes.
   */
  private final static int hashLength = 16;
  /**
   * Unique identification number.
   * Also used as the index for storing this operator in the operator list.
   */
  private volatile int ID;
  /**
   * Case-insensitive username.
   */
  private volatile String username;
  /**
   * Lock which controls access to {@code salt}, and {@code hash}.
   */
  private final ReentrantReadWriteLock passwordLock = new ReentrantReadWriteLock();
  /**
   * Contains the salt used to hash this operator's password.
   */
  private volatile byte[] salt;
  /**
   * Contains the hash of this operator's password.
   */
  private volatile byte[] hash;
  /**
   * Specifies whether this operator has been flagged for garbage collection.
   * In particular, a value of {@code true} prevents this operator from modifying the filesystem.
   */
  private volatile boolean disposed = false;
  /**
   * User-friendly display name (e.g. John Doe) as opposed to the username (e.g. jdoe1978).
   */
  private volatile String displayName;
  /**
   * Number of seconds before automatic logoff.
   * Note this value is passed to clients and does not interfere with the Centralizer database operator timeout of {@code Config.operatorTimeout}.
   * According to WebCTRL API documentation, zero disables automatic logoff, and negative numbers indicate the system-wide timeout should be used (specific to each WebCTRL server).
   */
  private volatile int navigationTimeout;
  /**
   * Bit-mask of constants from the {@link Permissions} class.
   */
  private volatile int permissions;
  /**
   * Contains extra information about the operator.
   * For instance, job titles or contact information would be appropriate here.
   */
  private volatile String description;
  /**
   * Indicates the last time this operator was modified.
   */
  private volatile long timestamp;
  /**
   * Indicates the time of this operator's creation.
   */
  private volatile long creationTime;
  /**
   * Indicates the time of the last successful operator login.
   * {@code -1} implies the operator has never logged in.
   */
  private volatile long lastLogin = -1L;
  /**
   * Indicates whether this operator should be forced to change their password at login.
   */
  private volatile boolean forceChange = false;
  /**
   * Specifies when an operator lockout ends, or {@code -1} if no lockout has been incurred.
   */
  private volatile long lockout = -1;
  /**
   * Keeps track of failed login attempts for lockout purposes.
   */
  private volatile LinkedList<Long> failedAttempts = new LinkedList<Long>();
  /**
   * Used for {@link #deserialize(byte[])}.
   */
  private Operator(){}
  /**
   * Initialize an operator with the given parameters.
   * @param username the login name to be used for this operator.
   * @param password byte array representation of a password for this operator.
   * @param permissions bit-mask of constants from the {@link Permissions} class.
   * @param displayName user-friendly name for this operator.
   * @param navigationTimeout specifies how many seconds of inactivity it takes for a WebCTRL server to automatically logoff this operator.
   * @param description any additional data to be attached to this operator (e.g. contact information).
   */
  protected Operator(String username, byte[] password, int permissions, String displayName, int navigationTimeout, String description){
    creationTime = System.currentTimeMillis();
    this.username = username;
    setPassword(password);
    this.permissions = Permissions.validate(permissions);
    this.displayName = displayName;
    this.navigationTimeout = navigationTimeout;
    this.description = description;
  }
  /**
   * Sets the identification number for this operator.
   * Meant to be used immediately after an invokation of the previous constructor.
   */
  protected void setID(int ID){
    this.ID = ID;
    stamp();
  }
  /**
   * @return whether or not this operator is locked out.
   */
  public boolean isLockedOut(){
    if (lockout!=-1 && Database.isServer() && Config.loginAttempts>=0){
      if (System.currentTimeMillis()>lockout){
        lockout = -1;
        return false;
      }else{
        return true;
      }
    }
    return false;
  }
  /**
   * Unlocks this operator.
   */
  public void unlock(){
    lockout = -1;
    synchronized (failedAttempts){
      failedAttempts.clear();
    }
  }
  /**
   * Resets the modification timestamp to now.
   */
  private void stamp(){
    timestamp = System.currentTimeMillis();
  }
  /**
   * Returns the value of {@code System.currentTimeMillis()} as recorded at the time of last modification.
   */
  public long getStamp(){
    return timestamp;
  }
  /**
   * Returns the value of {@code System.currentTimeMillis()} as recorded at the time of creation.
   */
  public long getCreationTime(){
    return creationTime;
  }
  /**
   * Returns the value of {@code System.currentTimeMillis()} as recorded during this operator's last successful login.
   * {@code -1} implies this operator has never successfully logged in.
   */
  public long getLastLogin(){
    return lastLogin;
  }
  /**
   * Serializes an operator into a byte array.
   */
  public byte[] serialize(){
    byte[] usernameData = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] displayNameData = displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] descData = description.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    passwordLock.readLock().lock();
    SerializationStream s = new SerializationStream(usernameData.length+displayNameData.length+descData.length+salt.length+hash.length+57);
    s.write(salt);
    s.write(hash);
    passwordLock.readLock().unlock();
    s.write(ID);
    s.write(forceChange);
    s.write(creationTime);
    s.write(timestamp);
    s.write(lastLogin);
    s.write(permissions);
    s.write(navigationTimeout);
    s.write(usernameData);
    s.write(displayNameData);
    s.write(descData);
    return s.data;
  }
  /**
   * Deserializes an operator from a byte array.
   * @param data the byte array containing the information to be deserialized.
   * @throws IndexOutOfBoundsException if {@code data} has been corrupted.
   */
  public static Operator deserialize(byte[] data) throws IndexOutOfBoundsException {
    Operator op = new Operator();
    SerializationStream s = new SerializationStream(data);
    op.salt = s.readBytes();
    op.hash = s.readBytes();
    op.ID = s.readInt();
    op.forceChange = s.readBoolean();
    op.creationTime = s.readLong();
    op.timestamp = s.readLong();
    op.lastLogin = s.readLong();
    op.permissions = s.readInt();
    op.navigationTimeout = s.readInt();
    op.username = s.readString();
    op.displayName = s.readString();
    op.description = s.readString();
    if (!s.end()){
      throw new IndexOutOfBoundsException("Byte array is too large and cannot be deserialized into an Operator.");
    }
    return op;
  }
  /**
   * Saves information to the operator's data file.
   * Should only be invoked from {@link Operators#save()}.
   * @param folder where to put the operator's data file.
   * @return {@code true} on success; {@code false} on failure.
   */
  protected synchronized boolean save(Path folder){
    if (disposed){
      //Indicates this operator has been deleted
      return true;
    }
    String username = getUsername();
    try{
      Path dataFile = folder.resolve(username);
      ByteBuffer buf = ByteBuffer.wrap(serialize());
      try(
        FileChannel out = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        FileLock lock = out.tryLock();
      ){
        while (buf.hasRemaining()){
          out.write(buf);
        }
      }
      return true;
    }catch(Exception e){
      Logger.log("Error occurred while attempting to save operator data for "+username+'.', e);
      return false;
    }
  }
  /**
   * Prevents this operator from making any changes to the filesystem.
   * Blocks until methods currently modifying the filesystem terminate naturally.
   */
  protected synchronized void dispose(){
    disposed = true;
  }
  /**
   * @return whether or not this operator has been disposed.
   */
  public boolean isDisposed(){
    return disposed;
  }
  /**
   * Sets the password of this operator.
   * The password array will be cleared for security reasons.
   */
  public void setPassword(byte[] password){
    forceChange = false;
    passwordLock.writeLock().lock();
    salt = new byte[hashLength];
    Database.entropy.nextBytes(salt);
    hash = StreamCipher.hash(password, salt.clone(), hashIterations);
    passwordLock.writeLock().unlock();
    stamp();
  }
  /**
   * Checks whether the provided credentials are correct.
   * Increments the lockout counter if credentials are incorrect.
   * The password array will be cleared for security reasons.
   */
  public boolean checkCredentials(String username, byte[] password){
    if (lockout!=-1 && Database.isServer() && Config.loginAttempts>=0){
      if (System.currentTimeMillis()>lockout){
        lockout = -1;
      }else{
        java.util.Arrays.fill(password,(byte)0);
        return false;
      }
    }
    boolean ret;
    if (getUsername().equalsIgnoreCase(username)){
      passwordLock.readLock().lock();
      ret = java.util.Arrays.equals(hash, StreamCipher.hash(password, salt.clone(), hashIterations));
      passwordLock.readLock().unlock();
    }else{
      java.util.Arrays.fill(password,(byte)0);
      ret = false;
    }
    if (ret){
      unlock();
      lastLogin = System.currentTimeMillis();
    }else if (Database.isServer() && Config.loginAttempts>=0){
      synchronized (failedAttempts){
        long time = System.currentTimeMillis();
        long t = time-Config.loginTimePeriod;
        Iterator<Long> iter = failedAttempts.iterator();
        while (iter.hasNext()){
          if (iter.next()<t){
            iter.remove();
          }
        }
        failedAttempts.add(time);
        if (failedAttempts.size()>Config.loginAttempts){
          failedAttempts.clear();
          lockout = time+Config.loginLockoutTime;
        }
      }
    }
    return ret;
  }
  /**
   * Sets the description of this operator.
   */
  public void setDescription(String description){
    this.description = description;
    stamp();
  }
  /**
   * @return the description of this operator.
   */
  public String getDescription(){
    return description;
  }
  /**
   * Sets the username of this operator.
   * @return {@code true} if the username was changed successfully; {@code false} if the new username failed validation.
   */
  public boolean setUsername(String username){
    username = username.toLowerCase();
    if (Database.validateName(username,false)){
      this.username = username;
      stamp();
      return true;
    }else{
      return false;
    }
  }
  /**
   * @return the username of this operator.
   */
  public String getUsername(){
    return username;
  }
  /**
   * Sets the display name of this operator.
   */
  public void setDisplayName(String displayName){
    this.displayName = displayName;
    stamp();
  }
  /**
   * @return the display name of this operator.
   */
  public String getDisplayName(){
    return displayName;
  }
  /**
   * Sets the {@link Permissions} bit-mask constant for this operator.
   */
  public void setPermissions(int permissions){
    this.permissions = Permissions.validate(permissions);
    stamp();
  }
  /**
   * @return a bit-mask of constants from {@link Permissions} which specifies the privileges of this operator.
   */
  public int getPermissions(){
    return permissions;
  }
  /**
   * Sets the navigation timeout of this operator.
   */
  public void setNavigationTimeout(int navigationTimeout){
    this.navigationTimeout = navigationTimeout;
    stamp();
  }
  /**
   * @return the navigation timeout for this operator.
   */
  public int getNavigationTimeout(){
    return navigationTimeout;
  }
  /**
   * @return the {@code ID} for this operator.
   */
  public int getID(){
    return ID;
  }
  /**
   * @return whether this operator should be forced to change his or her password at next login.
   */
  public boolean changePassword(){
    return forceChange;
  }
  /**
   * Sets whether this operator should be forced to change his or her password at next login.
   */
  public void changePassword(boolean b){
    forceChange = b;
  }
}