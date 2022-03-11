package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.Logger;
import java.util.*;
import com.controlj.green.datatable.util.CoreHelper;
import com.controlj.green.addonsupport.web.auth.AuthenticationManager;
import com.controlj.green.addonsupport.web.auth.OperatorAdapter;
import com.controlj.green.extensionsupport.Extension;
import com.controlj.green.webserver.*;
import com.controlj.green.core.ui.UserSession;
/**
 * Namespace which contains methods to access small sections of a few internal WebCTRL APIs.
 */
public class HelperAPI {
  /**
   * Specifies whether methods of this API should log stack traces generated from errors.
   */
  public static volatile boolean logErrors = true;
  /**
   * @return a collection of all local WebCTRL operators where usernames are mapped to display names, or {@code null} if an error occurs.
   */
  public static Map<String,String> getLocalOperators(){
    try{
      return new CoreHelper().getOperatorList();
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return null;
    }
  }
  /**
   * Terminates sessions for all foreign operators.
   * @return whether this method executed successfully.
   */
  public static boolean logoutAllForeign(){
    try{
      for (final UserSession session:UserSession.getAllUserSessions()){
        if (session.getOperator().isForeign()){
          session.close();
        }
      }
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return false;
    }
  }
  /**
   * Terminates sessions corresponding to the given operator.
   * @return whether at least one session was successfully closed.
   */
  public static boolean logout(aces.webctrl.centralizer.common.Operator operator){
    boolean ret = false;
    try{
      com.controlj.green.core.data.Operator op;
      com.controlj.green.addonsupport.access.Operator op2;
      for (final UserSession session:UserSession.getAllUserSessions()){
        op = session.getOperator();
        if (op instanceof OperatorAdapter){
          op2 = ((OperatorAdapter)op).getAdaptee();
          if (op2 instanceof aces.webctrl.centralizer.addon.web.CentralOperator && ((aces.webctrl.centralizer.addon.web.CentralOperator)op2).getOperator()==operator){
            ret = true;
            session.close();
          }
        }
      }
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
    }
    return ret;
  }
  /**
   * Disables an add-on with the given name.
   * @param name is used to identify the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean disableAddon(String name){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      AddOn addon = null;
      for (AddOn x:server.scanForAddOns()){
        if (x.getName().equals(name)){
          addon = x;
          break;
        }
      }
      if (addon==null){
        return false;
      }
      server.disableAddOn(addon);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return false;
    }
  }
  /**
   * Enables an add-on with the given name.
   * @param name is used to identify the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean enableAddon(String name){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      AddOn addon = null;
      for (AddOn x:server.scanForAddOns()){
        if (x.getName().equals(name)){
          addon = x;
          break;
        }
      }
      if (addon==null){
        return false;
      }
      server.enableAddOn(addon);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return false;
    }
  }
  /**
   * Removes an add-on with the given name.
   * @param name is used to identify the add-on.
   * @param removeData specifies whether to remove data associated to the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean removeAddon(String name, boolean removeData){
    try{
      TomcatServer server = TomcatServerSingleton.get();
      if (server==null){
        return false;
      }
      AddOn addon = null;
      for (AddOn x:server.scanForAddOns()){
        if (x.getName().equals(name)){
          addon = x;
          break;
        }
      }
      if (addon==null){
        return false;
      }
      server.removeAddOn(addon, removeData);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return false;
    }
  }
  /**
   * Activates the specified {@code WebOperatorProvider}.
   * @param addon specifies the name of the addon to activate.
   * @return an {@code Extension} object matching the given addon, or {@code null} if the addon cannot be found or if any error occurs.
   */
  public static Extension activateWebOperatorProvider(String addon){
    try{
      AuthenticationManager auth = new AuthenticationManager();
      for (Extension e:auth.findWebOperatorProviders()){
        if (addon.equals(e.getName())){
          auth.activateProvider(e);
          return e;
        }
      }
    }catch(Throwable t){}
    return null;
  }
  /**
   * Activates the default {@code WebOperatorProvider}.
   * @return whether this method executed successfully.
   */
  public static boolean activateDefaultWebOperatorProvider(){
    try{
      new AuthenticationManager().activateProvider(null);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return false;
    }
  }
}