/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.common;
/**
 * Contains bit-masking constants used for determining an operator's permissions level.
 */
public class Permissions {
  /**
   * Grants an operator full administrative control over the database.
   * For example, this permission is required to change database configuration parameters.
   */
  public final static int ADMINISTRATOR = 0b1;
  /**
   * Grants an operator the ability to create, modify, and delete other operators.
   * Note operators cannot create other operators to have more permissions than they have.
   */
  public final static int OPERATOR_MANAGEMENT = 0b10;
  /**
   * Grants an operator the ability to manage file synchronization.
   */
  public final static int FILE_SYNCHRONIZATION = 0b100;
  /**
   * Grants an operator the ability to manage data retrieval.
   */
  public final static int FILE_RETRIEVAL = 0b1000;
  /**
   * Grants an operator the ability to manage script execution.
   */
  public final static int SCRIPT_EXECUTION = 0b10000;
  /**
   * Checks the permissions bit-mask is of the correct form.
   * If an operator is specified as an administator, then it automatically gets all other privileges.
   * @param b is the permissions integer.
   * @return the validated permissions integer.
   */
  protected static int validate(int b){
    return (b&ADMINISTRATOR)==0?b:-1;
  }
}