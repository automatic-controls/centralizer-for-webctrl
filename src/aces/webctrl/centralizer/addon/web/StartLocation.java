/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.core.*;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import javax.servlet.*;
import javax.servlet.http.*;
public class StartLocation extends SecureServlet {
  private volatile static String html = null;
  public StartLocation(){
    super(java.util.Collections.singleton("login"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/web/StartLocation.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
      ).replace(
        "__PREFIX__",
        Initializer.getPrefix()
      );
    }catch(Throwable e){
      if (e instanceof ServletException){
        throw (ServletException)e;
      }else{
        throw new ServletException(e);
      }
    }
  }
  @Override public void process(final CentralOperator webop, final HttpServletRequest req, final HttpServletResponse res) throws Throwable {
    if (webop==null){
      res.sendError(403, "Local operators cannot access this webpage.");
    }else{
      final Operator op = webop.getOperator();
      
    }
  }
}
