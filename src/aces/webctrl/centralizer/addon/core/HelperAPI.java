package aces.webctrl.centralizer.addon.core;
import aces.webctrl.centralizer.common.Logger;
import java.nio.file.*;
import java.util.*;
import com.controlj.green.datatable.util.CoreHelper;
import com.controlj.green.addonsupport.web.auth.AuthenticationManager;
import com.controlj.green.extensionsupport.Extension;
import com.controlj.green.web.tools.webapp.WebserverProxy;
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
   * Disables an add-on with the given name.
   * @param name is used to identify the add-on.
   * @return {@code true} on success; {@code false} on failure.
   */
  public static boolean disableAddon(String name){
    try{
      new WebserverProxy().disableAddOn(name);
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
      new WebserverProxy().enableAddOn(name);
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
      new WebserverProxy().removeAddOn(name, removeData);
      return true;
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
      return false;
    }
  }
  /**
   * Activates the specified {@code WebOperatorProvider}.
   * @param addonFile specifies the location of the .addon file to activate.
   * @return an {@code Extension} object matching the given {@code addonFile}, or {@code null} if the addon cannot be found or if any error occurs.
   */
  public static Extension activateWebOperatorProvider(Path addonFile){
    try{
      AuthenticationManager auth = new AuthenticationManager();
      for (Extension e:auth.findWebOperatorProviders()){
        if (Files.isSameFile(e.getSourceFile().toPath(), addonFile)){
          auth.activateProvider(e);
          return e;
        }
      }
    }catch(Throwable t){
      if (logErrors){ Logger.logAsync(t); }
    }
    return null;
  }
}