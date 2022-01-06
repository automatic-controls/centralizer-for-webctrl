package aces.webctrl.centralizer.common;
import java.util.concurrent.atomic.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
/**
 * Thread-safe class encapsulating data pertaining to a single server.
 */
public class Server {
  /** This server's identification number. */
  private volatile int ID;
  /** Additional verification which helps to prevent one server from spoofing the identity of another. */
  private volatile byte[] identifier;
  /** This server's IP address. */
  private volatile String ipAddress;
  /** Unique friendly name for this server. */
  private volatile String name;
  /**
   * Descriptive text for this server.
   * For instance, this could specify how one should connect to the WebCTRL server (e.g. with a certain VPN).
   */
  private volatile String description;
  /** Indicates the time this server object was first created. */
  private volatile long creationTime;
  /** Indicates whether there is an active connection to this server. */
  private final AtomicBoolean connected = new AtomicBoolean();
  /**
   * Specifies whether this object has been flagged for garbage collection.
   * In particular, a value of {@code true} prevents this operator from modifying the filesystem.
   */
  private volatile boolean disposed = false;
  /**
   * Used for {@link #deserialize(byte[])}.
   */
  protected Server(){}
  /**
   * Creates a server object using information obtained over a TCP/IP connection.
   */
  protected Server(int ID, String ipAddress, String name, String description){
    creationTime = System.currentTimeMillis();
    this.ID = ID;
    this.ipAddress = ipAddress;
    this.name = name;
    this.description = description;
    connected.set(true);
    identifier = new byte[32];
    Database.entropy.nextBytes(identifier);
  }
  /**
   * Serializes an server into a byte array.
   */
  public byte[] serialize(boolean includeIdentifier){
    byte[] ipData = ipAddress.getBytes();
    byte[] nameData = name.getBytes();
    byte[] descData = description.getBytes();
    SerializationStream s = new SerializationStream(nameData.length+ipData.length+descData.length+24+(includeIdentifier?identifier.length+4:1));
    s.write(nameData);
    s.write(ipData);
    s.write(descData);
    if (includeIdentifier){
      s.write(identifier);
    }else{
      s.write(connected.get());
    }
    s.write(ID);
    s.write(creationTime);
    return s.data;
  }
  /**
   * Deserializes a server from a byte array.
   * @param data the byte array containing the information to be deserialized.
   * @throws IndexOutOfBoundsException if {@code data} has been corrupted.
   */
  public static Server deserialize(byte[] data, boolean includeIdentifier) throws IndexOutOfBoundsException {
    Server server = new Server();
    SerializationStream s = new SerializationStream(data);
    server.name = s.readString();
    server.ipAddress = s.readString();
    server.description = s.readString();
    if (includeIdentifier){
      server.identifier = s.readBytes();
    }else{
      server.connected.set(s.readBoolean());
    }
    server.ID = s.readInt();
    server.creationTime = s.readLong();
    if (!s.end()){
      throw new IndexOutOfBoundsException("Byte array is too large and cannot be deserialized into a Server.");
    }
    return server;
  }
  /**
   * Saves server data to the filesystem.
   * @param folder where to put the server's information.
   * @return {@code true} on success; {@code false} on failure.
   */
  protected synchronized boolean save(Path folder){
    if (disposed){
      //Indicates this operator has been deleted
      return true;
    }
    String name = getName();
    try{
      Path serverFolder = folder.resolve(name);
      if (!Files.exists(serverFolder)){
        Files.createDirectory(serverFolder);
      }
      Path dataFile = serverFolder.resolve("data");
      ByteBuffer buf = ByteBuffer.wrap(serialize(true));
      try(
        FileChannel out = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        FileLock lock = out.tryLock();
      ){
        out.write(buf);
      }
      return true;
    }catch(Exception e){
      Logger.log("Error occurred while attempting to save server data for "+name+'.', e);
      return false;
    }
  }
  /**
   * Prevents this object from making any changes to the filesystem.
   * Blocks until methods currently modifying the filesystem terminate naturally.
   */
  protected synchronized void dispose(){
    disposed = true;
  }
  /**
   * @return whether or not this server has been disposed.
   */
  public boolean isDisposed(){
    return disposed;
  }
  /**
   * Sets the IP address of this server.
   */
  public void setIPAddress(String ipAddress){
    this.ipAddress = ipAddress;
  }
  /**
   * @return the IP address of this server.
   */
  public String getIPAddress(){
    return ipAddress;
  }
  /**
   * Sets the name of this server.
   * @return {@code true} if the name was changed successfully; {@code false} if the new name failed validation.
   */
  public boolean setName(String name){
    if (Database.validateName(name, true)){
      this.name = name;
      return true;
    }else{
      return false;
    }
  }
  /**
   * @return the name of this server.
   */
  public String getName(){
    return name;
  }
  /**
   * Sets the description of this server.
   */
  public void setDescription(String description){
    this.description = description;
  }
  /**
   * @return the description of this server.
   */
  public String getDescription(){
    return description;
  }
  /**
   * @return the {@code ID} for this server.
   */
  public int getID(){
    return ID;
  }
  /**
   * DO NOT MODIFY THE RETURNED BYTE ARRAY.
   * This identifier is an automatically-generated shared secret between the database and the server it identifies.
   * It helps prevent servers from spoofing their identity.
   * This data should only be transferred after encryption has been established.
   * @return the byte array which uniquely identities this server.
   */
  public byte[] getIdentifier(){
    return identifier;
  }
  /**
   * @return the value of {@code System.currentTimeMillis()} as recorded at the time of creation.
   */
  public long getCreationTime(){
    return creationTime;
  }
  /**
   * @return whether or not this server is currently connected.
   */
  public boolean isConnected(){
    return connected.get();
  }
  /**
   * @return {@code true} if the server was successfully connected; {@code false} if the server is already connected.
   */
  public boolean connect(){
    return connected.compareAndSet(false,true);
  }
  /**
   * @return {@code true} if the server was successfully disconnected; {@code false} if the server wasn't connected in the first place.
   */
  public boolean disconnect(){
    return connected.compareAndSet(true,false);
  }
}