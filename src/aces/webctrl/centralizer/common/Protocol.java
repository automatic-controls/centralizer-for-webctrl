/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
/**
 * This class contains byte constants used to transfer data.
 */
public class Protocol {

  /** Indicates the database is a blank install. */
  public final static byte BLANK_INSTALL = -1;
  /** Indicates the database is already configured. */
  public final static byte ALREADY_CONFIGURED = 0;
  /** Indicates a new server is attempting to connect. */
  public final static byte NEW_SERVER = 1;
  /** Indicates an existing server is attempting to connect. */
  public final static byte EXISTING_SERVER = 2;
  /** Indicates any other entity that would like to connect to the database. */
  public final static byte UNSPECIFIED_SERVER = 3;

  /** Used for automatically controlling the value of byte constants. */
  private static byte ID1 = 0;
  /** Used for automatically controlling the value of byte constants. */
  private static byte ID2 = 0;
  /** Used for automatically controlling the value of byte constants. */
  private static byte ID3 = 0;


  //This section generally contains codes which clients send to the database to initiate various protocols.
  /**
   * Clients periodically send this byte to the database to indicate an active connection and to request further instruction.
   */
  public final static byte PING = ++ID1;
  /**
   * Indicates the client would like to login using operator credentials.
   * If the credentials are valid, the database will accept any command from the client authenticated using this operator's ID.
   * Each user will be automatically logged out after a certain timeout.
   * Alternatively, the client can send a manual logout command.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the operator to be logged in</li>
   * <li>Username</li>
   * <li>Password</li>
   * </ul>
   */
  public final static byte LOGIN = ++ID1;
  /**
   * Indicates the client would like to logout.
   * The server will no longer accept commands from the client authenticated using this operator's ID.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the operator to be logged out</li>
   * </ul>
   */
  public final static byte LOGOUT = ++ID1;
  /**
   * Indicates the client would like to create an operator.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.OPERATORS})</li>
   * <li>Username</li>
   * <li>Password</li>
   * <li>Permissions</li>
   * <li>Display Name</li>
   * <li>Navigation Timeout</li>
   * <li>Description</li>
   * <li>Whether to force password change on next login</li>
   * </ul>
   */
  public final static byte CREATE_OPERATOR = ++ID1;
  /**
   * Indicates the client would like to modify an operator.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.OPERATORS})</li>
   * <li>ID of the operator to modify</li>
   * <li>List of properties to modify and their new values</li>
   * </ul>
   */
  public final static byte MODIFY_OPERATOR = ++ID1;
  /**
   * Indicates the client would like to delete an operator.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.OPERATORS})</li>
   * <li>ID of the operator to delete</li>
   * </ul>
   */
  public final static byte DELETE_OPERATOR = ++ID1;
  /**
   * Indicates the client would like to modify a server.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * <li>ID of the server to modify</li>
   * <li>List of properties to modify and their new values</li>
   * </ul>
   */
  public final static byte MODIFY_SERVER = ++ID1;
  /**
   * Indicates the client would like to delete a server.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * <li>ID of the server to delete</li>
   * </ul>
   */
  public final static byte DELETE_SERVER = ++ID1;
  /**
   * Indicates the client would like to disconnect a server.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * <li>ID of the server to disconnect</li>
   * </ul>
   */
  public final static byte DISCONNECT_SERVER = ++ID1;
  /**
   * Indicates the client would like to retrieve a list of all servers and whether each server is currently connected.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * </ul>
   */
  public final static byte GET_SERVER_LIST = ++ID1;
  /**
   * Indicates the client would like to retrieve a list of all currently logged in operators for the specified server.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * <li>ID of the server to get an operator list for</li>
   * </ul>
   */
  public final static byte GET_ACTIVE_OPERATORS = ++ID1;
  /**
   * Indicates the client would like to receive a copy of the database configuration properties.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * </ul>
   */
  public final static byte GET_CONFIG = ++ID1;
  /**
   * Indicates the client would like to modify the database configuration properties.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * <li>List of properties to modify and their values</li>
   * </ul>
   */
  public final static byte CONFIGURE = ++ID1;
  /**
   * Indicates the client would like the server to generate a new pre-shared key.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * </ul>
   */
  public final static byte GENERATE_PRESHARED_KEY = ++ID1;
  /**
   * Indicates the client would like the server to restart.
   * <p>Additional Parameters:
   * <ul>
   * <li>ID of the authenticating operator (requires {@code Permissions.ADMINISTRATOR})</li>
   * </ul>
   */
  public final static byte RESTART_DATABASE = ++ID1;




