/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
/**
 * Used for {@code Protocol.GET_SYNC_LIST}.
 */
public class SyncList {
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
   * The retrieved {@code SyncTask} list, or {@code null} if one could not be retrieved.
   */
  public volatile java.util.ArrayList<aces.webctrl.centralizer.common.SyncTask> tasks = null;
}