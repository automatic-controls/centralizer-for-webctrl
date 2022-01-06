package aces.webctrl.centralizer.common;
import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.*;
/**
 * Thread-safe class which encapsulates a single 4096 bit RSA key-pair.
 */
public class Key {
  /** Unique identification number for this key-pair. */
  private volatile int ID;
  /** Raw bytes which encode the public key. */
  private volatile byte[] publicKeyRaw = null;
  /** Raw bytes which encode the private key. */
  private volatile byte[] privateKeyRaw = null;
  /** The public key used for encryption. */
  private volatile PublicKey publicKey = null;
  /** The private key used for decryption. */
  private volatile PrivateKey privateKey = null;
  /** Hex string hash of the public key. */
  private String hash = null;
  /**
   * Used for deserialization.
   */
  private Key(){}
  /**
   * Generate a new key pair with the given identification number.
   */
  protected Key(int ID) throws Exception {
    this.ID = ID;
    KeyPair pair;
    synchronized (Keys.keyPairGen){
      pair = Keys.keyPairGen.generateKeyPair();
    }
    publicKey = pair.getPublic();
    publicKeyRaw = publicKey.getEncoded();
    privateKey = pair.getPrivate();
    privateKeyRaw = privateKey.getEncoded();
  }
  /**
   * Encrypts data using the public key.
   */
  public byte[] encrypt(byte[] data) throws Exception {
    Cipher c = Cipher.getInstance(Database.CIPHER);
    c.init(Cipher.ENCRYPT_MODE, publicKey, Database.entropy);
    return c.doFinal(data);
  }
  /**
   * Decrypts data using the private key.
   */
  public byte[] decrypt(byte[] data) throws Exception {
    Cipher c = Cipher.getInstance(Database.CIPHER);
    c.init(Cipher.DECRYPT_MODE, privateKey, Database.entropy);
    return c.doFinal(data);
  }
  /**
   * Used to precompute serialization length.
   */
  public int length(boolean includePrivate){
    return publicKeyRaw.length+(includePrivate?privateKeyRaw.length:0)+12;
  }
  /**
   * @return the {@code ID} for this key.
   */
  public int getID(){
    return ID;
  }
  /**
   * Serializes data associated to this key.
   */
  public void serialize(SerializationStream s, boolean includePrivate){
    s.write(ID);
    s.write(publicKeyRaw);
    if (includePrivate){
      s.write(privateKeyRaw);
    }
  }
  /**
   * Deserializes a key from the given data.
   */
  public static Key deserialize(SerializationStream s, boolean includePrivate) throws Exception {
    Key k = new Key();
    k.ID = s.readInt();
    k.publicKeyRaw = s.readBytes();
    synchronized (Keys.keyFactory){
      k.publicKey = Keys.keyFactory.generatePublic(new X509EncodedKeySpec(k.publicKeyRaw));
    }
    if (includePrivate){
      k.privateKeyRaw = s.readBytes();
      synchronized (Keys.keyFactory){
        k.privateKey = Keys.keyFactory.generatePrivate(new PKCS8EncodedKeySpec(k.privateKeyRaw));
      }
    }
    return k;
  }
  /**
   * @return Hex string hash of the public key.
   */
  public String getHashString(){
    if (hash==null){
      hash = Long.toHexString(StreamCipher.hash(publicKeyRaw.clone()));
    }
    return hash;
  }
  @Override public boolean equals(Object obj){
    if (obj instanceof Key){
      Key k = (Key)obj;
      return ID==k.ID && java.util.Arrays.equals(publicKeyRaw, k.publicKeyRaw) && java.util.Arrays.equals(privateKeyRaw, k.privateKeyRaw);
    }else{
      return false;
    }
  }
}