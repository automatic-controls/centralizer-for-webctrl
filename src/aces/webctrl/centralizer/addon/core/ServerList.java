/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.*;
import java.util.*;
/**
 * Used for {@code Protocol.GET_SERVER_LIST}.
 */
public class ServerList {
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
   * The list of servers.
   */
  public volatile ArrayList<Server> servers = new ArrayList<Server>();
  /**
   * Populates the server list using information from the given {@code SerializationStream}.
   */
  public void populate(SerializationStream s) throws IndexOutOfBoundsException {
    while (!s.end()){
      servers.add(Server.deserialize(s.readBytes(), false));
    }
  }
}