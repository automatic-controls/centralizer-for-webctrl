/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
/**
 * This class is designed for continuous encryption/decryption operations on a stream.
 * The symmetric key changes with each operation depending on the data being processed.
 * Since a single corrupted bit can make two {@code StreamCipher} instances incompatible,
 * the {@link #hash(int)} or {@link #hashCode()} values should be compared after each transmission. This behavior provides a way to verify data integrity.
 * The {@link #mark()} and {@link #reset()} methods may be used to revert to an earlier internal state should data corruption occur.
 * <p>Usage Example:
 * <pre>{@code
 * byte[] key = ...;
 *byte[] data = ...;
 *StreamCipher a = new StreamCipher(key);
 *StreamCipher b = new StreamCipher(key);
 *assert java.util.Arrays.equals(data, b.decrypt(a.encrypt(data)));
 *assert a.hashCode()==b.hashCode();
 * }</pre>
 */
public class StreamCipher {
  /** The symmetric key used for encryption and decryption. */
  private volatile byte[] key = null;
  /** Stores the result of XORing all the bytes of {@link #key} together. */
  private volatile byte keyXOR;
  /** Copy of {@link #key} stored and retrieved using {@link #mark()} and {@link #reset()}. */
  private volatile byte[] lastKey = null;
  /** Copy of {@link #keyXOR} stored and retrieved using {@link #mark()} and {@link #reset()}. */
  private volatile byte lastKeyXOR;
  /** Whether to take extra steps for improved security. There is a possible speed trade-off. */
  private volatile boolean extra = true;
  /**
   * @return whether to take extra steps for improved security. There is a possible speed trade-off.
   */
  public boolean useExtraSteps(){
    return extra;
  }
  /** Set whether to take extra steps for improved security. There is a possible speed trade-off. */
  public void useExtraSteps(boolean extra){
    this.extra = extra;
  }
  /**
   * Creates a new {@code StreamCipher} with the given symmetric key.
   * 128 bit keys should be sufficient ({@code key.length==16}).
   * Parameter contents will be modified.
   * @param key is the symmetric key used for initialization.
   */
  public StreamCipher(byte[] key){
    this.key = key;
    nextKey();
  }
  /**
   * Creates a copy of the internal state of the cipher which may be retrieved by using {@link #reset()}.
   */
  public void mark(){
    lastKey = key.clone();
    lastKeyXOR = keyXOR;
  }
  /**
   * Resets the interval state of the cipher to the last {@link #mark()} point.
   */
  public void reset(){
    if (lastKey!=null){
      key = lastKey;
      keyXOR = lastKeyXOR;
    }
  }
  /**
   * Encrypts a single byte.
   * @param b is the byte to encrypt.
   * @return the encrypted byte.
   */
  public byte encrypt(byte b){
    b^=keyXOR;
    keyXOR = 0;
    for (int i=0;i<key.length;++i){
      b+=key[i];
      key[i]^=b;
      keyXOR^=key[i];
    }
    if (extra){
      nextKey();
    }
    return b;
  }
  /**
   * Decrypts a single byte.
   * @param b is the byte to decrypt.
   * @return the decrypted byte.
   */
  public byte decrypt(byte b){
    byte tmp;
    byte tmp2 = keyXOR;
    keyXOR = 0;
    for (int i=key.length-1;i>=0;--i){
      tmp = b;
      b-=key[i];
      key[i]^=tmp;
      keyXOR^=key[i];
    }
    b^=tmp2;
    if (extra){
      nextKey();
    }
    return b;
  }
  /**
   * Use the current key to deterministically generate a new key of the same length
   */
  public void nextKey(){
    int i,j,k;
    byte b = -102;
    for (i=0;i<key.length;++i){
      key[i]^=b;
      b+=key[i];
    }
    for (k=0;k<2;++k){
      for (i=0;i<key.length;++i){
        j = b%key.length;
        if (j<0){
          j+=key.length;
        }
        b+=key[j];
        key[i]^=b;
      }
    }
    keyXOR = 0;
    for (i=0;i<key.length;++i){
      keyXOR^=key[i];
    }
  }
  /**
   * Encrypts a block of data.
   * @param data is the byte array to encrypt.
   */
  public void encrypt(byte[] data){
    encrypt(data,0,data.length);
  }
  /**
   * Encrypts a block of data from {@code start} (inclusive) to {@code end} (exclusive).
   * @param data is the byte array to encrypt.
   */
  public void encrypt(byte[] data, int start, int end){
    for (int i=start;i<end;++i){
      data[i] = encrypt(data[i]);
    }
  }
  /**
   * Decrypts a block of data.
   * @param data is the byte array to decrypt.
   */
  public void decrypt(byte[] data){
    decrypt(data,0,data.length);
  }
  /**
   * Decrypts a block of data from {@code start} (inclusive) to {@code end} (exclusive).
   * @param data is the byte array to decrypt.
   */
  public void decrypt(byte[] data, int start, int end){
    for (int i=start;i<end;++i){
      data[i] = decrypt(data[i]);
    }
  }
  /**
   * Returns a variable-length hash of the symmetric key.
   * Used to verify multiple {@code StreamCipher} instances have the same internal state.
   * @param length is the desired hash length (must be greater than {@code 0}).
   * @return hash of the symmetric key.
   */
  public byte[] hash(int length){
    if (length==1){
      //Optimization to avoid needlessly recomputing keyXOR
      return hash(key.clone(), new byte[]{keyXOR}, 1);
    }else{
      return hash(key.clone(), length);
    }
  }
  /**
   * Computes a hash of {@code data} using {@code salt}.
   * 10000 iterations should be sufficient for password hashing.
   * Parameter contents will be modified.
   * @param data is the byte array to compute a hash value for.
   * @param salt is the byte array used to add extra entropy.
   * @param iteration specifies how many times to run the hashing algorithm.
   * @return the hashed data whose length is equal to {@code salt.length}
   */
  public static byte[] hash(byte[] data, byte[] salt, int iteration){
    StreamCipher c = new StreamCipher(data);
    for (int j=0;j<iteration;++j){
      c.encrypt(salt);
      c.encrypt(salt);
      c.decrypt(salt);
      c.encrypt(salt);
      c.decrypt(salt);
      c.decrypt(salt);
      c.decrypt(salt);
      c.encrypt(salt);
      c.encrypt(salt);
      c.decrypt(salt);
    }
    return salt;
  }
  /**
   * Computes a simple variable-length hash.
   * Parameter contents will be modified.
   * @param data is the byte array to hash.
   * @param length is the desired hash length (must be greater than {@code 0}).
   * @return a hash value of the specified length.
   */
  public static byte[] hash(byte[] data, int length){
    byte[] salt = new byte[length];
    boolean b;
    for (int i=0,j,k;i<data.length;++i){
      b = false;
      salt[0]^=data[i];
      for (j=0,k=1;k<length;++j,++k){
        b^=true;
        if (b){
          salt[k]+=salt[j];
        }else{
          salt[k]^=salt[j];
        }
      }
    }
    return hash(data, salt, 1);
  }
  /**
   * Computes a simple hash.
   * Parameter contents will be modified.
   * @param data is the byte array to hash.
   * @return the hash value.
   */
  public static long hash(byte[] data){
    return new SerializationStream(hash(data,8)).readLong();
  }
  /**
   * Returns a hash of the symmetric key.
   * Used to verify multiple {@code StreamCipher} instances have the same internal state.
   * @return hash of the symmetric key.
   * @see {@link #hash(int)}
   */
  @Override public int hashCode(){
    return new SerializationStream(hash(4)).readInt();
  }
}