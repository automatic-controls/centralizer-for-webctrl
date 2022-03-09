/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.database;
import aces.webctrl.centralizer.common.*;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
//TODO (NEW FEATURE) - Send email alerts whenever Main.gracefulExit(boolean) is invoked
/** Handles application initialization and termination */
public class Main {

  /** Path to the installation folder (the .jar location) */
  private volatile static Path installation = null;
  /** Path to the WINSW executable */
  private volatile static String WINSW = null;
  /** Path to the XML configuration file for the Windows service */
  private volatile static String serviceXML = null;
  /** Path where files may be uploaded to. */
  private volatile static Path uploadFolder = null;
  /** Path where synchronized files may be stored. */
  private volatile static Path syncFolder = null;

  /** Where all files are stored for this database */
  private volatile static Path rootFolder = null;
  /** Used to ensure only one instance of this application may run at a given time */
  private volatile static Path lockFile = null;
  /** Used to ensure only one instance of this application may run at a given time */
  private volatile static FileChannel lockFileChannel = null;
  /** Used to ensure only one instance of this application may run at a given time */
  private volatile static FileLock lockFileLock = null;

  /** Essentially a group of worker threads which complete tasks on a shared queue */
  private volatile static AsynchronousChannelGroup asyncGroup = null;
  /** Server socket which is bound to {@link #port} */
  private volatile static AsynchronousServerSocketChannel server = null;
  /** Handles new client connections */
  private volatile static CompletionHandler<AsynchronousSocketChannel,Void> clientAcceptor = null;
  /** Status variable specifying whether {@link #server} is currently accepting connections */
  private final static AtomicBoolean running = new AtomicBoolean();
  /** Used to determine when a graceful exit has completed. */
  private final static AtomicBoolean exited = new AtomicBoolean();
  /** Task processing queue which permits the main thread to take some work (e.g. message logging and database backups) away from the server thread pool. */
  private final static DelayQueue<DelayedRunnable> queue = new DelayQueue<DelayedRunnable>();
  /** Specifies the number of threads to use for asynchronous processing. */
  private final static int threads = Runtime.getRuntime().availableProcessors();

