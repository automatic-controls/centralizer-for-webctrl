package aces.webctrl.centralizer.common;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.concurrent.TimeUnit;
//TODO (Performance Improvement) - Make every file IO operation non-blocking
/**
 * Wraps an {@code AsynchronousSocketChannel} to provide automatic encrytion and decryption.
 */
public class SocketWrapper {
  /**
   * Controls the {@code timeout} and {@code pingInterval}.
   */
  public volatile static SocketWrapperConfig config;
  /**
   * Specifies the maximum block size to write/read variable length data.
   * The default value is 32 kilobytes.
   */
  private final static int blockSize = 32768;
  /**
   * Specifies the maximum block size to use for reading files into memory.
   * The default value is 256 kilobytes.
   */
  private final static int fileBlockSize = blockSize<<3;
  /**
   * Specifies how many attempts will be made to read or write data.
   */
  private final static int attempts = 3;
  /** The {@code StreamCipher} object used for symmetric encryption/decryption tasks */
  private volatile StreamCipher c = null;
  /** The wrapped socket. */
  private volatile AsynchronousSocketChannel socket;
  /** Keeps track of whether or not the underlying socket it closed. */
  private volatile boolean closed = false;
  /** Stores the IP address of the underlying socket. */
  private volatile String IP;
  /**
   * Initializes a {@code SocketWrapper} instance to wrap the given socket.
   */
  public SocketWrapper(AsynchronousSocketChannel socket){
    this.socket = socket;
    try{
      IP = socket.getRemoteAddress().toString();
    }catch(Exception e){
      IP = "Unknown";
    }
  }
  /**
   * @return the IP address of the underlying socket.
   */
  public String getIP(){
    return IP;
  }
  /**
   * Sets the {@code StreamCipher} object for this instance.
   */
  public void setCipher(StreamCipher c){
    this.c = c;
  }
  /**
   * @return whether or not the underlying socket is closed.
   */
  public boolean isClosed(){
    return closed;
  }
  /**
   * Releases resources and closes the underlying socket.
   * @return {@code true} on success; {@code false} if an error occurs while closing the socket.
   */
  public boolean close(){
    closed = true;
    c = null;
    try{
      socket.close();
      return true;
    }catch(IOException e){
      Logger.logAsync("Error occurred while closing socket.", e);
      return false;
    }
  }
  /**
   * Reads a file from the underlying socket.
   * Note that socket errors and file errors are handled differently.
   * If any socket error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * The {@code Boolean} passed to the {@code CompletionHandler} indicates whether or not the file was successfully transferred.
   * In particular, the result will be {@code false} if any file error occurs.
   * Data will be maintained in blocks of at most {@code fileBlockSize}, so the program won't run out of memory.
   * Data will be read from the socket in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * @param file is a path to the destination file which will store the retrieved data.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void readFile(final Path file, final T attach, final CompletionHandler<Boolean,T> func){
    /* Protocol:
      Read a message from the socket indicating whether the remote file-open was successful.
      Open a FileChannel for writing to the given file.
      Write a message to the socket indicating whether the file-open was successful.
      {
        Read a message from the socket indicating either EOF, FILE_ERROR, or CONTINUE.
        If EOF or FILE_ERROR, break the loop.
        If CONTINUE, read a data block from the socket.
        Write the previously read data block to the file.
        Write a message to the socket indicating whether the remote file-write was successful.
        Repeat.
      }
    */
    read(null, new CompletionHandler<Byte,Void>(){
      public void completed(Byte b, Void v){
        if (b.byteValue()==Protocol.CONTINUE){
          FileChannel ch = null;
          FileLock lock = null;
          try{
            ch = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            lock = ch.tryLock();
            if (lock==null){
              throw new Exception("Unable to acquire FileLock for \""+file.toString()+"\".");
            }
            ReadFile<T> req = new ReadFile<T>(attach, func, ch, file, lock);
            req.HEADER = new CompletionHandler<Void,Void>(){
              public void completed(Void v, Void vv){
                read(null, req.HEADER2);
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            };
            req.HEADER2 = new CompletionHandler<Byte,Void>(){
              public void completed(Byte B, Void vv){
                byte b = B.byteValue();
                if (b==Protocol.CONTINUE){
                  if (req.buf==null){
                    readBytes(fileBlockSize, null, req.READER);
                  }else{
                    readBytes(req.buf, null, req.READER2);
                  }
                }else if (b==Protocol.EOF){
                  req.success(true);
                }else{
                  req.success(false);
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            };
            req.READER = new CompletionHandler<byte[],Void>(){
              public void completed(byte[] arr, Void v){
                req.buf = arr;
                try{
                  if (req.ch.write(ByteBuffer.wrap(req.buf))==req.buf.length){
                    write(Protocol.CONTINUE, null, req.HEADER);
                  }else{
                    Logger.logAsync("Error occurred while writing data to file \""+file.toString()+"\".", new Exception("Failed to transfer all buffered bytes to the file."));
                    write(Protocol.FILE_ERROR, null, req.ERROR);
                  }
                }catch(Exception e){
                  Logger.logAsync("Error occurred while writing data to file \""+file.toString()+"\".", e);
                  write(Protocol.FILE_ERROR, null, req.ERROR);
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            };
            req.READER2 = new CompletionHandler<Integer,Void>(){
              public void completed(Integer X, Void v){
                try{
                  int x = X.intValue();
                  if (req.ch.write(ByteBuffer.wrap(req.buf, 0, x))==x){
                    write(Protocol.CONTINUE, null, req.HEADER);
                  }else{
                    Logger.logAsync("Error occurred while writing data to file \""+file.toString()+"\".", new Exception("Failed to transfer all buffered bytes to the file."));
                    write(Protocol.FILE_ERROR, null, req.ERROR);
                  }
                }catch(Exception e){
                  Logger.logAsync("Error occurred while writing data to file \""+file.toString()+"\".", e);
                  write(Protocol.FILE_ERROR, null, req.ERROR);
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            };
            req.ERROR = new CompletionHandler<Void,Void>(){
              public void completed(Void v, Void vv){
                req.success(false);
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            };
            write(Protocol.CONTINUE, null, req.HEADER);
          }catch(Exception e){
            if (ch==null){
              Logger.logAsync("Error occurred while opening file \""+file.toString()+"\" for writing.", e);
            }else{
              Logger.logAsync("Error occurred while reading file \""+file.toString()+"\" from socket.", e);
              if (lock!=null){
                try{
                  lock.release();
                }catch(Exception err){
                  Logger.logAsync("Error occurred while closing FileLock for \""+file.toString()+"\".", err);
                }
              }
              try{
                ch.close();
              }catch(Exception err){
                Logger.logAsync("Error occurred while closing FileChannel to \""+file.toString()+"\".", err);
              }
            }
            write(Protocol.FILE_ERROR, null, new CompletionHandler<Void,Void>(){
              public void completed(Void v, Void vv){
                func.completed(false,attach);
              }
              public void failed(Throwable e, Void vv){
                func.failed(e,attach);
                close();
              }
            });
          }
        }else{
          Logger.logAsync("Remote error occurred while reading file \""+file.toString()+"\" from socket.");
          func.completed(false, attach);
        }
      }
      public void failed(Throwable e, Void v){
        func.failed(e,attach);
        close();
      }
    });
  }
  private class ReadFile<T>{
    volatile CompletionHandler<Void,Void> HEADER;
    volatile CompletionHandler<Byte,Void> HEADER2;
    volatile CompletionHandler<byte[],Void> READER;
    volatile CompletionHandler<Integer,Void> READER2;
    volatile CompletionHandler<Void,Void> ERROR;
    volatile CompletionHandler<Boolean,T> func;
    volatile T attach;
    volatile FileChannel ch;
    volatile byte[] buf = null;
    volatile Path file;
    volatile FileLock lock;
    ReadFile(T attach, CompletionHandler<Boolean,T> func, FileChannel ch, Path file, FileLock lock){
      this.attach = attach;
      this.func = func;
      this.ch = ch;
      this.file = file;
      this.lock = lock;
    }
    void success(boolean transfer){
      buf = null;
      try{
        lock.release();
        ch.close();
      }catch(Exception err){
        Logger.logAsync("Error occurred while closing FileChannel to \""+file.toString()+"\".", err);
      }
      func.completed(transfer, attach);
    }
    void fail(Throwable e){
      buf = null;
      try{
        lock.release();
        ch.close();
      }catch(Exception err){
        Logger.logAsync("Error occurred while closing FileChannel to \""+file.toString()+"\".", err);
      }
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Writes a file to the underlying socket.
   * Note that socket errors and file errors are handled differently.
   * If any socket error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * The {@code Boolean} passed to the {@code CompletionHandler} indicates whether or not the file was successfully transferred.
   * In particular, the result will be {@code false} if any file error occurs.
   * Data will be read from the file in blocks of at most {@code fileBlockSize}, so the program won't run out of memory.
   * Data will be written to the socket in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * @param file is a path to the data source which will copied to the socket.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void writeFile(final Path file, final T attach, final CompletionHandler<Boolean,T> func){
    /* Protocol:
      Open a FileChannel for reading the given file.
      Write a message to the socket indicating whether the file-open was successful.
      Read a message from the socket indicating whether the remote file-open was successful.
      {
        Read a block of data from the file.
        Write a message to the socket indicating either EOF, FILE_ERROR, or CONTINUE.
        If EOF or FILE_ERROR, break the loop.
        If CONTINUE, write the previously read data block to the socket.
        Read a message from the socket indicating whether the remote file-write was successful.
        Repeat.
      }
    */
    FileChannel ch = null;
    try{
      ch = FileChannel.open(file, StandardOpenOption.READ);
      final WriteFile<T> req = new WriteFile<T>(attach, func, ch, file);
      req.RESPONSE = new CompletionHandler<Void,Void>(){
        public void completed(Void v, Void attach){
          read(null,req.HEADER);
        }
        public void failed(Throwable e, Void attach){
          req.fail(e);
        }
      };
      req.HEADER = new CompletionHandler<Byte,Void>(){
        public void completed(Byte b, Void attach){
          if (b.byteValue()==Protocol.CONTINUE){
            try{
              if (req.fileBuf==null){
                req.fileBuf = ByteBuffer.allocate((int)Math.min(fileBlockSize, req.ch.size()));
              }else{
                req.fileBuf.clear();
              }
              req.x = req.ch.read(req.fileBuf);
              if (req.x==-1){
                write(Protocol.EOF, null, req.EOF);
              }else{
                write(Protocol.CONTINUE, null, req.WRITER);
              }
            }catch(Exception e){
              Logger.logAsync("Error occurred while reading data from file \""+file.toString()+"\".", e);
              write(Protocol.FILE_ERROR, null, req.ERROR);
            }
          }else{
            Logger.logAsync("Remote error occurred while writing file \""+file.toString()+"\" to socket.");
            req.ERROR.completed(null,null);
          }
        }
        public void failed(Throwable e, Void attach){
          req.fail(e);
        }
      };
      req.WRITER = new CompletionHandler<Void,Void>(){
        public void completed(Void v, Void attach){
          writeBytes(req.fileBuf.array(), 0, req.x, null, req.RESPONSE);
        }
        public void failed(Throwable e, Void attach){
          req.fail(e);
        }
      };
      req.EOF = new CompletionHandler<Void,Void>(){
        public void completed(Void v, Void attach){
          req.success(true);
        }
        public void failed(Throwable e, Void attach){
          req.fail(e);
        }
      };
      req.ERROR = new CompletionHandler<Void,Void>(){
        public void completed(Void v, Void attach){
          req.success(false);
        }
        public void failed(Throwable e, Void attach){
          req.fail(e);
        }
      };
      write(Protocol.CONTINUE, null, req.RESPONSE);
    }catch(Exception e){
      if (ch==null){
        Logger.logAsync("Error occurred while opening file \""+file.toString()+"\" for reading.", e);
      }else{
        Logger.logAsync("Error occurred while writing file \""+file.toString()+"\" to socket.", e);
        try{
          ch.close();
        }catch(Exception err){
          Logger.logAsync("Error occurred while closing FileChannel to \""+file.toString()+"\".", err);
        }
      }
      write(Protocol.FILE_ERROR, null, new CompletionHandler<Void,Void>(){
        public void completed(Void v, Void vv){
          func.completed(false,attach);
        }
        public void failed(Throwable e, Void vv){
          func.failed(e,attach);
          close();
        }
      });
    }
  }
  private class WriteFile<T>{
    volatile CompletionHandler<Void,Void> RESPONSE;
    volatile CompletionHandler<Byte,Void> HEADER;
    volatile CompletionHandler<Void,Void> WRITER;
    volatile CompletionHandler<Void,Void> EOF;
    volatile CompletionHandler<Void,Void> ERROR;
    volatile CompletionHandler<Boolean,T> func;
    volatile T attach;
    volatile FileChannel ch;
    volatile ByteBuffer fileBuf = null;
    volatile int x;
    volatile Path file;
    WriteFile(T attach, CompletionHandler<Boolean,T> func, FileChannel ch, Path file){
      this.attach = attach;
      this.func = func;
      this.ch = ch;
      this.file = file;
    }
    void success(boolean transfer){
      fileBuf = null;
      try{
        ch.close();
      }catch(Exception err){
        Logger.logAsync("Error occurred while closing FileChannel to \""+file.toString()+"\".", err);
      }
      func.completed(transfer, attach);
    }
    void fail(Throwable e){
      fileBuf = null;
      try{
        ch.close();
      }catch(Exception err){
        Logger.logAsync("Error occurred while closing FileChannel to \""+file.toString()+"\".", err);
      }
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Reads a decrypts blocks of data from the underlying socket.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * Data will be read in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * The parameter passed to the {@code CompletionHandler} indicates how many bytes were read into the array.
   * @param data is the pre-allocated byte array used to store the retrieved data.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void readBytes(byte[] data, T attach, CompletionHandler<Integer,T> func){
    readBytes(data,0,attach,func);
  }
  /**
   * Reads a decrypts blocks of data from the underlying socket.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * Data will be read in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * The parameter passed to the {@code CompletionHandler} indicates how many bytes were read into the array.
   * @param data is the pre-allocated byte array used to store the retrieved data.
   * @param offset is the index of {@code data} to begin storing gathered bytes.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void readBytes(byte[] data, int offset, T attach, CompletionHandler<Integer,T> func){
    ReadBytesPreAlloc<T> req = new ReadBytesPreAlloc<T>(data, offset, attach, func);
    try{
      if (c==null){
        //No encryption or hashing
        readInternal(req);
      }else{
        /* Protocol:
          Read and decrypt the expected data packet length from the socket.
          Generate a hash.
          Read and decrypt a second hash from the socket.
          Compare the hashes to determine if the data transfer was successful.
          Encrypt and write a message to the socket indicating the result.
          Ensure the expected data packet length will fit into the pre-allocated data array.
          {
            Read and decrypt a data block from the socket.
            Generate a hash.
            Read and decrypt a second hash from the socket.
            Compare the hashes to determine if the data transfer was successful.
            Encrypt and write a message to the socket indicating the result.
            Repeat until all data blocks have been read.
          }
        */
        readInternal(req, false);
      }
    }catch(Exception e){
      req.fail(e);
    }
  }
  private <T> void readInternal(ReadBytesPreAlloc<T> req){
    if (req.pos>=req.end){
      req.success();
    }else{
      final boolean b = req.pos==-1;
      final byte[] arr = b?new byte[4]:req.data;
      final int offset = b?0:req.pos;
      final int length = b?arr.length:Math.min(blockSize, req.end-req.pos);
      req.pos = b?req.offset:req.pos+blockSize;
      socket.read(ByteBuffer.wrap(arr, offset, length), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==length){
            if (b){
              SerializationStream s = new SerializationStream(arr);
              req.length = s.readInt();
              req.end = req.length+req.offset;
              if (req.length<0 || req.end>req.data.length){
                req.fail("Stream size ("+req.length+") exceeded pre-defined limit ("+(req.data.length-req.offset)+").");
                return;
              }
            }
            readInternal(req);
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private <T> void readInternal(ReadBytesPreAlloc<T> req, boolean hashFailed){
    if (hashFailed && --req.attempts==0){
      req.fail("Hash comparision failure.");
    }else if (req.pos>=req.end){
      req.success();
    }else{
      final boolean b = req.pos==-1;
      final byte[] arr = b?new byte[4]:req.data;
      final int offset = b?0:req.pos;
      final int newPos = b?req.offset:req.pos+blockSize;
      final int length = b?arr.length:Math.min(blockSize, req.end-req.pos);
      if (hashFailed){
        c.reset();
      }else{
        req.attempts = attempts;
      }
      c.mark();
      socket.read(ByteBuffer.wrap(arr, offset, length), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==length){
            c.decrypt(arr,offset,offset+length);
            final byte[] hash = c.hash(4);
            final ByteBuffer buf = ByteBuffer.allocate(4);
            socket.read(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
              public void completed(Integer x, Void v){
                if (x.intValue()==4){
                  byte[] hashRead = buf.array();
                  c.decrypt(hashRead);
                  final boolean hashSuccess = java.util.Arrays.equals(hash,hashRead);
                  final byte[] ret = (hashSuccess?Protocol.HASH_COMPARISON_SUCCESS_ARRAY:Protocol.HASH_COMPARISON_FAILURE_ARRAY).clone();
                  c.encrypt(ret);
                  socket.write(ByteBuffer.wrap(ret), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
                    public void completed(Integer x, Void v){
                      if (x.intValue()==ret.length){
                        if (hashSuccess){
                          req.pos = newPos;
                          if (b){
                            SerializationStream s = new SerializationStream(arr);
                            req.length = s.readInt();
                            req.end = req.length+req.offset;
                            if (req.length<0 || req.end>req.data.length){
                              req.fail("Stream size ("+req.length+") exceeded pre-defined limit ("+(req.data.length-req.offset)+").");
                              return;
                            }
                          }
                          readInternal(req,false);
                        }else{
                          readInternal(req,true);
                        }
                      }else{
                        req.fail();
                      }
                    }
                    public void failed(Throwable e, Void v){
                      req.fail(e);
                    }
                  });
                }else{
                  req.fail();
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            });
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private class ReadBytesPreAlloc<T> {
    volatile CompletionHandler<Integer,T> func;
    volatile T attach;
    volatile int attempts = SocketWrapper.attempts;
    volatile byte[] data;
    volatile int pos = -1;
    volatile int offset;
    volatile int length;
    volatile int end = 0;
    ReadBytesPreAlloc(byte[] data, int offset, T attach, CompletionHandler<Integer,T> func){
      this.attach = attach;
      this.func = func;
      this.data = data;
      this.offset = offset;
    }
    void success(){
      func.completed(length, attach);
    }
    void fail(){
      fail("Connection closed unexpectedly.");
    }
    void fail(String message){
      fail(new Exception(message));
    }
    void fail(Throwable e){
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Reads and decrypts blocks of data from the underlying socket.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * Data will be read in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * @param limit Specifies a limit on the number of bytes which can be read. Malicious connections may try to crash the application by making it allocate too much memory.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void readBytes(int limit, T attach, CompletionHandler<byte[],T> func){
    ReadBytes<T> req = new ReadBytes<T>(limit, attach, func);
    try{
      if (c==null){
        //No encryption or hashing
        readInternal(req);
      }else{
        /* Protocol:
          Read and decrypt the expected data packet length from the socket.
          Generate a hash.
          Read and decrypt a second hash from the socket.
          Compare the hashes to determine if the data transfer was successful.
          Encrypt and write a message to the socket indicating the result.
          Ensure the expected data packet length is less than or equal to the pre-defined limit, and allocate an array.
          {
            Read and decrypt a data block from the socket.
            Generate a hash.
            Read and decrypt a second hash from the socket.
            Compare the hashes to determine if the data transfer was successful.
            Encrypt and write a message to the socket indicating the result.
            Repeat until all data blocks have been read.
          }
        */
        readInternal(req, false);
      }
    }catch(Exception e){
      req.fail(e);
    }
  }
  private <T> void readInternal(ReadBytes<T> req){
    if (req.data!=null && req.pos>=req.data.length){
      req.success();
    }else{
      final boolean b = req.pos==-1;
      final byte[] arr = b?new byte[4]:req.data;
      final int offset = b?0:req.pos;
      final int length = b?arr.length:Math.min(blockSize, req.data.length-req.pos);
      req.pos = b?0:req.pos+blockSize;
      socket.read(ByteBuffer.wrap(arr, offset, length), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==length){
            if (b){
              SerializationStream s = new SerializationStream(arr);
              int size = s.readInt();
              if (size>=0 && size<=req.limit){
                req.data = new byte[size];
              }else{
                req.fail("Stream size ("+size+") exceeded pre-defined limit ("+req.limit+").");
                return;
              }
            }
            readInternal(req);
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private <T> void readInternal(ReadBytes<T> req, boolean hashFailed){
    if (hashFailed && --req.attempts==0){
      req.fail("Hash comparision failure.");
    }else if (req.data!=null && req.pos>=req.data.length){
      req.success();
    }else{
      final boolean b = req.pos==-1;
      final byte[] arr = b?new byte[4]:req.data;
      final int offset = b?0:req.pos;
      final int newPos = b?0:req.pos+blockSize;
      final int length = b?arr.length:Math.min(blockSize, req.data.length-req.pos);
      if (hashFailed){
        c.reset();
      }else{
        req.attempts = attempts;
      }
      c.mark();
      socket.read(ByteBuffer.wrap(arr, offset, length), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==length){
            c.decrypt(arr,offset,offset+length);
            final byte[] hash = c.hash(4);
            final ByteBuffer buf = ByteBuffer.allocate(4);
            socket.read(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
              public void completed(Integer x, Void v){
                if (x.intValue()==4){
                  byte[] hashRead = buf.array();
                  c.decrypt(hashRead);
                  final boolean hashSuccess = java.util.Arrays.equals(hash,hashRead);
                  final byte[] ret = (hashSuccess?Protocol.HASH_COMPARISON_SUCCESS_ARRAY:Protocol.HASH_COMPARISON_FAILURE_ARRAY).clone();
                  c.encrypt(ret);
                  socket.write(ByteBuffer.wrap(ret), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
                    public void completed(Integer x, Void v){
                      if (x.intValue()==ret.length){
                        if (hashSuccess){
                          req.pos = newPos;
                          if (b){
                            SerializationStream s = new SerializationStream(arr);
                            int size = s.readInt();
                            if (size>=0 && size<=req.limit){
                              req.data = new byte[size];
                            }else{
                              req.fail("Stream size ("+size+") exceeded pre-defined limit ("+req.limit+").");
                              return;
                            }
                          }
                          readInternal(req,false);
                        }else{
                          readInternal(req,true);
                        }
                      }else{
                        req.fail();
                      }
                    }
                    public void failed(Throwable e, Void v){
                      req.fail(e);
                    }
                  });
                }else{
                  req.fail();
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            });
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private class ReadBytes<T> {
    volatile CompletionHandler<byte[],T> func;
    volatile T attach;
    volatile int attempts = SocketWrapper.attempts;
    volatile byte[] data = null;
    volatile int pos = -1;
    volatile int limit;
    ReadBytes(int limit, T attach, CompletionHandler<byte[],T> func){
      this.limit = limit;
      this.attach = attach;
      this.func = func;
    }
    void success(){
      func.completed(data, attach);
    }
    void fail(){
      fail("Connection closed unexpectedly.");
    }
    void fail(String message){
      fail(new Exception(message));
    }
    void fail(Throwable e){
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Encrypts and writes blocks of data to the underlying socket.
   * Parameter contents will be modified.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * Data will be written in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * @param data is the byte array to write to the underlying socket.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void writeBytes(byte[] data, T attach, CompletionHandler<Void,T> func){
    writeBytes(data, 0, data.length, attach, func);
  }
  /**
   * Encrypts and writes blocks of data to the underlying socket.
   * Parameter contents will be modified.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * Data will be written in blocks of at most {@code blockSize} bytes, so that {@code TimeoutException} does not occur.
   * @param data is the byte array to write to the underlying socket.
   * @param offset specifies the beginning index for where to start writing from the data array.
   * @param length specifies how many bytes to write from the data array.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void writeBytes(byte[] data, int offset, int length, T attach, CompletionHandler<Void,T> func){
    WriteBytes<T> req = new WriteBytes<T>(attach, func, data, offset, length);
    try{
      if (c==null){
        //No encryption or hashing
        writeInternal(req);
      }else{
        /* Protocol:
          Encrypt and write the expected data packet length to the socket.
          Generate, encrypt, and write a hash to the socket.
          Read and decrypt a message from the socket indicating whether the hash comparision was successful.
          {
            Select, encrypt, and write a block of data to the socket.
            Generate, encrypt, and write a hash to the socket.
            Read and decrypt a message from the socket indicating whether the hash comparision was successful.
            Repeat until all data blocks have been written.
          }
        */
        writeInternal(req, false);
      }
    }catch(Exception e){
      req.fail(e);
    }
  }
  private <T> void writeInternal(final WriteBytes<T> req){
    if (req.pos>=req.end){
      req.success();
    }else{
      final boolean b = req.pos==-1;
      final byte[] arr = b?req.header:req.data;
      final int offset = b?0:req.pos;
      final int length = b?req.header.length:Math.min(blockSize, req.end-req.pos);
      req.pos = b?req.offset:req.pos+blockSize;
      socket.write(ByteBuffer.wrap(arr, offset, length), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==length){
            writeInternal(req);
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private <T> void writeInternal(final WriteBytes<T> req, boolean hashFailed){
    if (hashFailed && --req.attempts==0){
      req.fail("Hash comparision failure.");
    }else if (req.pos>=req.end){
      req.success();
    }else{
      final boolean b = req.pos==-1;
      final byte[] arr = b?req.header:req.data;
      final int offset = b?0:req.pos;
      final int newPos = b?req.offset:req.pos+blockSize;
      final int length = b?req.header.length:Math.min(blockSize, req.end-req.pos);
      if (!hashFailed){
        req.attempts = attempts;
        c.encrypt(arr,offset,offset+length);
        req.hash = c.hash(4);
        c.encrypt(req.hash);
      }
      socket.write(ByteBuffer.wrap(arr, offset, length), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==length){
            socket.write(ByteBuffer.wrap(req.hash), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
              public void completed(Integer x, Void v){
                if (x.intValue()==req.hash.length){
                  final ByteBuffer buf = ByteBuffer.allocate(4);
                  socket.read(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
                    public void completed(Integer x, Void v){
                      if (x.intValue()==4){
                        byte[] arr = buf.array();
                        c.mark();
                        c.decrypt(arr);
                        if (java.util.Arrays.equals(arr, Protocol.HASH_COMPARISON_SUCCESS_ARRAY)){
                          req.pos = newPos;
                          writeInternal(req,false);
                        }else{
                          c.reset();
                          writeInternal(req,true);
                        }
                      }else{
                        req.fail();
                      }
                    }
                    public void failed(Throwable e, Void v){
                      req.fail(e);
                    }
                  });
                }else{
                  req.fail();
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            });
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private class WriteBytes<T> {
    volatile CompletionHandler<Void,T> func;
    volatile T attach;
    volatile int attempts = SocketWrapper.attempts;
    volatile byte[] data;
    volatile byte[] header;
    volatile byte[] hash;
    volatile int pos = -1;
    volatile int offset;
    volatile int end;
    WriteBytes(T attach, CompletionHandler<Void,T> func, byte[] data, int offset, int length){
      this.attach = attach;
      this.func = func;
      this.data = data;
      this.offset = offset;
      end = offset+length;
      SerializationStream s = new SerializationStream(4);
      s.write(length);
      header = s.data;
    }
    void success(){
      func.completed(null, attach);
    }
    void fail(){
      fail("Connection closed unexpectedly.");
    }
    void fail(String message){
      fail(new Exception(message));
    }
    void fail(Throwable e){
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Reads and decrypts a single byte from the underlying socket.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void read(final T attach, final CompletionHandler<Byte,T> func){
    if (c==null){
      //No encryption or hashing
      final ByteBuffer buf = ByteBuffer.allocate(1);
      socket.read(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==1){
            buf.flip();
            func.completed(buf.get(), attach);
          }else{
            func.failed(new Exception("Connection closed unexpectedly."), attach);
            close();
          }
        }
        public void failed(Throwable e, Void v){
          func.failed(e,attach);
          close();
        }
      });
    }else{
      /* Protocol:
        Read and decrypt a byte from the socket.
        Generate a hash.
        Read and decrypt a second hash from the socket.
        Compare the hashes to determine if the data transfer was successful.
        Write a message to the socket indicating the result.
      */
      ReadByte<T> req = new ReadByte<T>(attach,func);
      try{
        readInternal(req);
      }catch(Exception e){
        req.fail(e);
      }
    }
  }
  private <T> void readInternal(final ReadByte<T> req){
    if (--req.attempts==0){
      req.fail("Hash comparision failure.");
    }else{
      c.mark();
      final ByteBuffer buf = ByteBuffer.allocate(2);
      socket.read(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==2){
            buf.flip();
            final byte b = c.decrypt(buf.get());
            final boolean success = c.hash(1)[0]==c.decrypt(buf.get());
            ByteBuffer buf2 = ByteBuffer.allocate(1);
            buf2.put(c.encrypt(success?Protocol.HASH_COMPARISON_SUCCESS:Protocol.HASH_COMPARISON_FAILURE));
            buf2.flip();
            socket.write(buf2, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
              public void completed(Integer x, Void v){
                if (x.intValue()==1){
                  if (success){
                    req.success(b);
                  }else{
                    c.reset();
                    readInternal(req);
                  }
                }else{
                  req.fail();
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            });
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private class ReadByte<T> {
    volatile CompletionHandler<Byte,T> func;
    volatile T attach;
    volatile int attempts = SocketWrapper.attempts;
    ReadByte(T attach, CompletionHandler<Byte,T> func){
      this.attach = attach;
      this.func = func;
    }
    void success(byte b){
      func.completed(b, attach);
    }
    void fail(){
      fail("Connection closed unexpectedly.");
    }
    void fail(String message){
      fail(new Exception(message));
    }
    void fail(Throwable e){
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Encrypt and write a single byte to the underlying socket.
   * One extra byte will be written to encode a hash of the {@code StreamCipher}'s internal state.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * @param b is the byte to be written.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void write(byte b, final T attach, final CompletionHandler<Void,T> func){
    if (c==null){
      //No encryption or hashing
      socket.write(ByteBuffer.wrap(new byte[]{b}), config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==1){
            func.completed(null, attach);
          }else{
            func.failed(new Exception("Connection closed unexpectedly."), attach);
            close();
          }
        }
        public void failed(Throwable e, Void v){
          func.failed(e,attach);
          close();
        }
      });
    }else{
      /* Protocol:
        Encrypt the given byte.
        Write the encrypted byte to the socket.
        Generate and encrypt a hash.
        Write the encrypted hash to the socket.
        Read and decrypt the reply to determine if the hash comparision was successful.
      */
      WriteByte<T> req = new WriteByte<T>(attach, func, c.encrypt(b), c.encrypt(c.hash(1)[0]));
      try{
        writeInternal(req);
      }catch(Exception e){
        req.fail(e);
      }
    }
  }
  private <T> void writeInternal(final WriteByte<T> req){
    if (--req.attempts==0){
      req.fail("Hash comparision failure.");
    }else{
      ByteBuffer buf = ByteBuffer.allocate(2);
      buf.put(req.b);
      buf.put(req.hash);
      buf.flip();
      socket.write(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
        public void completed(Integer x, Void v){
          if (x.intValue()==2){
            final ByteBuffer buf = ByteBuffer.allocate(1);
            socket.read(buf, config.getTimeout(), TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer,Void>(){
              public void completed(Integer x, Void v){
                if (x.intValue()==1){
                  buf.flip();
                  c.mark();
                  if (c.decrypt(buf.get())==Protocol.HASH_COMPARISON_SUCCESS){
                    req.success();
                  }else{
                    c.reset();
                    writeInternal(req);
                  }
                }else{
                  req.fail();
                }
              }
              public void failed(Throwable e, Void v){
                req.fail(e);
              }
            });
          }else{
            req.fail();
          }
        }
        public void failed(Throwable e, Void v){
          req.fail(e);
        }
      });
    }
  }
  private class WriteByte<T> {
    volatile CompletionHandler<Void,T> func;
    volatile T attach;
    volatile int attempts = SocketWrapper.attempts;
    volatile byte b;
    volatile byte hash;
    WriteByte(T attach, CompletionHandler<Void,T> func, byte b, byte hash){
      this.attach = attach;
      this.func = func;
      this.b = b;
      this.hash = hash;
    }
    void success(){
      func.completed(null, attach);
    }
    void fail(){
      fail("Connection closed unexpectedly.");
    }
    void fail(String message){
      fail(new Exception(message));
    }
    void fail(Throwable e){
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Listeners for a ping message from the client with a timeout of {@code 2*config.getPingInterval()}.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void listen(T attach, CompletionHandler<Void,T> func){
    try{
      final ByteBuffer buf = ByteBuffer.allocate(1);
      socket.read(buf, config.getPingInterval()<<1, TimeUnit.MILLISECONDS, attach, new CompletionHandler<Integer,T>(){
        public void completed(Integer x, T attach){
          if (x.intValue()==1 && buf.array()[0]==Protocol.PING){
            func.completed(null,attach);
          }else{
            func.failed(new Exception("Connection closed unexpectedly."),attach);
            close();
          }
        }
        public void failed(Throwable e, T attach){
          func.failed(e,attach);
          close();
        }
      });
    }catch(Exception e){
      func.failed(e,attach);
      close();
    }
  }
  /**
   * Sends a ping message.
   * If an error is encountered, the {@code failed} method of the {@code CompletionHandler} will be invoked, and then the underlying socket will be closed.
   * @param attach is any object which the {@code CompletionHandler} should have access to.
   * @param func is the {@code CompletionHandler} invoked upon success or failure of this method.
   * @param <T> is the type of attached object.
   */
  public <T> void ping(T attach, CompletionHandler<Void,T> func){
    try{
      socket.write(ByteBuffer.wrap(new byte[]{Protocol.PING}), config.getTimeout(), TimeUnit.MILLISECONDS, attach, new CompletionHandler<Integer,T>(){
        public void completed(Integer x, T attach){
          if (x.intValue()==1){
            func.completed(null,attach);
          }else{
            func.failed(new Exception("Connection closed unexpectedly."),attach);
            close();
          }
        }
        public void failed(Throwable e, T attach){
          func.failed(e,attach);
          close();
        }
      });
    }catch(Exception e){
      func.failed(e,attach);
      close();
    }
  }
}