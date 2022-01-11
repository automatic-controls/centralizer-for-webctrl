package aces.webctrl.centralizer.common;
import java.nio.*;
/**
 * Provides tuning parameters to the {@code SocketWrapper} class.
 */
public abstract class SocketWrapperConfig {
  /** @return the timeout to wait for data packets. */
  public abstract long getTimeout();
  /** @return how often clients should ping the database */
  public abstract long getPingInterval();
  /**
   * Invoked whenever bytes are written to a socket.
   * May be used to capture all raw data packets being transmitted.
   * @param IP - is the IP address of the remote host.
   * @param buf - contains raw bytes read from the socket.
   */
  public void onWrite(String IP, ByteBuffer buf){
    if (PacketLogger.isWorking()){
      byte[] arr = new byte[buf.remaining()];
      buf.get(arr);
      PacketLogger.logAsync(IP, arr, true);
    }
  }
  /**
   * Invoked whenever bytes are read from a socket.
   * May be used to capture all raw data packets being received.
   * @param IP - is the IP address of the remote host.
   * @param buf - contains raw bytes read from the socket.
   */
  public void onRead(String IP, ByteBuffer buf){
    if (PacketLogger.isWorking()){
      byte[] arr = new byte[buf.remaining()];
      buf.get(arr);
      PacketLogger.logAsync(IP, arr, false);
    }
  }
}