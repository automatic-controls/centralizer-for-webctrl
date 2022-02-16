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
import com.controlj.green.addonsupport.web.*;
public class StartLocation extends SecureServlet {
  private volatile static String html = null;
  public StartLocation(){
    super(java.util.Collections.singleton("login"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/html/StartLocation.html").replaceAll(
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
      final String type = req.getParameter("type");
      if (type==null){
        res.setContentType("text/html");
        res.getWriter().print(html
          .replace("__USERNAME__", esc(op.getUsername()))
          .replace("__TREE__", esc(op.startTree))
          .replace("__LOCATION__", esc(op.startLocation))
          .replace("__ACTION__", esc(op.startAction))
          .replace("__CATEGORY__", esc(op.startCategory))
          .replace("__INSTANCE__", esc(op.startInstance))
          .replace("__TAB__", esc(op.startTab))
        );
      }else if (type.equals("test")){
        final String tree = req.getParameter("tree");
        final String location = req.getParameter("location");
        final String action = req.getParameter("action");
        final String category = req.getParameter("category");
        final String instance = req.getParameter("instance");
        final String tab = req.getParameter("tab");
        if (tree==null || location==null || action==null || category==null || instance==null || tab==null){
          res.setStatus(400);
        }else{
          try{
            String url = Link.createLink(UITree.valueOf(tree), location, action, category, instance, tab).getURL(req);
            res.setContentType("text/plain");
            res.getWriter().print(url);
          }catch(Throwable e){
            res.setStatus(404);
          }
        }
      }else if (type.equals("save")){
        final String tree = req.getParameter("tree");
        final String location = req.getParameter("location");
        final String action = req.getParameter("action");
        final String category = req.getParameter("category");
        final String instance = req.getParameter("instance");
        final String tab = req.getParameter("tab");
        if (tree==null || location==null || action==null || category==null || instance==null || tab==null){
          res.setStatus(400);
        }else{
          op.startTree = tree;
          op.startLocation = location;
          op.startAction = action;
          op.startCategory = category;
          op.startInstance = instance;
          op.startTab = tab;
        }
      }else{
        res.sendError(400);
      }
    }
  }
  private static String esc(String str){
    return str==null?"":Utility.escapeJS(str);
  }
}