/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
/**
 * Used for {@code Protocol.CREATE_SYNC}.
 */
public class SyncStatus {
  /**
   * Can be one of:
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN}</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS}</li>
   * </ul>
   */
  public volatile byte status;
  /**
   * The ID for the newly created {@code SyncTask}, or {@code -1} if one could not be created.
   */
  public volatile int ID = -1;
}