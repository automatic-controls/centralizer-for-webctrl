/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.net.*;
import javax.servlet.*;
import com.controlj.green.addonsupport.*;
/** Namespace which controls the primary operation of this addon */
public class Initializer implements ServletContextListener {
  /** The name of this addon */
  private volatile static String name;
  /** Prefix used for constructing relative URL paths */
  private volatile static String prefix;
  /** The main processing thread */
  private volatile static Thread mainThread = null;
  /** Tracks results to perform callbacks if the socket should be disconnected before normal result resolution can occur. */
  private final static List<Result<?>> results = Collections.synchronizedList(new LinkedList<Result<?>>());
  /** Task processing queue */
  private final static DelayQueue<DelayedRunnable> queue = new DelayQueue<DelayedRunnable>();
  /** Becomes true when the servlet context is destroyed */
  private volatile static boolean stop = false;
  /** Becomes true when the primary processing thread is terminated */
  private volatile static boolean stopped = false;
  /** Status message which may be displayed to to clients */
  private volatile static String status = "Uninitialized";
  /** Used to ensure the log file does not overflow with error messages when we try and fail to reconnect every few seconds */
  private volatile static boolean logConnectionErrors = true;
  /** Wraps the primary {@code AsynchronousSocketChannel} */
  private volatile static SocketWrapper wrap = null;
  /** The socket used to connect to the central database */
  private volatile static AsynchronousSocketChannel ch = null;
  /** Single-threaded group used for processing socket channel IO */
  private volatile static AsynchronousChannelGroup grp = null;
  /** Used to keep track of whether a task is being aynchronously executed */
  public volatile static boolean taskExecuting = false;
  /** Whether or not there is a connection to the central database */
  private volatile static boolean connected = false;
  /** Controls access to {@code nextPing} */
  private final static Object nextPingLock = new Object();
  /** Maintains the next ping task, so the database is not pinged more than is necessary */
  private volatile static Task nextPing = null;
  /** Queue containing actions which run on the next ping. */
  private final static Queue<RunnableProtocol> protocolQueue = new LinkedList<RunnableProtocol>();
  /** Path to JSP file which is edited to prevent addon removal. */
  private volatile static Path webapps_lite = null;
  /** Path to the WebCTRL installation directory. */
  public volatile static Path rootWebCTRL = null;
  /** Path to the active WebCTRL system folder (e.g, {@code C:\WebCTRL8.0\webroot\test_system}). */
  public volatile static Path systemFolder = null;
  /** Path to the addons folder of WebCTRL. */
  public volatile static Path addonsFolder = null;
  /** Contains basic informatin about this addon. */
  public volatile static AddOnInfo info = null;


  /** Used to control setup parameters for blank databases. */
  private volatile static Object blankSetupLock = new Object();
  private volatile static boolean setupBlank = false;
  private volatile static byte[] username = null;
  private volatile static byte[] password = null;
  private volatile static byte[] displayName = null;
  private volatile static int navigationTimeout = -1;
  private volatile static byte[] description = null;
  public volatile static long connectionKey = 0;

