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
public class ManageFileSync extends SecureServlet {
  private volatile static String html = null;
  public ManageFileSync(){
    super(java.util.Collections.singleton("view_administrator_only"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/html/ManageFileSync.html").replaceAll(
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
      if ((op.getPermissions()&Permissions.FILE_SYNCHRONIZATION)==0){
        res.sendError(403, "You do not have sufficient permissions to access this webpage.");
      }else{
        final String type = req.getParameter("type");
        if (type==null){
          res.setContentType("text/html");
          res.getWriter().print(html);
        }else if (type.equals("")){
          
        }else{
          res.sendError(400);
        }
      }
    }
  }
}