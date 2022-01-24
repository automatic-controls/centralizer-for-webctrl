/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.database;
import aces.webctrl.centralizer.common.*;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.atomic.*;
import java.nio.channels.*;
import java.security.spec.*;
import javax.crypto.*;
public class Connection implements Comparable<Connection> {
  private final static AtomicLong nextID = new AtomicLong();
  private final long ID = nextID.getAndIncrement();
  private final TreeMap<Integer,OperatorTracker> operators = new TreeMap<Integer,OperatorTracker>();
  private final Queue<Task> queue = new LinkedList<Task>();
  private final Connection THIS = this;
  protected volatile SocketWrapper wrap;
  protected volatile Server server = null;
  private volatile boolean initialized = false;
  private final AtomicBoolean closed = new AtomicBoolean();
  public Connection(AsynchronousSocketChannel ch){
    wrap = new SocketWrapper(ch);
    Logger.logAsync("Establishing connection to "+wrap.getIP());
  }
  /**
   * Adds a task to the queue.
   */
  public void add(Task t){
    synchronized (queue){
      queue.add(t);
    }
  }
  /**
   * @return whether or not initialization has been completed successfully.
   */
  public boolean isInitialized(){
    return initialized;
  }
  /**
   * Provides an ordering on {@code Connection} objects.
   */
  @Override public int compareTo(Connection con){
    if (ID==con.ID){
      return 0;
    }else if (ID>con.ID){
      return 1;
    }else{
      return -1;
    }
  }
  /**
   * Terminates the connection.
   * @param remove specifies whether to remove this {@code Connection} from the connection list.
   * @return {@code true} on success; {@code false} if an error occurs while closing the socket.
   */
  public boolean close(boolean remove){
    if (closed.compareAndSet(false,true)){
      if (remove){
        Connections.remove(this);
      }
      if (server==null){
        Logger.logAsync("Connection closed to "+wrap.getIP());
      }else{
        server.disconnect();
        Logger.logAsync("Server disconnected: "+server.getName());
      }
      if (wrap.isClosed()){
        return true;
      }else{
        return wrap.close();
      }
    }
    return true;
  }
  /**
   * Removes all invalidated trackers and then returns a list containing all logged in operators with their current expiration dates.
   * @return a deep-copy of the operator tracker list.
   */
  public ArrayList<OperatorTracker> getTrackers(){
    synchronized (operators){
      Set<Entry<Integer,OperatorTracker>> set = operators.entrySet();
      Iterator<Entry<Integer,OperatorTracker>> iter = set.iterator();
      OperatorTracker t;
      while (iter.hasNext()){
        t = iter.next().getValue();
        if (!t.validate()){
          Logger.logAsync("Operator "+t.getOperator().getUsername()+" removed from "+(server==null?wrap.getIP():server.getName())+" due to inactivity");
          iter.remove();
        }
      }
      ArrayList<OperatorTracker> arr = new ArrayList<OperatorTracker>(operators.size());
      for (Entry<Integer,OperatorTracker> e: set){
        arr.add(new OperatorTracker(e.getValue()));
      }
      return arr;
    }
  }
  /**
   * This method automatically removes invalidated operator trackers from the list.
   * @return An {@code OperatorTracker} if the operator with the given {@code ID} is currently logged in; {@code null} otherwise.
   */
  public OperatorTracker getTracker(int ID){
    synchronized (operators){
      OperatorTracker t = operators.get(ID);
      if (t==null){
        return null;
      }else{
        if (t.validate()){
          return t;
        }else{
          operators.remove(ID);
          Logger.logAsync("Operator "+t.getOperator().getUsername()+" removed from "+(server==null?wrap.getIP():server.getName())+" due to inactivity");
          return null;
        }
      }
    }
  }
  /**
   * Login the specified operator.
   * If the operator is already logged in, this will return the corresponding tracker and reset the expiry timeout.
   * @return The newly created tracker or {@code null} if the parameter {@code op} is null.
   */
  public OperatorTracker login(Operator op){
    if (op==null){
      return null;
    }
    int ID = op.getID();
    OperatorTracker t;
    synchronized (operators){
      t = operators.get(ID);
      if (t==null){
        t = new OperatorTracker(op);
        operators.put(ID,t);
        Logger.logAsync("Operator "+op.getUsername()+" logged on to "+(server==null?wrap.getIP():server.getName()));
      }else{
        t.reset();
      }
    }
    return t;
  }
  /**
   * Logout the operator with the given {@code ID}.
   * @return {@code true} if the operator was successfully logged out; {@code false} if the operator was never logged in to begin with.
   */
  public boolean logout(int ID){
    OperatorTracker t;
    synchronized (operators){
      t = operators.remove(ID);
    }
    if (t!=null){
      Logger.logAsync("Operator "+t.getOperator().getUsername()+" logged off from "+(server==null?wrap.getIP():server.getName()));
    }
    return t!=null;
  }
  /**
   * Convenience {@code CompletionHandler} class for having a default {@code failed} method.
   */
  abstract class Handler<T> implements CompletionHandler<T,Void> {
    public abstract void func(T ret);
    @Override public void completed(T ret, Void v){
      func(ret);
    }
    @Override public void failed(Throwable e, Void v){
      close(true);
    }
  }
  public void listen1(){
    if (server!=null && server.isDisposed()){
      close(true);
      return;
    }
    wrap.listen(null, new Handler<Void>(){
      public void func(Void v){
        listen2();
      }
    });
  }
  public void listen2(){
    if (server!=null && server.isDisposed()){
      close(true);
      return;
    }
    Task t;
    synchronized (queue){
      t = queue.poll();
    }
    if (t==null){
      wrap.write(Protocol.NO_FURTHER_INSTRUCTIONS,null,new Handler<Void>(){
        public void func(Void v){
          listen3();
        }
      });
    }else{
      wrap.write(t.getType(),null,new Handler<Void>(){
        public void func(Void v){
          try{
            t.run();
          }catch(Throwable e){
            Logger.logAsync("Unexpected error occurred.",e);
            close(true);
          }
        }
      });
    }
  }
  public void listen3(){
    if (server!=null && server.isDisposed()){
      close(true);
      return;
    }
    wrap.read(null, new Handler<Byte>(){
      public void func(Byte X){
        byte x = X.byteValue();
        if (x==Protocol.NO_FURTHER_INSTRUCTIONS){
          listen1();
        }else{
          ProtocolMap.exec(THIS,x);
        }
      }
    });
  }
  public void init(){
    SerializationStream s = new SerializationStream(Config.VERSION_RAW.length+4);
    s.write(Config.VERSION_RAW);
    //Write the application version to the client
    wrap.writeBytes(s.data, null, new Handler<Void>(){
      public void func(Void v){
        //Read the requested key ID from the client
        wrap.readBytes(4, null, new Handler<byte[]>(){
          public void func(byte[] arr){
            int ID = new SerializationStream(arr).readInt();
            Key kk = Keys.getPreferredKey();
            if (ID>=0 && ID!=kk.getID()){
              kk = Keys.get(ID);
              //Add a task to the queue which updates the client's preshared key on the next ping
              updatePresharedKey();
            }
            final Key k = kk;
            if (k==null){
              //If the requested key does not exist, terminate the connection
              close(true);
            }else{
              SerializationStream s = new SerializationStream(k.length(false));
              k.serialize(s,false);
              //Send the requested key back to the client
              wrap.writeBytes(s.data, null, new Handler<Void>(){
                public void func(Void v){
                  //Read and decrypt the client's temporary public key
                  wrap.readBytes(16384, null, new Handler<byte[]>(){
                    public void func(byte[] tmpPublicKey){
                      try{
                        tmpPublicKey = k.decrypt(tmpPublicKey);
                        //Generate and encrypt a new symmetric key for this session
                        final byte[] symmetricKey = new byte[16];
                        Database.entropy.nextBytes(symmetricKey);
                        Cipher cipher = Cipher.getInstance(Database.CIPHER);
                        synchronized (Keys.keyFactory){
                          cipher.init(Cipher.ENCRYPT_MODE, Keys.keyFactory.generatePublic(new X509EncodedKeySpec(tmpPublicKey)));
                        }
                        //Send the encrypted symmetric key to the client
                        wrap.writeBytes(cipher.doFinal(symmetricKey), null, new Handler<Void>(){
                          public void func(Void v){
                            //Now encryption has been successfully setup
                            wrap.setCipher(new StreamCipher(symmetricKey));
                            //Adds tasks to the queue which will update the client on the next ping
                            updatePingInterval();
                            updateOperators(null);
                            //Notify the client as to whether this application is a fresh install
                            final boolean blankInstall = Operators.count()==0;
                            wrap.write(blankInstall?Protocol.BLANK_INSTALL:Protocol.ALREADY_CONFIGURED, null, new Handler<Void>(){
                              public void func(Void v){
                                if (blankInstall){
                                  //Read system admin credentials from the client
                                  wrap.readBytes(16384, null, new Handler<byte[]>(){
                                    public void func(byte[] data){
                                      try{
                                        SerializationStream s = new SerializationStream(data);
                                        Operator op = Operators.add(s.readString(), s.readBytes(), Permissions.ADMINISTRATOR, s.readString(), s.readInt(), s.readString());
                                        if (!s.end()){
                                          Logger.logAsync("Lost data detected.");
                                        }
                                        Arrays.fill(s.data,(byte)0);
                                        if (op==null){
                                          Logger.logAsync("Retrieved operator does not meet requirements.");
                                        }else{
                                          Logger.logAsync("Database successfully configured with system admin: "+op.getUsername());
                                        }
                                        //Send a status message and terminate the connection
                                        wrap.write(op==null?Protocol.FAILURE:Protocol.SUCCESS, null, new Handler<Void>(){
                                          public void func(Void v){
                                            close(true);
                                          }
                                        });
                                      }catch(Throwable e){
                                        Logger.logAsync("Database configuration error.");
                                        close(true);
                                      }
                                    }
                                  });
                                }else{
                                  //Read a byte indicating the type of client we are dealing with
                                  wrap.read(null, new Handler<Byte>(){
                                    public void func(Byte X){
                                      byte x = X.byteValue();
                                      if (x==Protocol.NEW_SERVER){
                                        //Read details to configure the new server
                                        wrap.readBytes(16384, null, new Handler<byte[]>(){
                                          public void func(byte[] data){
                                            try{
                                              SerializationStream s = new SerializationStream(data);
                                              server = Servers.add(wrap.getIP(), s.readString(), s.readString());
                                              if (!s.end()){
                                                Logger.logAsync("Lost data detected.");
                                              }
                                              if (server==null){
                                                Logger.logAsync("Server failed to initialize.");
                                                //Write a failure message and terminate the connection
                                                wrap.write(Protocol.FAILURE, null, new Handler<Void>(){
                                                  public void func(Void v){
                                                    close(true);
                                                  }
                                                });
                                              }else{
                                                //Write a success message
                                                wrap.write(Protocol.SUCCESS, null, new Handler<Void>(){
                                                  public void func(Void v){
                                                    final byte[] identifier = server.getIdentifier();
                                                    SerializationStream s = new SerializationStream(identifier.length+8);
                                                    s.write(server.getID());
                                                    s.write(identifier);
                                                    //Write data which will allow the client to identity itself in the future
                                                    wrap.writeBytes(s.data, null, new Handler<Void>(){
                                                      public void func(Void v){
                                                        Logger.logAsync("New server initialized: "+server.getName());
                                                        //go to the listen/ping protocol
                                                        initialized = true;
                                                        listen1();
                                                      }
                                                    });
                                                  }
                                                });
                                              }
                                            }catch(Throwable e){
                                              Logger.logAsync("Error occurred while configuring new server.");
                                              close(true);
                                            }
                                          }
                                        });
                                      }else if (x==Protocol.EXISTING_SERVER){
                                        updateServerParams();
                                        //Read identifying information from the client
                                        wrap.readBytes(1024, null, new Handler<byte[]>(){
                                          public void func(byte[] data){
                                            try{
                                              SerializationStream s = new SerializationStream(data);
                                              server = Servers.get(s.readInt());
                                              boolean go = server!=null && Arrays.equals(s.readBytes(), server.getIdentifier()) && server.connect();
                                              if (go){
                                                if (!s.end()){
                                                  Logger.logAsync("Lost data detected.");
                                                }
                                                //Write a success message and transition to the listen/ping protocol
                                                wrap.write(Protocol.SUCCESS, null, new Handler<Void>(){
                                                  public void func(Void v){
                                                    server.setIPAddress(wrap.getIP());
                                                    Logger.logAsync("Server connected: "+server.getName());
                                                    initialized = true;
                                                    listen1();
                                                  }
                                                });
                                              }else{
                                                Logger.logAsync("Unable to identify server.");
                                                //Write a failure message and terminate the connection
                                                wrap.write(Protocol.FAILURE, null, new Handler<Void>(){
                                                  public void func(Void v){
                                                    close(true);
                                                  }
                                                });
                                              }
                                            }catch(Throwable e){
                                              Logger.logAsync("Error occurred while determining identity of server.");
                                              close(true);
                                            }
                                          }
                                        });
                                      }else{
                                        //The client type is unspecified, so we go straight to the listen/ping protocol
                                        initialized = true;
                                        listen1();
                                      }
                                    }
                                  });
                                }
                              }
                            });
                          }
                        });
                      }catch(Throwable e){
                        Logger.logAsync("Cipher negotiation error occurred.",e);
                        close(true);
                      }
                    }
                  });
                }
              });
            }
          }
        });
      }
    });
  }
  /**
   * Writes the most recent preshared key to the client.
   */
  public void updatePresharedKey(){
    add(new Task(Protocol.UPDATE_PRESHARED_KEY){
      public void run(){
        Key k = Keys.getPreferredKey();
        SerializationStream s = new SerializationStream(k.length(false));
        k.serialize(s,false);
        wrap.writeBytes(s.data, null, new Handler<Void>(){
          public void func(Void v){
            listen2();
          }
        });
      }
    });
  }
  /**
   * Write the new ping interval to the client.
   */
  public void updatePingInterval(){
    add(new Task(Protocol.UPDATE_PING_INTERVAL){
      public void run(){
        SerializationStream s = new SerializationStream(8);
        s.write(Config.pingInterval);
        wrap.writeBytes(s.data, null, new Handler<Void>(){
          public void func(Void v){
            listen2();
          }
        });
      }
    });
  }
  /**
   * Writes the server name and description to the client.
   */
  public void updateServerParams(){
    if (server!=null){
      add(new Task(Protocol.UPDATE_SERVER_PARAMS){
        public void run(){
          byte[] nameBytes = server.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8);
          byte[] descBytes = server.getDescription().getBytes(java.nio.charset.StandardCharsets.UTF_8);
          SerializationStream s = new SerializationStream(nameBytes.length+descBytes.length+8);
          s.write(nameBytes);
          s.write(descBytes);
          wrap.writeBytes(s.data, null, new Handler<Void>(){
            public void func(Void v){
              listen2();
            }
          });
        }
      });
    }
  }
  /**
   * Ensures the client's operator list is up to date.
   * If {@code op} is {@code null}, then all operators are updated.
   * Otherwise, only the specified operator is updated.
   */
  public void updateOperators(final Operator op){
    if (op==null){
      Operators.forEach(new java.util.function.Predicate<Operator>(){
        public boolean test(Operator x){
          updateOperators(x);
          return true;
        }
      });
    }else{
      add(new Task(Protocol.UPDATE_OPERATORS){
        public void run(){
          SerializationStream s = new SerializationStream(12);
          s.write(op.getID());
          if (op.isDisposed()){
            s.write(-1L);
            wrap.writeBytes(s.data, null, new Handler<Void>(){
              public void func(Void v){
                listen2();
              }
            });
          }else{
            s.write(op.getStamp());
            wrap.writeBytes(s.data, null, new Handler<Void>(){
              public void func(Void v){
                wrap.read(null, new Handler<Byte>(){
                  public void func(Byte b){
                    if (b.byteValue()==0){
                      listen2();
                    }else{
                      wrap.writeBytes(op.serialize(), null, new Handler<Void>(){
                        public void func(Void v){
                          listen2();
                        }
                      });
                    }
                  }
                });
              }
            });
          }
        }
      });
    }
  }
}