  /**
   * @return whether or not there is an active connection to the database.
   */
  public static boolean isConnected(){
    return connected;
  }
  /**
   * Sets parameters for configuring blank databases.
   * @return whether configuration was successful.
   */
  public static boolean configureBlank(String username, char[] password, String displayName, int navigationTimeout, String description){
    synchronized (blankSetupLock){
      if (Initializer.password!=null){
        java.util.Arrays.fill(Initializer.password, (byte)0);
        Initializer.password = null;
      }
      setupBlank = username!=null && password!=null && displayName!=null && description!=null && Database.validateName(username,false) && Database.validatePassword(password);
      if (!setupBlank){
        return false;
      }
      Initializer.username = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      Initializer.password = Utility.toBytes(password);
      Initializer.displayName = displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      Initializer.navigationTimeout = navigationTimeout;
      Initializer.description = description.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      return true;
    }
  }
  public static void disableBlankSetup(){
    synchronized (blankSetupLock){
      setupBlank = false;
      if (Initializer.password!=null){
        java.util.Arrays.fill(Initializer.password, (byte)0);
        Initializer.password = null;
      }
      username = null;
      displayName = null;
      description = null;
    }
  }
  /** @return a status message. */
  public static String getStatus(){
    return status;
  }
  /** @return the name of this application. */
  public static String getName(){
    return name;
  }
  /** @return the prefix to use for constructing relative URL paths. */
  public static String getPrefix(){
    return prefix;
  }
  static {
    // Temporary workaround until ALC releases a patch for WebCTRL
    try{
      Class.forName("com.controlj.green.directaccess.DirectAccessInternal").getMethod("getDirectAccessInternal").invoke(null);
      com.controlj.green.addonsupport.access.DirectAccess.getDirectAccess();
    }catch (Throwable t){}
  }
  /** Loads the local database and attempts to establish a connection to the central database. */
  @Override public void contextInitialized(ServletContextEvent sce){
    info = AddOnInfo.getAddOnInfo();
    name = info.getName();
    prefix = '/'+name+'/';
    Path root = info.getPrivateDir().toPath();
    try{
      Logger.init(root.resolve("log.txt"), new Consumer<DelayedRunnable>(){
        public void accept(DelayedRunnable r){
          enqueue(r);
        }
      });
      systemFolder = root.getParent().getParent().getParent().normalize();
      rootWebCTRL = systemFolder.getParent().getParent().normalize();
      addonsFolder = rootWebCTRL.resolve("addons").normalize();
      webapps_lite = rootWebCTRL.resolve("webroot").resolve("_common").resolve("lvl5").resolve("config").resolve("webapps_lite.jsp").normalize();
    }catch(Throwable e){
      e.printStackTrace();
    }
    SocketWrapper.config = new SocketWrapperConfig(){
      public long getTimeout(){
        return ClientConfig.timeout;
      }
      public long getPingInterval(){
        return ClientConfig.pingInterval;
      }
    };
    Database.init(root, false);
    ClientConfig.init(root.resolve("config"));
    ClientConfig.load();
    Logger.trim(ClientConfig.deleteLogAfter);
    Uploads.init(root.resolve("uploads"));
    HelperAPI.logoutAllForeign();
    HelperAPI.activateWebOperatorProvider(name);
    mainThread = new Thread(){
      public void run(){
        enqueueConfigure(0);
        enqueueBackup();
        enqueueConnect(0);
        enqueueUpload();
        DelayedRunnable r;
        ArrayList<Task> arr = new ArrayList<Task>();
        boolean b;
        Task t;
        while (!stop){
          try{
            while (!stop && (r=queue.poll())!=null){
              if (r instanceof Task){
                t = (Task)r;
                if (t.isValid()){
                  //taskExecuting must be evaluated before connected in order to prevent a race condition, which is why we save taskExecuting to a temporary variable b
                  b = taskExecuting;
                  if (connected){
                    if (b){
                      arr.add(t);
                    }else{
                      taskExecuting = true;
                      try{
                        t.run();
                      }catch(Throwable e){
                        Logger.log("Error occurred while running task in primary queue.", e);
                        forceDisconnect();
                      }
                    }
                  }
                }
              }else{
                try{
                  r.run();
                }catch(Throwable e){
                  Logger.log("Error occurred while running action in primary queue.", e);
                }
              }
            }
            if (stop){
              break;
            }
            r = queue.poll(500, TimeUnit.MILLISECONDS);
            if (r!=null){
              queue.offer(r);
            }
            if (connected){
              queue.addAll(arr);
            }
            arr.clear();
          }catch(InterruptedException e){}
        }
        stopped = true;
        synchronized (Initializer.class){
          Initializer.class.notifyAll();
        }
      }
    };
    Logger.log("Centralizer addon successfully initialized.");
    status = "Initialized";
    mainThread.start();
  }
  /** Disconnects from the central database and saves a copy of the local database. */
  @Override public void contextDestroyed(ServletContextEvent sce){
    stop = true;
    status = "Shutting down...";
    if (mainThread!=null){
      mainThread.interrupt();
      //Wait for the primary processing thread to terminate.
      synchronized (Initializer.class){
        while (!stopped){
          try{
            Initializer.class.wait(1000);
          }catch(Throwable err){}
        }
      }
    }
    forceDisconnect();
    save();
    PacketLogger.stop();
    HelperAPI.activateDefaultWebOperatorProvider();
    HelperAPI.logoutAllForeign();
    Logger.log("Centralizer addon has been shut down.");
    Logger.close();
  }
  /** Cancels any outstanding ping operations */
  private static void cancelNextPing(){
    synchronized (nextPingLock){
      if (nextPing!=null){
        nextPing.cancel();
        nextPing = null;
      }
    }
  }
  /** Used to forcibly close the socket. */
  public static void forceDisconnect(){
    try{
      if (connected && wrap!=null && !wrap.isClosed()){
        wrap.close();
        grp.shutdownNow();
        cancelNextPing();
        connected = false;
        taskExecuting = false;
        clearResults();
        status = "Disconnected";
        Logger.logAsync("Forcibly disconnected from central database.");
        enqueueConnect(System.currentTimeMillis()+(ClientConfig.reconnectTimeout>>1));
      }
    }catch(Throwable e){
      cancelNextPing();
      connected = false;
      taskExecuting = false;
      status = "Disconnected";
      enqueueConnect(System.currentTimeMillis()+(ClientConfig.reconnectTimeout>>1));
    }
    grp = null;
    wrap = null;
    ch = null;
  }
  /** Called whenever a {@code Task} unexpectedly fails */
  public static void disconnect(Throwable e){
    connected = false;
    taskExecuting = false;
    ch = null;
    wrap = null;
    if (grp!=null){
      try{
        grp.shutdownNow();
      }catch(Throwable err){}
      grp = null;
    }
    cancelNextPing();
    clearResults();
    status = "Disconnected";
    Logger.logAsync("Task failed with error.", e);
    enqueueConnect(System.currentTimeMillis()+(ClientConfig.reconnectTimeout>>3));
  }
  private static void clearResults(){
    synchronized (results){
      for (Result<?> r:results){
        if (!r.isFinished()){
          r.setResult(null);
        }
      }
      results.clear();
    }
  }
  /** Enqueues a task on the primary processing queue */
  public static void enqueue(DelayedRunnable r){
    queue.offer(r);
  }
  /** Enqueues a task to apply custom edits to system WebCTRL files. */
  private static void enqueueConfigure(long expiry){
    enqueue(new DelayedRunnable(expiry){
      public void run(){
        disableAddonRemoval();
      }
    });
  }
  /**
   * Edits systems WebCTRL files to disable removal of this addon.
   * @return {@code true} if successful; {@code false} otherwise.
   */
  public static boolean disableAddonRemoval(){
    try{
      if (Files.exists(webapps_lite)){
        ByteBuffer buf = ByteBuffer.wrap(new String(Files.readAllBytes(webapps_lite), java.nio.charset.StandardCharsets.UTF_8).replaceAll(
          "(?<!function )executeWebappCommand\\(\\s*+(?!'start')[^,]++,\\s*+(\\w++)\\s*+\\);?+(?!/\\*EDITED\\*/)",
          "if ($1!=\""+name+"\"){ $0/*EDITED*/ }"
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(
          FileChannel ch = FileChannel.open(webapps_lite, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
          FileLock lock = ch.tryLock();
        ){
          while (buf.hasRemaining()){
            ch.write(buf);
          }
        }
        return true;
      }else{
        Logger.logAsync("Could not disable addon removal because webapps_lite cannot be found.");
        return false;
      }
    }catch(Throwable e){
      Logger.logAsync("Error occurred while disabling addon removal.", e);
      return false;
    }
  }
  /**
   * Edits systems WebCTRL files to enable removal of this addon.
   * @return {@code true} if successful; {@code false} otherwise.
   */
  public static boolean enableAddonRemoval(){
    try{
      if (Files.exists(webapps_lite)){
        ByteBuffer buf = ByteBuffer.wrap(new String(Files.readAllBytes(webapps_lite), java.nio.charset.StandardCharsets.UTF_8).replaceAll(
          "if \\(\\w++!=\""+name+"\"\\)\\{ (.+?)/\\*EDITED\\*/ \\}",
          "$1"
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(
          FileChannel ch = FileChannel.open(webapps_lite, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
          FileLock lock = ch.tryLock();
        ){
          while (buf.hasRemaining()){
            ch.write(buf);
          }
        }
        return true;
      }else{
        Logger.logAsync("Could not enable addon removal because webapps_lite cannot be found.");
        return false;
      }
    }catch(Throwable e){
      Logger.logAsync("Error occurred while enabling addon removal.", e);
      return false;
    }
  }
  /** Enqueues a task which attempts to connect to the database */
  private static void enqueueConnect(long expiry){
    if (!stop){
      enqueue(new DelayedRunnable(expiry){
        public void run(){
          ClientConfig.ipLock.readLock().lock();
          String host = ClientConfig.host;
          int port = ClientConfig.port;
          ClientConfig.ipLock.readLock().unlock();
          if (host==null){
            enqueueConnect(System.currentTimeMillis()+ClientConfig.reconnectTimeout);
          }else{
            try{
              status = "Connecting...";
              grp = AsynchronousChannelGroup.withFixedThreadPool(1, java.util.concurrent.Executors.defaultThreadFactory());
              ch = AsynchronousSocketChannel.open(grp);
              Future<Void> f = ch.connect(new InetSocketAddress(host,port));
              try{
                f.get(ClientConfig.timeout, TimeUnit.MILLISECONDS);
              }catch(TimeoutException e){
                f.cancel(true);
                throw e;
              }
              wrap = new SocketWrapper(ch);
              connected = true;
              logConnectionErrors = true;
              enqueue(new Task(wrap,0){
                public void run(){
                  //Read the application version
                  wrapper.readBytes(256, null, new Handler<byte[]>(){
                    public boolean onSuccess(byte[] arr){
                      String version = new SerializationStream(arr).readString();
                      //Ensure the database and addon versions are compatible
                      if (!Config.isCompatibleVersion(version)){
                        Logger.logAsync("Incompatible versions: "+Config.VERSION+" and "+version);
                        forceDisconnect();
                      }else{
                        SerializationStream s = new SerializationStream(4);
                        final Key k = ClientConfig.databaseKey;
                        s.write(k==null?-1:k.getID());
                        //Write the requested key ID
                        wrapper.writeBytes(s.data, null, new Handler<Void>(){
                          public boolean onSuccess(Void v){
                            //Read the requested key
                            wrapper.readBytes(16384, null, new Handler<byte[]>(){
                              public boolean onSuccess(byte[] arr){
                                try{
                                  final Key kk = Key.deserialize(new SerializationStream(arr),false);
                                  //Ensure the key meets expectations
                                  if (k==null){
                                    ClientConfig.databaseKey = kk;
                                  }else if (!k.equals(kk)){
                                    forceDisconnect();
                                  }
                                  //Generate a temporary KeyPair for the handshake
                                  java.security.KeyPair pair;
                                  synchronized (Keys.keyPairGen){
                                    pair = Keys.keyPairGen.generateKeyPair();
                                  }
                                  final java.security.PrivateKey pk = pair.getPrivate();
                                  //Encrypt and write the temporary public key
                                  wrapper.writeBytes(kk.encrypt(pair.getPublic().getEncoded()), null, new Handler<Void>(){
                                    public boolean onSuccess(Void v){
                                      //Read the encrypted symmetric key to use for this session
                                      wrapper.readBytes(16384, null, new Handler<byte[]>(){
                                        public boolean onSuccess(byte[] arr){
                                          try{
                                            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(Database.CIPHER);
                                            c.init(javax.crypto.Cipher.DECRYPT_MODE, pk);
                                            //Decrypt the symmetric key and use it to initialize a StreamCipher
                                            wrapper.setCipher(new StreamCipher(c.doFinal(arr)));
                                            //Determine whether the central database is a blank install
                                            wrapper.read(null, new Handler<Byte>(){
                                              public boolean onSuccess(Byte B){
                                                if (B.byteValue()==Protocol.BLANK_INSTALL){
                                                  SerializationStream s;
                                                  synchronized (blankSetupLock){
                                                    if (setupBlank){
                                                      s = new SerializationStream(username.length+password.length+displayName.length+description.length+20);
                                                      s.write(username);
                                                      s.write(password);
                                                      s.write(displayName);
                                                      s.write(navigationTimeout);
                                                      s.write(description);
                                                    }else{
                                                      forceDisconnect();
                                                      return true;
                                                    }
                                                  }
                                                  //Write admin credentials
                                                  wrapper.writeBytes(s.data, null, new Handler<Void>(){
                                                    public boolean onSuccess(Void v){
                                                      disableBlankSetup();
                                                      forceDisconnect();
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  final int ID = ClientConfig.ID;
                                                  if (ID<0){
                                                    final String name = ClientConfig.name;
                                                    final String desc = ClientConfig.description;
                                                    if (name==null || desc==null){
                                                      forceDisconnect();
                                                      return true;
                                                    }
                                                    //Write a message indicating this is a new server
                                                    wrapper.write(Protocol.NEW_SERVER, null, new Handler<Void>(){
                                                      public boolean onSuccess(Void v){
                                                        byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                                        byte[] descBytes = desc.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                                        SerializationStream s = new SerializationStream(nameBytes.length+descBytes.length+16);
                                                        s.write(connectionKey);
                                                        s.write(nameBytes);
                                                        s.write(descBytes);
                                                        //Write the name and description to configure this as a new server
                                                        wrapper.writeBytes(s.data, null, new Handler<Void>(){
                                                          public boolean onSuccess(Void v){
                                                            //Read a status message
                                                            wrapper.read(null, new Handler<Byte>(){
                                                              public boolean onSuccess(Byte b){
                                                                if (b.byteValue()!=Protocol.SUCCESS){
                                                                  forceDisconnect();
                                                                }else{
                                                                  //Read identification parameters
                                                                  wrapper.readBytes(1028, null, new Handler<byte[]>(){
                                                                    public boolean onSuccess(byte[] arr){
                                                                      try{
                                                                        SerializationStream s = new SerializationStream(arr);
                                                                        ClientConfig.ID = s.readInt();
                                                                        ClientConfig.identifier = s.readBytes();
                                                                        status = "Connected";
                                                                        cancelNextPing();
                                                                        Logger.logAsync("Connected to database.");
                                                                        enqueuePing(wrapper,0);
                                                                        return false;
                                                                      }catch(Throwable e){
                                                                        ClientConfig.ID = -1;
                                                                        forceDisconnect();
                                                                        return true;
                                                                      }
                                                                    }
                                                                  });
                                                                }
                                                                return true;
                                                              }
                                                            });
                                                            return true;
                                                          }
                                                        });
                                                        return true;
                                                      }
                                                    });
                                                  }else{
                                                    final byte[] identifier = ClientConfig.identifier;
                                                    if (identifier==null){
                                                      ClientConfig.ID = -1;
                                                      forceDisconnect();
                                                    }
                                                    //Write a message indicate this is an existing server
                                                    wrapper.write(Protocol.EXISTING_SERVER, null, new Handler<Void>(){
                                                      public boolean onSuccess(Void v){
                                                        SerializationStream s = new SerializationStream(identifier.length+8);
                                                        s.write(ID);
                                                        s.write(identifier);
                                                        //Write identifying information
                                                        wrapper.writeBytes(s.data, null, new Handler<Void>(){
                                                          public boolean onSuccess(Void v){
                                                            //Read a status message
                                                            wrapper.read(null, new Handler<Byte>(){
                                                              public boolean onSuccess(Byte b){
                                                                if (b.byteValue()==Protocol.SUCCESS){
                                                                  status = "Connected";
                                                                  cancelNextPing();
                                                                  Logger.logAsync("Connected to database.");
                                                                  enqueuePing(wrapper,0);
                                                                  return false;
                                                                }else{
                                                                  forceDisconnect();
                                                                  return true;
                                                                }
                                                              }
                                                            });
                                                            return true;
                                                          }
                                                        });
                                                        return true;
                                                      }
                                                    });
                                                  }
                                                }
                                                return true;
                                              }
                                            });
                                          }catch(Throwable e){
                                            forceDisconnect();
                                          }
                                          return true;
                                        }
                                      });
                                      return true;
                                    }
                                  });
                                }catch(Throwable e){
                                  forceDisconnect();
                                }
                                return true;
                              }
                            });
                            return true;
                          }
                        });
                      }
                      return true;
                    }
                  });
                }
              });
            }catch(Throwable e){
              connected = false;
              ch = null;
              wrap = null;
              if (grp!=null){
                try{
                  grp.shutdownNow();
                }catch(Throwable err){}
                grp = null;
              }
              if (logConnectionErrors){
                logConnectionErrors = false;
                Logger.log("Error connecting to database.", e);
              }
              status = "Disconnected with "+e.getClass().getSimpleName()+": "+e.getMessage();
              enqueueConnect(System.currentTimeMillis()+ClientConfig.reconnectTimeout);
            }
          }
        }
      });
    }
  }
  /** Triggers regularly scheduled uploads. */
  private static void enqueueUpload(){
    enqueue(new DelayedRunnable(System.currentTimeMillis()+60000){
      public void run(){
        if (connected){
          Uploads.trigger();
        }
        enqueueUpload();
      }
    });
  }
  /** Saves all data once every day */
  private static void enqueueBackup(){
    enqueue(new DelayedRunnable(ClientConfig.nextBackupTime()){
      public void run(){
        save();
        Logger.trim(ClientConfig.deleteLogAfter);
        enqueueBackup();
      }
    });
  }
  /** Saves all data */
  private static boolean save(){
    boolean ret = true;
    ret&=ClientConfig.save();
    ret&=Database.save();
    ret&=Uploads.save();
    if (ret){
      Logger.log("Database saved successfully.");
    }else{
      Logger.log("Database backup failure.");
    }
    return ret;
  }
  /**
   * Enqueue a ping operation to the primary queue.
   * This will cancel the outstanding ping operation if it exists and is scheduled to execute at a later time.
   */
  private static void enqueuePing(final SocketWrapper wrapper, long expiry){
    if (!stop){
      synchronized (nextPingLock){
        if (nextPing==null || expiry<nextPing.getExpiry()){
          if (nextPing!=null){
            nextPing.cancel();
          }
          nextPing = new Task(wrapper, expiry){
            public void run(){
              synchronized (nextPingLock){
                nextPing = null;
              }
              lockoutCounter = 0;
              lockoutExpiry = 0;
              wrapper.ping(null, new Handler<Void>(){
                public boolean onSuccess(Void v){
                  ping1(wrapper);
                  return true;
                }
              });
            }
          };
          enqueue(nextPing);
        }
      }
    }
  }
  private static void ping1(final SocketWrapper wrapper){
    if (!stop){
      wrapper.read(null, new Handler<Byte>(){
        public boolean onSuccess(Byte B){
          byte b = B.byteValue();
          if (b==Protocol.NO_FURTHER_INSTRUCTIONS){
            ping2(wrapper);
            //Each of the following protocols should invoke ping1(SocketWrapper) when asynchronous execution is finished
          }else if (b==Protocol.UPDATE_OPERATORS){
            wrapper.readBytes(64, null, new Handler<byte[]>(){
              public boolean onSuccess(byte[] data){
                SerializationStream s = new SerializationStream(data);
                int ID = s.readInt();
                long stamp = s.readLong();
                long cre = s.readLong();
                Operator op = Operators.get(ID);
                boolean gatherData = false;
                if (op==null){
                  if (stamp==-1L){
                    ping1(wrapper);
                  }else{
                    gatherData = true;
                  }
                }else if (stamp==-1L){
                  Operators.remove(op);
                  HelperAPI.logout(op);
                  Logger.logAsync("Operator "+op.getUsername()+" deleted.");
                  ping1(wrapper);
                }else if (op.getStamp()==stamp && op.getCreationTime()==cre){
                  wrapper.write((byte)0, null, new Handler<Void>(){
                    public boolean onSuccess(Void v){
                      ping1(wrapper);
                      return true;
                    }
                  });
                }else{
                  gatherData = true;
                }
                if (gatherData){
                  wrapper.write((byte)1, null, new Handler<Void>(){
                    public boolean onSuccess(Void v){
                      wrapper.readBytes(4096, null, new Handler<byte[]>(){
                        public boolean onSuccess(byte[] data){
                          try{
                            Operator op = Operator.deserialize(data);
                            Operators.update(op);
                            Logger.logAsync("Operator "+op.getUsername()+" updated.");
                          }catch(Throwable e){
                            Logger.logAsync("Error occurred while updating operator.", e);
                          }
                          ping1(wrapper);
                          return true;
                        }
                      });
                      return true;
                    }
                  });
                }
                return true;
              }
            });
          }else if (b==Protocol.UPDATE_PRESHARED_KEY){
            wrapper.readBytes(16384, null, new Handler<byte[]>(){
              public boolean onSuccess(byte[] data){
                try{
                  ClientConfig.databaseKey = Key.deserialize(new SerializationStream(data), false);
                  Logger.logAsync("Preshared key updated.");
                }catch(Throwable e){
                  Logger.logAsync("Error occurred in UPDATE_PRESHARED_KEY protocol.", e);
                }
                ping1(wrapper);
                return true;
              }
            });
          }else if (b==Protocol.UPDATE_PING_INTERVAL){
            wrapper.readBytes(16, null, new Handler<byte[]>(){
              public boolean onSuccess(byte[] data){
                long t = new SerializationStream(data).readLong();
                ClientConfig.pingInterval = t;
                Logger.logAsync("Ping interval set to "+t+" milliseconds.");
                ping1(wrapper);
                return true;
              }
            });
          }else if (b==Protocol.UPDATE_SERVER_PARAMS){
            wrapper.readBytes(16384, null, new Handler<byte[]>(){
              public boolean onSuccess(byte[] data){
                SerializationStream s = new SerializationStream(data);
                String newName = s.readString();
                String newDesc = s.readString();
                if (!ClientConfig.name.equals(newName)){
                  ClientConfig.name = newName;
                  Logger.logAsync("Server name changed to \""+ClientConfig.name+'"');
                }
                if (!ClientConfig.description.equals(newDesc)){
                  ClientConfig.description = newDesc;
                  Logger.logAsync("Server description changed to \""+ClientConfig.description+'"');
                }
                ping1(wrapper);
                return true;
              }
            });
          }else if (b==Protocol.SYNC_FILE){
            wrapper.readBytes(16384, null, new Handler<byte[]>(){
              public boolean onSuccess(byte[] data){
                boolean err = false;
                try{
                  Path dest = SyncTask.resolve(systemFolder, new String(data, java.nio.charset.StandardCharsets.UTF_8));
                  if (dest==null){
                    err = true;
                  }else{
                    final Path dst = dest.normalize();
                    final ArrayList<String> addons = new ArrayList<String>(8);
                    wrapper.write(Protocol.SUCCESS, null, new Handler<Void>(){
                      public boolean onSuccess(Void v){
                        wrapper.readPath(dst, null, new Handler<Boolean>(){
                          public boolean onSuccess(Boolean b){
                            for (String str:addons){
                              try{
                                HelperAPI.enableAddon(str);
                              }catch(Throwable t){
                                Logger.logAsync("Error occurred while enabling "+str+" addon.", t);
                              }
                            }
                            Logger.logAsync((b?"Completed":"Failed")+" SYNC_FILE - "+dst.toString());
                            ping1(wrapper);
                            return true;
                          }
                        }, dst.startsWith(addonsFolder)?new Consumer<Path>(){
                          public void accept(Path p){
                            try{
                              final String name = p.getFileName().toString();
                              final int len = name.length();
                              if (len>=6 && name.endsWith(".addon")){
                                String str = name.substring(0,len-6);
                                addons.add(str);
                                if (Files.isRegularFile(p)){
                                  HelperAPI.disableAddon(str);
                                }
                              }
                            }catch(Throwable t){
                              Logger.logAsync("Error occurred while checking SyncTask for addons.", t);
                            }
                          }
                        }:null, null);
                        return true;
                      }
                    });
                  }
                }catch(Throwable t){
                  Logger.logAsync("Error occurred in SYNC_FILE protocol.", t);
                  err = true;
                }
                if (err){
                  wrapper.write(Protocol.FAILURE, null, new Handler<Void>(){
                    public boolean onSuccess(Void v){
                      ping1(wrapper);
                      return true;
                    }
                  });
                }
                return true;
              }
            });
          }else if (b==Protocol.SYNC_OPERATORS){
            final ArrayList<Operator> list = new ArrayList<Operator>(Operators.count());
            Operators.forEach(new Predicate<Operator>(){
              public boolean test(Operator op){
                list.add(op);
                return true;
              }
            });
            SerializationStream s = new SerializationStream(list.size()*12);
            for (Operator op:list){
              s.write(op.getID());
              s.write(op.getCreationTime());
            }
            wrapper.writeBytes(s.data, null, new Handler<Void>(){
              public boolean onSuccess(Void v){
                wrapper.readBytes(16384, null, new Handler<byte[]>(){
                  public boolean onSuccess(byte[] data){
                    try{
                      SerializationStream s = new SerializationStream(data);
                      Operator op;
                      while (!s.end()){
                        op = Operators.get(s.readInt());
                        if (op!=null){
                          Operators.remove(op);
                          HelperAPI.logout(op);
                          Logger.logAsync("Operator "+op.getUsername()+" deleted.");
                        }
                      }
                    }catch(Throwable t){
                      Logger.logAsync("Error occurred in SYNC_OPERATORS protocol.", t);
                    }
                    ping1(wrapper);
                    return true;
                  }
                });
                return true;
              }
            });
          }else{
            forceDisconnect();
          }
          return true;
        }
      });
    }
  }
  private static void ping2(final SocketWrapper wrapper){
    if (!stop){
      RunnableProtocol r;
      synchronized (protocolQueue){
        r = protocolQueue.poll();
      }
      if (r==null){
        wrapper.write(Protocol.NO_FURTHER_INSTRUCTIONS,null,new Handler<Void>(){
          public boolean onSuccess(Void v){
            enqueuePing(wrapper, System.currentTimeMillis()+ClientConfig.pingInterval);
            return false;
          }
        });
      }else{
        wrapper.write(r.getProtocol(), null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            r.run(wrapper);
            return true;
          }
        });
      }
    }
  }
  /** Used in the ping-listen protocol method {@code ping2(SocketWrapper)} */
  private static void enqueue(RunnableProtocol r){
    synchronized (protocolQueue){
      protocolQueue.add(r);
    }
  }
  /** Enqueues a ping task which should run immediately */
  private static void pingNow(){
    enqueuePing(wrap,0);
  }
  /**
   * Records failed validation attempts while disconnected from the database.
   */
  private volatile static int lockoutCounter = 0;
  /**
   * Records the time when operator lockouts expire.
   * If 100 failed validation attempts occur while disconnected, all central operators are locked out for 5 minutes.
   */
  private volatile static long lockoutExpiry = 0;
  /**
   * Logs in the specified operator. When connected to the database, this method waits for the database to respond.
   * @param username is the operator to be logged in.
   * @param password is cleared after use for security reasons.
   * @return {@code Result<OperatorStatus>} that encapsulates the result of this asynchronous operation.
   */
  public static Result<OperatorStatus> login(final String username, final byte[] password){
    final Result<OperatorStatus> ret = new Result<OperatorStatus>();
    final OperatorStatus stat = new OperatorStatus();
    final boolean connected = Initializer.connected;
    stat.operator = Operators.get(username);
    if (stat.operator==null){
      Arrays.fill(password,(byte)0);
      stat.status = Protocol.DOES_NOT_EXIST;
      ret.setResult(stat);
    }else{
      if (!connected && System.currentTimeMillis()<lockoutExpiry){
        Arrays.fill(password,(byte)0);
        stat.status = Protocol.LOCKED_OUT;
        ret.setResult(stat);
      }else if (stat.operator.checkCredentials(username,password.clone())){
        if (connected){
          results.add(ret);
          enqueue(new RunnableProtocol(Protocol.LOGIN){
            public void run(final SocketWrapper wrapper){
              byte[] usernameBytes = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
              final SerializationStream s = new SerializationStream(usernameBytes.length+password.length+12);
              s.write(stat.operator.getID());
              s.write(usernameBytes);
              s.write(password);
              Arrays.fill(password,(byte)0);
              wrapper.writeBytes(s.data, null, new Handler<Void>(){
                public boolean onSuccess(Void v){
                  wrapper.read(null, new Handler<Byte>(){
                    public boolean onSuccess(Byte b){
                      results.remove(ret);
                      stat.status = b.byteValue();
                      if (stat.status==Protocol.DOES_NOT_EXIST){
                        Operators.remove(stat.operator);
                        HelperAPI.logout(stat.operator);
                      }else if (stat.status==Protocol.SUCCESS || stat.status==Protocol.CHANGE_PASSWORD){
                        Logger.logAsync("Operator "+stat.operator.getUsername()+" logged in.");
                      }
                      ret.setResult(stat);
                      ping2(wrapper);
                      return true;
                    }
                  });
                  return true;
                }
              });
            }
          });
          pingNow();
        }else{
          Arrays.fill(password,(byte)0);
          stat.status = Protocol.SUCCESS;
          Logger.logAsync("Operator "+stat.operator.getUsername()+" logged in locally.");
          ret.setResult(stat);
        }
      }else{
        if (!connected){
          lockoutCounter+=1;
          if (lockoutCounter>=100){
            lockoutExpiry = System.currentTimeMillis()+300000;
            lockoutCounter = 0;
          }
        }
        Arrays.fill(password,(byte)0);
        stat.status = Protocol.FAILURE;
        ret.setResult(stat);
      }
    }
    return ret;
  }
  /**
   * Logs in the specified operator. This method does not attempt to communicate with the database or enforce lockout counters.
   * @param username is the operator to be logged in.
   * @param password is cleared after use for security reasons.
   * @return {@code OperatorStatus} that encapsulates the result of this operation.
   */
  public static OperatorStatus loginLocal(final String username, final byte[] password){
    final OperatorStatus stat = new OperatorStatus();
    stat.operator = Operators.get(username);
    if (stat.operator==null){
      Arrays.fill(password,(byte)0);
      stat.status = Protocol.DOES_NOT_EXIST;
    }else if (stat.operator.checkCredentials(username,password)){
      stat.status = Protocol.SUCCESS;
      Logger.logAsync("Operator "+stat.operator.getUsername()+" logged in locally.");
    }else{
      stat.status = Protocol.FAILURE;
    }
    return stat;
  }
  /**
   * Logs out the specified operator.
   * @param ID identifies the operator to be logged out.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * </ul>
   */
  public static Result<Byte> logout(final int ID){
    final Result<Byte> ret = new Result<Byte>();
    if (connected){
      results.add(ret);
      enqueue(new RunnableProtocol(Protocol.LOGOUT){
        public void run(final SocketWrapper wrapper){
          SerializationStream s = new SerializationStream(4);
          s.write(ID);
          wrapper.writeBytes(s.data, null, new Handler<Void>(){
            public boolean onSuccess(Void v){
              wrapper.read(null, new Handler<Byte>(){
                public boolean onSuccess(Byte b){
                  results.remove(ret);
                  ret.setResult(b);
                  ping2(wrapper);
                  return true;
                }
              });
              return true;
            }
          });
        }
      });
      pingNow();
    }else{
      ret.setResult(Protocol.SUCCESS);
    }
    return ret;
  }
  /**
   * Creates a new operator with the given parameters.
   * @param authID identifies the operator which authenticates this operation.
   * @param username specifies the username for the new operator.
   * @param password specifies the password for the new operator (will be cleared for security reasons).
   * @param permissions specifies the permissions constant for the new operator.
   * @param displayName specifies the display name for the new operator.
   * @param navTimeout specifies the navigation timeout for the new operator.
   * @param desc specifies the description for the new operator.
   * @param forceChange specifies whether the operator should be forced to change their password on the next login.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> createOperator(final int authID, final String username, final byte[] password, final int permissions, final String displayName, final int navTimeout, final String desc, final boolean forceChange){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.CREATE_OPERATOR){
      public void run(final SocketWrapper wrapper){
        byte[] usernameBytes = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] displayNameBytes = displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] descBytes = desc.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SerializationStream s = new SerializationStream(usernameBytes.length+password.length+displayNameBytes.length+descBytes.length+29);
        s.write(authID);
        s.write(usernameBytes);
        s.write(password);
        s.write(permissions);
        s.write(displayNameBytes);
        s.write(navTimeout);
        s.write(descBytes);
        s.write(forceChange);
        Arrays.fill(password,(byte)0);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                if (b==Protocol.SUCCESS){
                  pingNow();
                }
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Modifies the specified operator.
   * @param authID identifies the operator which authenticates this operation.
   * @param modID identifies the operator to be modified.
   * @param modifications is a collection of {@code OperatorModification}s.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.PARTIAL_SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the operator being modified does not exist.</li>
   * </ul>
   */
  public static Result<Byte> modifyOperator(final int authID, final int modID, final Collection<OperatorModification> modifications){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.MODIFY_OPERATOR){
      public void run(final SocketWrapper wrapper){
        int len = 8;
        for (OperatorModification mod:modifications){
          len+=mod.length();
        }
        SerializationStream s = new SerializationStream(len);
        s.write(authID);
        s.write(modID);
        for (OperatorModification mod:modifications){
          mod.serialize(s);
        }
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                if (b==Protocol.SUCCESS || b==Protocol.PARTIAL_SUCCESS){
                  pingNow();
                }
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Deletes the specified operator.
   * @param authID identifies the operator which authenticates this operation.
   * @param modID identifies the operator to be deleted.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the operator being deleted does not exist.</li>
   * </ul>
   */
  public static Result<Byte> deleteOperator(final int authID, final int modID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.DELETE_OPERATOR){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(8);
        s.write(authID);
        s.write(modID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                if (b==Protocol.SUCCESS){
                  pingNow();
                }
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Modifies the specified server.
   * @param authID identifies the operator which authenticates this operation.
   * @param sID identifies the server to be modified.
   * @param modifications is a collection of {@code ServerModification}s.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.PARTIAL_SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the server being modified does not exist.</li>
   * </ul>
   */
  public static Result<Byte> modifyServer(final int authID, final int sID, final Collection<ServerModification> modifications){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.MODIFY_SERVER){
      public void run(final SocketWrapper wrapper){
        int len = 8;
        for (ServerModification mod:modifications){
          len+=mod.length();
        }
        SerializationStream s = new SerializationStream(len);
        s.write(authID);
        s.write(sID);
        for (ServerModification mod:modifications){
          mod.serialize(s);
        }
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                if (sID==ClientConfig.ID && b==Protocol.SUCCESS || b==Protocol.PARTIAL_SUCCESS){
                  pingNow();
                }
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Disconnects the specified server from the database.
   * @param authID identifies the operator which authenticates this operation.
   * @param sID identifies the server to be disconnected.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the server does not exist.</li>
   * <li>{@code Protocol.FAILURE} indicates the server is not currently connected.</li>
   * </ul>
   */
  public static Result<Byte> disconnectServer(final int authID, final int sID){
    final Result<Byte> ret = new Result<Byte>();
    if (sID==ClientConfig.ID){
      forceDisconnect();
      ret.setResult(Protocol.SUCCESS);
    }else{
      results.add(ret);
      enqueue(new RunnableProtocol(Protocol.DISCONNECT_SERVER){
        public void run(final SocketWrapper wrapper){
          SerializationStream s = new SerializationStream(8);
          s.write(authID);
          s.write(sID);
          wrapper.writeBytes(s.data, null, new Handler<Void>(){
            public boolean onSuccess(Void v){
              wrapper.read(null, new Handler<Byte>(){
                public boolean onSuccess(Byte b){
                  results.remove(ret);
                  ret.setResult(b);
                  ping2(wrapper);
                  return true;
                }
              });
              return true;
            }
          });
        }
      });
      if (connected){
        pingNow();
      }
    }
    return ret;
  }
  /**
   * Deletes the specified server.
   * @param authID identifies the operator which authenticates this operation.
   * @param sID identifies the server to be deleted.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the server being deleted does not exist.</li>
   * </ul>
   */
  public static Result<Byte> deleteServer(final int authID, final int sID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.DELETE_SERVER){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(8);
        s.write(authID);
        s.write(sID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Retrieves a list of all servers registered to the database.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<ServerList>} that encapsulates the result of this asynchronous operation.
   */
  public static Result<ServerList> getServerList(final int authID){
    final Result<ServerList> ret = new Result<ServerList>();
    final ServerList list = new ServerList();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.GET_SERVER_LIST){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(4);
        s.write(authID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                list.status = b.byteValue();
                if (list.status==Protocol.SUCCESS){
                  wrapper.readBytes(65536, null, new Handler<byte[]>(){
                    public boolean onSuccess(byte[] data){
                      results.remove(ret);
                      try{
                        list.populate(new SerializationStream(data));
                      }catch(Throwable e){
                        Logger.logAsync("Error occurred in GET_SERVER_LIST protocol.", e);
                        list.status = Protocol.FAILURE;
                      }
                      ret.setResult(list);
                      ping2(wrapper);
                      return true;
                    }
                  });
                }else{
                  results.remove(ret);
                  ret.setResult(list);
                  ping2(wrapper);
                }
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Gets a list of active operators for the given server.
   * @param authID identifies the operator which authenticates this operation.
   * @param sID identifies the server to retrieve active operators for.
   * @return {@code Result<OperatorList>} that encapsulates the result of this asynchronous operation.
   */
  public static Result<OperatorList> getActiveOperators(final int authID, final int sID){
    final Result<OperatorList> ret = new Result<OperatorList>();
    final OperatorList list = new OperatorList();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.GET_ACTIVE_OPERATORS){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(8);
        s.write(authID);
        s.write(sID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                list.status = b.byteValue();
                if (list.status==Protocol.SUCCESS){
                  wrapper.readBytes(16384, null, new Handler<byte[]>(){
                    public boolean onSuccess(byte[] data){
                      results.remove(ret);
                      try{
                        list.populate(new SerializationStream(data));
                      }catch(Throwable e){
                        list.status = Protocol.FAILURE;
                      }
                      ret.setResult(list);
                      ping2(wrapper);
                      return true;
                    }
                  });
                }else{
                  results.remove(ret);
                  ret.setResult(list);
                  ping2(wrapper);
                }
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Retrieves database configuration parameters populated in {@code Config.java}.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> getConfig(final int authID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.GET_CONFIG){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(4);
        s.write(authID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte B){
                byte b = B.byteValue();
                if (b==Protocol.SUCCESS){
                  wrapper.readBytes(2048, null, new Handler<byte[]>(){
                    public boolean onSuccess(byte[] data){
                      results.remove(ret);
                      try{
                        Config.deserialize(new SerializationStream(data));
                        ret.setResult(Protocol.SUCCESS);
                      }catch(Throwable e){
                        ret.setResult(Protocol.FAILURE);
                        Logger.logAsync("Error occurred in GET_CONFIG protocol.", e);
                      }
                      ping2(wrapper);
                      return true;
                    }
                  });
                }else{
                  results.remove(ret);
                  ret.setResult(b);
                  ping2(wrapper);
                }
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Sends configuration parameters as specified in {@code Config.java} to the database.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> configure(final int authID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.CONFIGURE){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(Config.LENGTH+4);
        s.write(authID);
        Config.serialize(s);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                if (b==Protocol.SUCCESS){
                  pingNow();
                }
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Request that the database generate a new preshared key.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> generatePresharedKey(final int authID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.GENERATE_PRESHARED_KEY){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(4);
        s.write(authID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                if (b==Protocol.SUCCESS){
                  pingNow();
                }
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Initiates a database restart.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> restartDatabase(final int authID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.RESTART_DATABASE){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(4);
        s.write(authID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Creates a new synchronization task.
   * @param authID identifies the operator which authenticates this operation.
   * @param description describes the task to be created.
   * @param syncInterval specifies in milliseconds how to to wait between scheduled triggers, or {@code -1} for manual trigger only.
   * @param src specifies the source path.
   * @param dst specifies the destination path.
   * @param allServers specifies whether this task should apply to all servers.
   * @return {@code Result<SyncStatus>} that encapsulates the result of this asynchronous operation.
   */
  public static Result<SyncStatus> createSyncTask(final int authID, final String description, final long syncInterval, final String src, final String dst, final boolean allServers){
    final Result<SyncStatus> ret = new Result<SyncStatus>();
    final SyncStatus stat = new SyncStatus();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.CREATE_SYNC){
      public void run(final SocketWrapper wrapper){
        byte[] a = description.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] c = dst.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SerializationStream s = new SerializationStream(a.length+b.length+c.length+25);
        s.write(authID);
        s.write(a);
        s.write(syncInterval);
        s.write(b);
        s.write(c);
        s.write(allServers);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                stat.status = b;
                if (b==Protocol.SUCCESS){
                  wrapper.readBytes(32, null, new Handler<byte[]>(){
                    public boolean onSuccess(byte[] data){
                      stat.ID = new SerializationStream(data).readInt();
                      results.remove(ret);
                      ret.setResult(stat);
                      ping2(wrapper);
                      return true;
                    }
                  });
                }else{
                  results.remove(ret);
                  ret.setResult(stat);
                  ping2(wrapper);
                }
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Deletes a synchronization task.
   * @param authID identifies the operator which authenticates this operation.
   * @param sID identifies the SyncTask to be deleted.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the specified SyncTask does not exist.</li>
   * </ul>
   */
  public static Result<Byte> deleteSyncTask(final int authID, final int sID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.DELETE_SYNC){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(8);
        s.write(authID);
        s.write(sID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Modifies a synchronization task.
   * @param authID identifies the operator which authenticates this operation.
   * @param task contains prepared information to send to the database.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicating there is no task whose ID matches.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> modifySyncTask(final int authID, final SyncTask task){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.MODIFY_SYNC){
      public void run(final SocketWrapper wrapper){
        byte[] bytes = task.serialize();
        SerializationStream s = new SerializationStream(bytes.length+4);
        s.write(authID);
        s.writeRaw(bytes);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Retrieves a list of all synchronization tasks.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<SyncList>} that encapsulates the result of this asynchronous operation.
   */
  public static Result<SyncList> getSyncTasks(final int authID){
    final Result<SyncList> ret = new Result<SyncList>();
    SyncList list = new SyncList();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.GET_SYNC_LIST){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(4);
        s.write(authID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                list.status = b;
                if (b==Protocol.SUCCESS){
                  wrapper.readBytes(16777216, null, new Handler<byte[]>(){
                    public boolean onSuccess(byte[] data){
                      results.remove(ret);
                      list.tasks = SyncTasks.deserialize(new SerializationStream(data));
                      ret.setResult(list);
                      ping2(wrapper);
                      return true;
                    }
                  });
                }else{
                  results.remove(ret);
                  ret.setResult(list);
                  ping2(wrapper);
                }
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Triggers a synchronization task.
   * @param authID identifies the operator which authenticates this operation.
   * @param sID identifies the SyncTask to be triggered.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * <li>{@code Protocol.DOES_NOT_EXIST} indicates the specified SyncTask does not exist.</li>
   * </ul>
   */
  public static Result<Byte> triggerSyncTask(final int authID, final int sID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.TRIGGER_SYNC){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(8);
        s.write(authID);
        s.write(sID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Triggers all synchronization tasks.
   * @param authID identifies the operator which authenticates this operation.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN} indiciates the authenticating operator is not logged in.</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS} indicates the authenticating operator does not have the required permissions.</li>
   * </ul>
   */
  public static Result<Byte> triggerSyncTasks(final int authID){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.TRIGGER_SYNC_ALL){
      public void run(final SocketWrapper wrapper){
        SerializationStream s = new SerializationStream(4);
        s.write(authID);
        wrapper.writeBytes(s.data, null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                results.remove(ret);
                ret.setResult(b);
                ping2(wrapper);
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
  /**
   * Uploads a file to the database.
   * @param src specifies the source file to upload.
   * @param dst specifies the destination path on the database.
   * @return {@code Result<Byte>} that encapsulates the result of this asynchronous operation.
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * </ul>
   */
  public static Result<Byte> uploadFile(final Path src, final String dst){
    final Result<Byte> ret = new Result<Byte>();
    results.add(ret);
    enqueue(new RunnableProtocol(Protocol.UPLOAD_FILE){
      public void run(final SocketWrapper wrapper){
        Logger.logAsync("Attempting UPLOAD_FILE - "+src.toString());
        wrapper.writeBytes(dst.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, new Handler<Void>(){
          public boolean onSuccess(Void v){
            wrapper.read(null, new Handler<Byte>(){
              public boolean onSuccess(Byte b){
                if (b==Protocol.SUCCESS){
                  wrapper.writePath(src, null, new Handler<Boolean>(){
                    public boolean onSuccess(Boolean b){
                      results.remove(ret);
                      ret.setResult(b?Protocol.SUCCESS:Protocol.FAILURE);
                      Logger.logAsync((b?"Completed":"Failed")+" UPLOAD_FILE - "+src.toString());
                      ping2(wrapper);
                      return true;
                    }
                  }, null, null);
                }else{
                  results.remove(ret);
                  ret.setResult(b);
                  Logger.logAsync("Failed UPLOAD_FILE - "+src.toString());
                  ping2(wrapper);
                }
                return true;
              }
            });
            return true;
          }
        });
      }
    });
    if (connected){
      pingNow();
    }
    return ret;
  }
}
abstract class RunnableProtocol {
  private byte protocol;
  public RunnableProtocol(byte protocol){
    this.protocol = protocol;
  }
  public byte getProtocol(){
    return protocol;
  }
  /** All implementations should invoke {@code Initializer.ping2(SocketWrapper)} when asynchronous execution is finished */
  public abstract void run(SocketWrapper wrapper);
}