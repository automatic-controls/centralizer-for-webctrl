package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.*;
import java.util.*;
/**
 * Used for {@code Protocol.GET_ACTIVE_OPERATORS}.
 */
public class OperatorList {
  /**
   * Can be one of:
   * <ul>
   * <li>{@code Protocol.SUCCESS}</li>
   * <li>{@code Protocol.FAILURE}</li>
   * <li>{@code Protocol.NOT_LOGGED_IN}</li>
   * <li>{@code Protocol.INSUFFICIENT_PERMISSIONS}</li>
   * <li>{@code Protocol.DOES_NOT_EXIST}</li>
   * </ul>
   */
  public volatile byte status;
  /**
   * Indicates how many operators are active.
   * Note this field is more reliable than {@code operators.length} because there may be an unlikely circumstance where the central and local operators are out-of-sync,
   * in which case certain operators may fail to be represented.
   */
  public volatile int length = 0;
  /**
   * The list of operators.
   */
  public volatile ArrayList<Operator> operators = new ArrayList<Operator>();
  /**
   * Populates the operator list using information from the given {@code SerializationStream}.
   */
  public void populate(SerializationStream s) throws IndexOutOfBoundsException {
    length = s.readInt();
    operators.ensureCapacity(length);
    Operator op;
    while (!s.end()){
      op = Operators.get(s.readInt());
      if (op!=null){
        operators.add(op);
      }
    }
  }
}