  //This sections generally contains codes which the database sends to clients to initiate various update protocols.
  /**
   * Indicates termination of the current data transfer.
   */
  public final static byte NO_FURTHER_INSTRUCTIONS = ++ID2;
  /**
   * Indicates the pre-shared key should be updated.
   */
  public final static byte UPDATE_PRESHARED_KEY = ++ID2;
  /**
   * Indicates the ping interval should be updated.
   */
  public final static byte UPDATE_PING_INTERVAL = ++ID2;
  /**
   * Indicates that one or more operators should be updated.
   */
  public final static byte UPDATE_OPERATORS = ++ID2;
  /**
   * Indicates that the server name and/or description should be updated.
   */
  public final static byte UPDATE_SERVER_PARAMS = ++ID2;



  
  //This sections generally contains codes which indicate the status of a requested operation. These are typically sent from the database to the client, but there are exceptions.
  /**
   * Indicates the hash comparison was successful.
   * Used to prevent data corruption (random or otherwise).
   */
  public final static byte[] HASH_COMPARISON_SUCCESS_ARRAY = new byte[]{32, 2, -83, -106};
  /**
   * Indicates {@link StreamCipher#hash(int)} failed to match the expected result.
   * Possible causes include random corruption or an attempt to initiate a man-in-the-middle attack.
   * Note random data corruption is unlikely since the TCP/IP protocol is generally reliable.
   */
  public final static byte[] HASH_COMPARISON_FAILURE_ARRAY = new byte[]{34, -103, -107, 44};
  /**
   * Indicates the hash comparison was successful.
   * Used to prevent data corruption (random or otherwise).
   */
  public final static byte HASH_COMPARISON_SUCCESS = ++ID3;
  /**
   * Indicates {@link StreamCipher#hash(int)} failed to match the expected result.
   * Possible causes include random corruption or an attempt to initiate a man-in-the-middle attack.
   * Note random data corruption is unlikely since the TCP/IP protocol is generally reliable.
   */
  public final static byte HASH_COMPARISON_FAILURE = ++ID3;
  /**
   * Indicates success.
   */
  public final static byte SUCCESS = ++ID3;
  /**
   * Indicates partial success.
   */
  public final static byte PARTIAL_SUCCESS = ++ID3;
  /**
   * Indicates failure.
   */
  public final static byte FAILURE = ++ID3;
  /**
   * Indicates the end-of-file has been reached.
   */
  public final static byte EOF = ++ID3;
  /**
   * Indicates there are more bytes to be send.
   */
  public final static byte CONTINUE = ++ID3;
  /**
   * Indicates there was an error reading or writing to a local file.
   */
  public final static byte FILE_ERROR = ++ID3;
  /**
   * Indicates a resource does not exist.
   */
  public final static byte DOES_NOT_EXIST = ++ID3;
  /**
   * Indicates an operator is locked out and cannot be logged in.
   */
  public final static byte LOCKED_OUT = ++ID3;
  /**
   * Indicates an operator should change its password.
   */
  public final static byte CHANGE_PASSWORD = ++ID3;
  /**
   * Indicates the authenticating operator is not currently logged in.
   */
  public final static byte NOT_LOGGED_IN = ++ID3;
  /**
   * Indicates the client does not have sufficient permissions to initiate the previously requested operation.
   */
  public final static byte INSUFFICIENT_PERMISSIONS = ++ID3;
}