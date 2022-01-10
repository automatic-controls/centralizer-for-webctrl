package aces.webctrl.centralizer.common;
/**
 * Provides tuning parameters to the {@code SocketWrapper} class.
 */
public interface SocketWrapperConfig {
  public long getTimeout();
  public long getPingInterval();
}