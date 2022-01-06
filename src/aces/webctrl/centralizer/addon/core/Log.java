package aces.webctrl.centralizer.addon.core;
/**
 * Used for {@code Protocol.GET_LOG}.
 */
public class Log {
  /**
   * Can be one of:
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN}</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS}</li>
   * </ul>
   */
  public volatile byte status;
  /**
   * The raw log data.
   */
  public volatile byte[] data;
}