  /** Application entry point */
  public static void main(String[] args){

    try{
      //Minimal initialization sequence to establish logging capabilities
      
      installation = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().normalize();
      rootFolder = installation.resolve("data");
      lockFile = rootFolder.resolve("lock");
      if (!Files.exists(rootFolder)){
        Files.createDirectory(rootFolder);
      }
      lockFileChannel = FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      lockFileLock = lockFileChannel.tryLock();
      if (lockFileLock==null){
        System.err.println("The application could not be started because it is already running.");
        lockFileChannel.close();
        System.exit(1);
      }
      Logger.init(rootFolder.resolve("log.txt"), new java.util.function.Consumer<DelayedRunnable>(){
        public void accept(DelayedRunnable r){
          enqueue(r);
        }
      });
      Logger.log(Config.NAME+" initializing...");
    }catch(Throwable e){
      try{
        e.printStackTrace();
        if (Logger.isInitialized()){
          Logger.close();
        }
        if (lockFileLock!=null){
          lockFileLock.release();
        }
        if (lockFileChannel!=null && lockFileChannel.isOpen()){
          lockFileChannel.close();
        }
      }catch(Throwable err){
        err.printStackTrace();
      }
      System.exit(1);
    }
    
    try{
      //Initialization

      WINSW = '"'+installation.resolve("winsw.exe").toString()+'"';
      serviceXML = '"'+installation.resolve("service.xml").toString()+'"';
      uploadFolder = installation.resolve("uploads").normalize();
      try{
        if (!Files.exists(uploadFolder)){
          Files.createDirectory(uploadFolder);
        }
      }catch(Throwable t){
        Logger.log("Error occurred while creating folder: "+uploadFolder.toString(), t);
      }
      syncFolder = installation.resolve("sync").normalize();
      try{
        if (!Files.exists(syncFolder)){
          Files.createDirectory(syncFolder);
        }
      }catch(Throwable t){
        Logger.log("Error occurred while creating folder: "+syncFolder.toString(), t);
      }

      clientAcceptor = new CompletionHandler<AsynchronousSocketChannel,Void>(){
        @Override
        public void completed(AsynchronousSocketChannel client, Void v){
          try{
            server.accept(null, this);
            Connections.add(client);
          }catch(ShutdownChannelGroupException e){
            //Occurs when asyncGroup.shutdown() is called from another thread
            //Do nothing
          }catch(final Throwable e){
            enqueue(new DelayedRunnable(System.currentTimeMillis()){
              public void run(){
                Logger.log("Connection acceptor encountered error.", e);
                gracefulExit(true);
              }
            });
          }
        }
        @Override
        public void failed(final Throwable e, Void v){
          if (!(e instanceof AsynchronousCloseException)){
            enqueue(new DelayedRunnable(System.currentTimeMillis()){
              public void run(){
                Logger.log("Failed to accept connection.", e);
                gracefulExit(true);
              }
            });
          }
        }
      };
      Runtime.getRuntime().addShutdownHook(new Thread(){
        public void run(){
          gracefulExit(false);
        }
      });
      Database.exec = Executors.newFixedThreadPool(Math.min(2, threads>>1));
      if (Database.init(rootFolder, true)){
        Logger.log("Initialization successful.");
      }else{
        Logger.log("Initialization failure.");
      }
      if (connect()){
        Logger.trim(Config.deleteLogAfter);
        enqueueSave();
        enqueueSync(0);
        DelayedRunnable r;
        while (!asyncGroup.awaitTermination(1000L, TimeUnit.MILLISECONDS)){
          while ((r=queue.poll())!=null){
            r.run();
          }
        }
      }
    }catch(Throwable e){
      Logger.log("Error occurred in main loop.", e);
    }
    gracefulExit(true);
  }
  public static void enqueue(DelayedRunnable d){
    queue.offer(d);
  }
  private static void enqueueSync(long when){
    enqueue(new DelayedRunnable(when){
      public void run(){
        try{
          final long time = System.currentTimeMillis();
          SyncTasks.forEach(new Predicate<SyncTask>(){
            public boolean test(SyncTask t){
              long x = t.getNextTriggerTime();
              if (x>=0 && x<=time){
                triggerSyncTask(t);
              }
              return false;
            }
          });
        }catch(Throwable t){
          Logger.log("Error occurred while checking synchronization tasks.", t);
        }finally{
          enqueueSync(System.currentTimeMillis()+Config.syncCheckInterval);
        }
      }
    });
  }
  private static void enqueueSave(){
    enqueue(new DelayedRunnable(Config.nextBackupTime()){
      public void run(){
        try{
          save();
          Logger.trim(Config.deleteLogAfter);
        }catch(Throwable t){
          Logger.log("Error occurred while backing up database.", t);
        }finally{
          enqueueSave();
        }
      }
    });
  }
  private static void save(){
    if (Database.save()){
      Logger.log("Database saved successfully.");
    }else{
      Logger.log("Database backup failure.");
    }
  }
  private static void gracefulExit(boolean exit){
    if (exited.compareAndSet(false,true)){
      try{
        disconnect();
        save();
        DelayedRunnable r;
        while ((r=queue.poll())!=null){
          r.run();
        }
        PacketLogger.stop();
        Logger.log("Application terminated.");
        Logger.close();
        lockFileLock.release();
        lockFileChannel.close();
      }catch(Throwable err){
        err.printStackTrace();
      }
      if (exit){
        System.exit(0);
      }
    }
  }
  /**
   * Attempts to bind the server to {@link #port} on the local machine.
   * @return {@code true} on success; {@code false} if an error has occurred.
   */
  private static boolean connect(){
    if (running.compareAndSet(false,true)){
      try{
        int port = Config.port;
        asyncGroup = AsynchronousChannelGroup.withFixedThreadPool(threads, Executors.defaultThreadFactory());
        server = AsynchronousServerSocketChannel.open(asyncGroup);
        server.bind(new InetSocketAddress(port), Config.backlog);
        server.accept(null, clientAcceptor);
        Logger.log("Database successfully bound to port "+port+" using a fixed thread pool of size "+threads+'.');
        return true;
      }catch(Throwable e){
        running.set(false);
        Logger.log("Database bind error.", e);
        return false;
      }
    }else{
      return false;
    }
  }
  /**
   * Attempts to terminate the {@link #asyncGroup} and close the {@link #server}.
   * Blocks execution until operation has completed.
   * @return {@code true} on success; {@code false} if an error has occurred.
   */
  private static boolean disconnect(){
    if (running.compareAndSet(true,false)){
      try{
        asyncGroup.shutdownNow();
        Logger.log("Database successfully unbound.");
        return true;
      }catch(Throwable err){
        Logger.log("Error occurred while unbinding database.", err);
        return false;
      }
    }else{
      return false;
    }
  }
  /**
   * Attempts to restart the database service.
   * @return {@code true} on success; {@code false} if an error has occurred.
   */
  public static boolean restart(){
    try{
      Runtime.getRuntime().exec("cmd /c start \"\" "+WINSW+" restart "+serviceXML);
      return true;
    }catch(Throwable e){
      Logger.logAsync("Error occurred during attempted system restart.", e);
      return false;
    }
  }
  /**
   * Trigger the given synchronization task.
   */
  public static void triggerSyncTask(final SyncTask t){
    t.resetTrigger();
    final Path source = SyncTask.resolve(Main.getSyncs(),t.getSource());
    final String dst = t.getDestination();
    if (source!=null){
      final Path src = source.normalize();
      Logger.logAsync("SYNC_FILE - "+src.toString());
      Connections.forEach(new Predicate<Connection>(){
        public boolean test(Connection c){
          Server s = c.server;
          if (s!=null && t.appliesTo(s.getID())){
            c.syncFile(src, dst);
          }
          return true;
        }
      });
    }
  }
  /**
   * Trigger all synchronization tasks.
   */
  public static void triggerSyncTasks(){
    SyncTasks.forEach(new Predicate<SyncTask>(){
      public boolean test(SyncTask t){
        triggerSyncTask(t);
        return false;
      }
    });
  }
  /**
   * @return the installation folder for this application.
   */
  public static Path getInstallation(){
    return installation;
  }
  /**
   * @return the folder that all uploaded files are sent to by default.
   */
  public static Path getUploads(){
    return uploadFolder;
  }
  /**
   * @return the default folder for containing synchronized files.
   */
  public static Path getSyncs(){
    return syncFolder;
  }
}