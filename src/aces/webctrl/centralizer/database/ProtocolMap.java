package aces.webctrl.centralizer.database;
import aces.webctrl.centralizer.common.*;
import java.util.*;
import java.util.function.*;
public class ProtocolMap {
  private final static TreeMap<Byte,Consumer<Connection>> protocolMap = generateMap();
  public static void exec(Connection con, Byte b){
    Consumer<Connection> f = protocolMap.get(b);
    if (f==null){
      Logger.logAsync("Unrecognized protocol.");
      con.close(true);
    }else{
      f.accept(con);
    }
  }
  private static TreeMap<Byte,Consumer<Connection>> generateMap(){
    //All consumers should call Connection.listen3() when their task is complete
    TreeMap<Byte,Consumer<Connection>> map = new TreeMap<Byte,Consumer<Connection>>();
    map.put(Protocol.LOGIN, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(8192, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              int ID = s.readInt();
              String username = s.readString();
              byte[] password = s.readBytes();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              Arrays.fill(s.data,(byte)0);
              byte ret;
              Operator op = Operators.get(ID);
              if (op==null){
                Arrays.fill(password,(byte)0);
                ret = Protocol.DOES_NOT_EXIST;
              }else if (op.isLockedOut()){
                Arrays.fill(password,(byte)0);
                ret = Protocol.LOCKED_OUT;
              }else if (op.checkCredentials(username,password)){
                c.login(op);
                ret = op.changePassword()?Protocol.CHANGE_PASSWORD:Protocol.SUCCESS;
              }else{
                ret = Protocol.FAILURE;
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in LOGIN protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.LOGOUT, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              int ID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              c.wrap.write(c.logout(ID)?Protocol.SUCCESS:Protocol.FAILURE, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in LOGOUT protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.CREATE_OPERATOR, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(16384, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              int ID = s.readInt();
              String username = s.readString();
              byte[] password = s.readBytes();
              int permissions = s.readInt();
              String displayName = s.readString();
              int navTimeout = s.readInt();
              String desc = s.readString();
              boolean forceChange = s.readBoolean();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              Arrays.fill(s.data,(byte)0);
              byte ret;
              OperatorTracker t = c.getTracker(ID);
              if (t==null){
                Arrays.fill(password,(byte)0);
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                int p = t.getOperator().getPermissions();
                if ((p&Permissions.OPERATORS)==0){
                  Arrays.fill(password,(byte)0);
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  permissions&=p;
                  final Operator op = Operators.add(username, password, permissions, displayName, navTimeout, desc);
                  if (op==null){
                    ret = Protocol.FAILURE;
                  }else{
                    op.changePassword(forceChange);
                    ret = Protocol.SUCCESS;
                    Connections.forEach(new Predicate<Connection>(){
                      @Override public boolean test(Connection con){
                        con.updateOperators(op);
                        return true;
                      }
                    });
                  }
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in CREATE_OPERATOR protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.MODIFY_OPERATOR, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(16384, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              final int modID = s.readInt();
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if (authID!=modID && (authP&Permissions.OPERATORS)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  final Operator op = Operators.get(modID);
                  if (op==null){
                    ret = Protocol.DOES_NOT_EXIST;
                  }else{
                    final int modP = op.getPermissions();
                    if ((authP|modP)==authP){
                      ret = Protocol.SUCCESS;
                      boolean modified = false;
                      Logger.logAsync("Operator "+op.getUsername()+" modified by "+t.getOperator().getUsername());
                      while (!s.end()){
                        switch (s.readByte()){
                          case 0:{//Username
                            if (op.setUsername(s.readString())){
                              modified = true;
                            }else{
                              ret = Protocol.PARTIAL_SUCCESS;
                            }
                            break;
                          }
                          case 1:{//Password
                            byte[] password = s.readBytes();
                            if (Database.validatePassword(password)){
                              op.setPassword(password);
                              modified = true;
                            }else{
                              Arrays.fill(password,(byte)0);
                              ret = Protocol.PARTIAL_SUCCESS;
                            }
                            break;
                          }
                          case 2:{//Display Name
                            op.setDisplayName(s.readString());
                            modified = true;
                            break;
                          }
                          case 3:{//Navigation Timeout
                            op.setNavigationTimeout(s.readInt());
                            modified = true;
                            break;
                          }
                          case 4:{//Permissions
                            int p = s.readInt();
                            int newP = p&authP;
                            if (p!=newP){
                              ret = Protocol.PARTIAL_SUCCESS;
                            }
                            op.setPermissions(newP);
                            modified = true;
                            break;
                          }
                          case 5:{//Description
                            op.setDescription(s.readString());
                            modified = true;
                            break;
                          }
                          case 6:{//Force Change
                            op.changePassword(true);
                            break;
                          }
                          case 7:{//Unlock
                            op.unlock();
                            break;
                          }
                          default:{
                            Logger.logAsync("Unrecognized entry in MODIFY_OPERATOR protocol.");
                            break;
                          }
                        }
                      }
                      if (modified){
                        Connections.forEach(new Predicate<Connection>(){
                          @Override public boolean test(Connection con){
                            con.updateOperators(op);
                            return true;
                          }
                        });
                      }
                    }else{
                      ret = Protocol.INSUFFICIENT_PERMISSIONS;
                    }
                  }
                }
              }
              Arrays.fill(s.data,(byte)0);
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in MODIFY_OPERATOR protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.DELETE_OPERATOR, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              final int modID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.OPERATORS)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  Operator op = Operators.get(modID);
                  if (op==null){
                    ret = Protocol.DOES_NOT_EXIST;
                  }else{
                    final int modP = op.getPermissions();
                    if ((authP|modP)==authP && Operators.remove(op)){
                      Logger.logAsync("Operator "+op.getUsername()+" deleted by "+t.getOperator().getUsername());
                      ret = Protocol.SUCCESS;
                      Connections.forEach(new Predicate<Connection>(){
                        @Override public boolean test(Connection con){
                          con.updateOperators(op);
                          return true;
                        }
                      });
                    }else{
                      ret = Protocol.INSUFFICIENT_PERMISSIONS;
                    }
                  }
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in DELETE_OPERATOR protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.MODIFY_SERVER, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              final int sID = s.readInt();
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  Server server = Servers.get(sID);
                  if (server==null){
                    ret = Protocol.DOES_NOT_EXIST;
                  }else{
                    Logger.logAsync("Server "+server.getName()+" modified by "+t.getOperator().getUsername());
                    ret = Protocol.SUCCESS;
                    while (!s.end()){
                      switch (s.readByte()){
                        case 0:{//Name
                          if (!server.setName(s.readString())){
                            ret = Protocol.PARTIAL_SUCCESS;
                          }
                          break;
                        }
                        case 1:{//Description
                          server.setDescription(s.readString());
                          break;
                        }
                        default:{
                          Logger.logAsync("Unrecognized entry in MODIFY_SERVER protocol.");
                          break;
                        }
                      }
                    }
                    if (server.isConnected()){
                      Connections.forEach(new Predicate<Connection>(){
                        @Override public boolean test(Connection con){
                          if (server==con.server){
                            con.updateServerParams();
                            return false;
                          }
                          return true;
                        }
                      });
                    }
                  }
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in MODIFY_SERVER protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.DISCONNECT_SERVER, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              final int sID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  Server server = Servers.get(sID);
                  if (server==null){
                    ret = Protocol.DOES_NOT_EXIST;
                  }else if (server.isConnected()){
                    final Container<Connection> container = new Container<Connection>();
                    if (!Connections.forEach(new Predicate<Connection>(){
                      @Override public boolean test(Connection con){
                        if (server==con.server){
                          container.x = con;
                          return false;
                        }
                        return true;
                      }
                    })){
                      Logger.logAsync("Server "+server.getName()+" disconnected by "+t.getOperator().getUsername());
                      container.x.close(true);
                      ret = Protocol.SUCCESS;
                    }else{
                      ret = Protocol.FAILURE;
                    }
                  }else{
                    ret = Protocol.FAILURE;
                  }
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in DISCONNECT_SERVER protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.DELETE_SERVER, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              final int sID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  Server server = Servers.get(sID);
                  if (server==null){
                    ret = Protocol.DOES_NOT_EXIST;
                  }else if (Servers.remove(server)){
                    Logger.logAsync("Server "+server.getName()+" deleted by "+t.getOperator().getUsername());
                    ret = Protocol.SUCCESS;
                    if (server.isConnected()){
                      final Container<Connection> container = new Container<Connection>();
                      if (!Connections.forEach(new Predicate<Connection>(){
                        @Override public boolean test(Connection con){
                          if (server==con.server){
                            container.x = con;
                            return false;
                          }
                          return true;
                        }
                      })){
                        container.x.close(true);
                      }
                    }
                  }else{
                    ret = Protocol.FAILURE;
                  }
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in DELETE_SERVER protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.GET_SERVER_LIST, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  ret = Protocol.SUCCESS;
                }
              }
              final boolean go = ret==Protocol.SUCCESS;
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  if (go){
                    final ArrayList<Server> servers = new ArrayList<Server>();
                    Servers.forEach(new Predicate<Server>(){
                      public boolean test(Server s){
                        servers.add(s);
                        return true;
                      }
                    });
                    servers.sort(new Comparator<Server>(){
                      @Override public int compare(Server a, Server b){
                        return a.getName().compareTo(b.getName());
                      }
                    });
                    int len = 0;
                    byte[][] serialBytes = new byte[servers.size()][];
                    for (int i=0;i<serialBytes.length;++i){
                      serialBytes[i] = servers.get(i).serialize(false);
                      len+=serialBytes[i].length+4;
                    }
                    SerializationStream s = new SerializationStream(len);
                    for (int i=0;i<serialBytes.length;++i){
                      s.write(serialBytes[i]);
                    }
                    c.wrap.writeBytes(s.data, null, c.new Handler<Void>(){
                      public void func(Void v){
                        c.listen3();
                      }
                    });
                  }else{
                    c.listen3();
                  }
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in GET_SERVER_LIST protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.GET_ACTIVE_OPERATORS, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              final int sID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              ArrayList<OperatorTracker> track = null;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  Server server = Servers.get(sID);
                  if (server==null){
                    ret = Protocol.DOES_NOT_EXIST;
                  }else{
                    ret = Protocol.SUCCESS;
                    if (server.isConnected()){
                      final Container<Connection> container = new Container<Connection>();
                      if (!Connections.forEach(new Predicate<Connection>(){
                        @Override public boolean test(Connection con){
                          if (server==con.server){
                            container.x = con;
                            return false;
                          }
                          return true;
                        }
                      })){
                        track = container.x.getTrackers();
                      }
                    }
                  }
                }
              }
              final ArrayList<OperatorTracker> trackers = track;
              final boolean go = ret==Protocol.SUCCESS;
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  if (go){
                    int len = 4;
                    int userCount = 0;
                    if (trackers!=null){
                      userCount = trackers.size();
                      len+=userCount<<2;
                    }
                    SerializationStream s = new SerializationStream(len);
                    s.write(userCount);
                    if (trackers!=null){
                      for (OperatorTracker t:trackers){
                        s.write(t.getOperator().getID());
                      }
                    }
                    c.wrap.writeBytes(s.data, null, c.new Handler<Void>(){
                      public void func(Void v){
                        c.listen3();
                      }
                    });
                  }else{
                    c.listen3();
                  }
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in GET_ACTIVE_OPERATORS protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.GET_CONFIG, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  ret = Protocol.SUCCESS;
                }
              }
              final boolean go = ret==Protocol.SUCCESS;
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  if (go){
                    SerializationStream s = new SerializationStream(Config.LENGTH);
                    Config.serialize(s);
                    c.wrap.writeBytes(s.data, null, c.new Handler<Void>(){
                      public void func(Void v){
                        c.listen3();
                      }
                    });
                  }else{
                    c.listen3();
                  }
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in GET_CONFIG protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.CONFIGURE, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(8192, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  ret = Protocol.SUCCESS;
                  if (Config.deserialize(s)){
                    Connections.forEach(new Predicate<Connection>(){
                      @Override public boolean test(Connection con){
                        con.updatePingInterval();
                        return true;
                      }
                    });
                  }
                  if (!s.end()){
                    Logger.logAsync("Lost data detected.");
                  }
                  Logger.logAsync("Database configured by "+t.getOperator().getUsername());
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in CONFIGURE protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.GENERATE_PRESHARED_KEY, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else if (Keys.generateNewKey()==null){
                  ret = Protocol.FAILURE;
                }else{
                  ret = Protocol.SUCCESS;
                  Logger.logAsync("Preshared key generated by "+t.getOperator().getUsername());
                  Connections.forEach(new Predicate<Connection>(){
                    @Override public boolean test(Connection con){
                      con.updatePresharedKey();
                      return true;
                    }
                  });
                }
              }
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  c.listen3();
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in GENERATE_PRESHARED_KEY protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    map.put(Protocol.RESTART_DATABASE, new Consumer<Connection>(){
      public void accept(final Connection c){
        c.wrap.readBytes(32, null, c.new Handler<byte[]>(){
          public void func(byte[] data){
            try{
              SerializationStream s = new SerializationStream(data);
              final int authID = s.readInt();
              if (!s.end()){
                Logger.logAsync("Lost data detected.");
              }
              byte ret;
              OperatorTracker t = c.getTracker(authID);
              if (t==null){
                ret = Protocol.NOT_LOGGED_IN;
              }else{
                t.reset();
                final int authP = t.getOperator().getPermissions();
                if ((authP&Permissions.ADMINISTRATOR)==0){
                  ret = Protocol.INSUFFICIENT_PERMISSIONS;
                }else{
                  ret = Protocol.SUCCESS;
                }
              }
              final boolean restart = ret==Protocol.SUCCESS;
              c.wrap.write(ret, null, c.new Handler<Void>(){
                public void func(Void v){
                  if (restart){
                    Logger.logAsync("Database restart initiated by "+t.getOperator().getUsername());
                    if (!Main.restart()){
                      c.listen3();
                    }
                  }else{
                    c.listen3();
                  }
                }
              });
            }catch(Exception e){
              Logger.logAsync("Error occurred in RESTART_DATABASE protocol.",e);
              c.close(true);
              return;
            }
          }
        });
      }
    });
    return map;
  }
}