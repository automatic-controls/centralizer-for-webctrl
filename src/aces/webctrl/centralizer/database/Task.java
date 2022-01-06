package aces.webctrl.centralizer.database;
public abstract class Task {
  private volatile byte type;
  public Task(byte type){
    this.type = type;
  }
  public byte getType(){
    return type;
  }
  /**
   * All implementations should invoke {@code listen2()} on the corresponding Connection object when the asynchronous task is complete.
   */
  public abstract void run();
}