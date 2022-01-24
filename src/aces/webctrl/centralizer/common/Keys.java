/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.security.*;
/**
 * Thread-safe namespace which encapsulates all RSA key-pairs used for database authentication.
 */
public class Keys {
  /** Global instance of {@code KeyFactory}. I am unsure if this object is thread-safe, so all access is synchronized (see {@code Key.deserialize}). */
  public static volatile KeyFactory keyFactory;
  /** Global instance of {@code KeyPairGenerator}. I am unsure if this object is thread-safe, so all access is synchronized (see {@code Key}'s constructor). */
  public static volatile KeyPairGenerator keyPairGen;
  /** Where to store key data. */
  protected static volatile Path keyFile;
  /** The ID of the most recently generated key. */
  private static volatile AtomicInteger newestKey;
  /** Lock which controls access to {@code keys} */
  private static volatile ReentrantReadWriteLock lock;
  /** List of all keys. */
  private static volatile ArrayList<Key> keys;
  /**
   * Generates and appends a new key to the key list.
   * @return the newly generated key, or {@code null} if an error occurs.
   */
  public static Key generateNewKey(){
    lock.writeLock().lock();
    try{
      int ID = newestKey.incrementAndGet();
      Key k = new Key(ID);
      keys.ensureCapacity(ID+1);
      while (keys.size()<=ID){
        keys.add(null);
      }
      keys.set(ID,k);
      return k;
    }catch(Throwable e){
      Logger.logAsync("Error occurred while generating new key.", e);
      return null;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * @return the preferred key to use for all database authentication handshakes.
   */
  public static Key getPreferredKey(){
    lock.readLock().lock();
    try{
      return keys.get(newestKey.get());
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * @return the key which has the given {@code ID}, or {@code null} if no such key exists.
   */
  public static Key get(int ID){
    lock.readLock().lock();
    try{
      return ID<0 || ID>=keys.size() ? null : keys.get(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Initializes parameters.
   * Invoked once at the start of the application.
   */
  protected static boolean init(Path keyFile){
    Keys.keyFile = keyFile;
    newestKey = new AtomicInteger(-1);
    keys = new ArrayList<Key>();
    lock = new ReentrantReadWriteLock();
    try{
      initCrypto(4096);
      boolean ret = true;
      if (Files.exists(keyFile)){
        ret&=load();
      }
      if (keys.size()==0){
        keys.add(new Key(0));
        newestKey.set(0);
      }
      return ret;
    }catch(Throwable e){
      Logger.log("Error occurred while initializing key-pairs used for database authentication.", e);
      return false;
    }
  }
  /**
   * Initializes the global {@code KeyFactory} and {@code KeyPairGenerator} instances for this application.
   */
  protected static void initCrypto(int keySize) throws Throwable {
    keyFactory = KeyFactory.getInstance("RSA");
    keyPairGen = KeyPairGenerator.getInstance("RSA");
    keyPairGen.initialize(keySize, Database.entropy);
  }
  /**
   * Loads all key data from the filesystem.
   * Invoked once at the start of the application.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  private static boolean load(){
    try{
      SerializationStream s = new SerializationStream(Files.readAllBytes(keyFile));
      int newestKey = -1;
      int ID;
      Key k;
      while (!s.end()){
        k = Key.deserialize(s,true);
        ID = k.getID();
        if (ID<0){
          Logger.log("Invalid key ID detected: "+ID+'.');
          continue;
        }
        newestKey = Math.max(newestKey, ID);
        keys.ensureCapacity(ID+1);
        while (keys.size()<=ID){
          keys.add(null);
        }
        if (keys.set(ID,k)!=null){
          Logger.log("Duplicate key ID detected: "+ID+'.');
        }
      }
      Keys.newestKey.set(newestKey);
      return true;
    }catch(Throwable e){
      keys.clear();
      Logger.log("Error occurred while loading key-pairs used for database authentication.", e);
      return false;
    }
  }
  /**
   * Saves all keys to the filesystem.
   * @return {@code true} on success; {@code false} if an error occurs.
   */
  public synchronized static boolean save(){
    try{
      SerializationStream s;
      lock.readLock().lock();
      try{
        int len = 0;
        for (Key k:keys){
          if (k!=null){
            len+=k.length(true);
          }
        }
        s = new SerializationStream(len);
        for (Key k:keys){
          if (k!=null){
            k.serialize(s,true);
          }
        }
      }finally{
        lock.readLock().unlock();
      }
      ByteBuffer buf = ByteBuffer.wrap(s.data);
      try(
        FileChannel out = FileChannel.open(keyFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        FileLock lock = out.tryLock();
      ){
        while (buf.hasRemaining()){
          out.write(buf);
        }
      }
      return true;
    }catch(Throwable e){
      Logger.log("Error occurred while saving database authentication key-pair data.", e);
      return false;
    }
  }
}