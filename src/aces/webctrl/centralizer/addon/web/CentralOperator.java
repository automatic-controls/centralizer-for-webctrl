/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.common.*;
import java.util.*;
import com.controlj.green.addonsupport.web.*;
public class CentralOperator implements com.controlj.green.addonsupport.web.auth.WebOperator {
  private volatile Operator op;
  private volatile boolean change;
  private volatile Link startLink = null;
  public CentralOperator(Operator op, boolean changePassword){
    this.op = op;
    change = changePassword;
    if (op.startTree!=null){
      try{
        startLink = Link.createLink(UITree.valueOf(op.startTree), op.startLocation, op.startAction, op.startCategory, op.startInstance, op.startTab);
      }catch(Throwable e){}
    }
  }
  public boolean changePassword(){
    return change;
  }
  public Operator getOperator(){
    return op;
  }
  @Override public int getNavigationTimeout(){
    return op.getNavigationTimeout();
  }
  @Override public String getDisplayName(){
    return op.getDisplayName();
  }
  @Override public String getLoginName(){
    return op.getUsername();
  }
  @Override public Link getStartLocation(){
    return startLink;
  }
  @Override public Locale getLocale(){
    return Locale.getDefault();
  }
  @Override public boolean isSystem(){
    return false;
  }
  @Override public Set<String> getPrivilegeSet(com.controlj.green.addonsupport.access.Location loc){
    return priv;
  }
  @Override public com.controlj.green.addonsupport.access.PrivilegeTester getFunctionalPrivilegeTester(){
    return tester;
  }
  @Override public com.controlj.green.addonsupport.access.PrivilegeTester getLocationPrivilegeTester(com.controlj.green.addonsupport.access.Location loc){
    return tester;
  }
  private final static com.controlj.green.addonsupport.access.PrivilegeTester tester = new com.controlj.green.addonsupport.access.PrivilegeTester(){
    @Override public boolean hasPrivilege(String str){
      return true;
    }
  };
  private volatile static Set<String> priv;
  static {
    Set<String> priv = new HashSet<String>();
    priv.add("administrator");
    CentralOperator.priv = Collections.unmodifiableSet(priv);
  }
}