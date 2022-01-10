package aces.webctrl.centralizer.database;
import aces.webctrl.centralizer.common.*;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/*
  Notes
    This application runs silently in the background

  Changes under consideration
    Send email alerts whenever the database is shutdown.
*/

/** Handles application initialization and termination */
public class Main {

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

  /** Application entry point */
  public static void main(String[] args){

    try{
      //Minimal initialization sequence to establish logging capabilities
      
      rootFolder = Paths.get(System.getenv("HomeDrive"), "WebCTRL Centralizer");
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
    }catch(Exception e){
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
      }catch(Exception err){
        err.printStackTrace();
      }
      System.exit(1);
    }
    
    try{
      //Initialization

      clientAcceptor = new CompletionHandler<AsynchronousSocketChannel,Void>(){
        @Override
        public void completed(AsynchronousSocketChannel client, Void v){
          try{
            server.accept(null, this);
            Connections.add(client);
          }catch(ShutdownChannelGroupException e){
            //Occurs when asyncGroup.shutdown() is called from another thread
            //Do nothing
          }catch(final Exception e){
            enqueue(new DelayedRunnable(0){
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
            enqueue(new DelayedRunnable(0){
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
      if (Database.init(rootFolder, true)){
        Logger.log("Initialization successful.");
      }else{
        Logger.log("Initialization failure.");
      }
      if (connect()){
        Logger.trim(Config.deleteLogAfter);
        enqueueSave();
        DelayedRunnable r;
        while (!asyncGroup.awaitTermination(1000L, TimeUnit.MILLISECONDS)){
          while ((r=queue.poll())!=null){
            r.run();
          }
        }
      }
    }catch(Exception e){
      Logger.log("Error occurred in main loop.", e);
    }
    gracefulExit(true);
  }
  public static void enqueue(DelayedRunnable d){
    queue.offer(d);
  }
  private static void enqueueSave(){
    enqueue(new DelayedRunnable(Config.nextBackupTime()){
      public void run(){
        save();
        Logger.trim(Config.deleteLogAfter);
        enqueueSave();
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
        Logger.log("Application terminated.");
        Logger.close();
        lockFileLock.release();
        lockFileChannel.close();
      }catch(Exception err){
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
        int threads = Runtime.getRuntime().availableProcessors();
        int port = Config.port;
        asyncGroup = AsynchronousChannelGroup.withFixedThreadPool(threads, Executors.defaultThreadFactory());
        server = AsynchronousServerSocketChannel.open(asyncGroup);
        server.bind(new InetSocketAddress(port), Config.backlog);
        server.accept(null, clientAcceptor);
        Logger.log("Database successfully bound to port "+port+" using a fixed thread pool of size "+threads+'.');
        return true;
      }catch(Exception e){
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
      }catch(Exception err){
        Logger.log("Error occurred while unbinding database.", err);
        return false;
      }
    }else{
      return false;
    }
  }
}