/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.SerializationStream;
public abstract class ServerModification {
  private volatile int len;
  private ServerModification(int len){ this.len = len; }
  /** Used to precompute serialization length */
  public int length(){
    return len;
  }
  public abstract void serialize(SerializationStream s);
  public static ServerModification changeName(String name){
    final byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return new ServerModification(nameBytes.length+5){
      public void serialize(SerializationStream s){
        s.write((byte)0);
        s.write(nameBytes);
      }
    };
  }
  public static ServerModification changeDescription(String desc){
    final byte[] descBytes = desc.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return new ServerModification(descBytes.length+5){
      public void serialize(SerializationStream s){
        s.write((byte)1);
        s.write(descBytes);
      }
    };
  }
}