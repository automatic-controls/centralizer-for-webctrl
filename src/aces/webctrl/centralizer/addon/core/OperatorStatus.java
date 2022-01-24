/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.Operator;
/**
 * Used for {@code Protocol.LOGIN}.
 */
public class OperatorStatus {
  /**
   * Can be one of:
   * <ul>
   * <li>{@code Protocol.DOES_NOT_EXIST}</li>
   * <li>{@code Protocol.LOCKED_OUT}</li>
   * <li>{@code Protocol.CHANGE_PASSWORD}</li>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * </ul>
   */
  public volatile byte status;
  /**
   * The logged in operator.
   */
  public volatile Operator operator;
}