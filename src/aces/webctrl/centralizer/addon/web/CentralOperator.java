package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.common.*;
import java.util.*;
public class CentralOperator implements com.controlj.green.addonsupport.web.auth.WebOperator {
  private volatile Operator op;
  private volatile boolean change;
  public CentralOperator(Operator op, boolean changePassword){
    this.op = op;
    change = changePassword;
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
  @Override public com.controlj.green.addonsupport.web.Link getStartLocation(){
    return null;
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
  private final static Set<String> priv = makePrivSet();
  private static Set<String> makePrivSet(){
    Set<String> priv = new HashSet<String>();
    priv.add("administrator");
    return priv;
  }
}
