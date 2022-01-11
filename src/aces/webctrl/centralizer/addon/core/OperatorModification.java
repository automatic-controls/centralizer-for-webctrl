package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.SerializationStream;
public abstract class OperatorModification {
  private volatile int len;
  private OperatorModification(int len){ this.len = len; }
  /** Used to precompute serialization length */
  public int length(){
    return len;
  }
  public abstract void serialize(SerializationStream s);
  public static OperatorModification changeUsername(String username){
    final byte[] usernameBytes = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return new OperatorModification(usernameBytes.length+5){
      public void serialize(SerializationStream s){
        s.write((byte)0);
        s.write(usernameBytes);
      }
    };
  }
  public static OperatorModification changePassword(final byte[] password){
    return new OperatorModification(password.length+5){
      public void serialize(SerializationStream s){
        s.write((byte)1);
        s.write(password);
        java.util.Arrays.fill(password,(byte)0);
      }
    };
  }
  public static OperatorModification changeDisplayName(String displayName){
    final byte[] displayNameBytes = displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return new OperatorModification(displayNameBytes.length+5){
      public void serialize(SerializationStream s){
        s.write((byte)2);
        s.write(displayNameBytes);
      }
    };
  }
  public static OperatorModification changeNavigationTimeout(final int navTimeout){
    return new OperatorModification(5){
      public void serialize(SerializationStream s){
        s.write((byte)3);
        s.write(navTimeout);
      }
    };
  }
  public static OperatorModification changePermissions(final int permissions){
    return new OperatorModification(5){
      public void serialize(SerializationStream s){
        s.write((byte)4);
        s.write(permissions);
      }
    };
  }
  public static OperatorModification changeDescription(String description){
    final byte[] descriptionBytes = description.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return new OperatorModification(descriptionBytes.length+5){
      public void serialize(SerializationStream s){
        s.write((byte)5);
        s.write(descriptionBytes);
      }
    };
  }
  public final static OperatorModification forcePasswordChange = new OperatorModification(1){
    public void serialize(SerializationStream s){
      s.write((byte)6);
    }
  };
  public final static OperatorModification unlockOperator = new OperatorModification(1){
    public void serialize(SerializationStream s){
      s.write((byte)7);
    }
  };
}