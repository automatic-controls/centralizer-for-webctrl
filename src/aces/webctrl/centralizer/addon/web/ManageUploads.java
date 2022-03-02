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
import java.util.*;
public class ManageUploads extends SecureServlet {
  private volatile static String html = null;
  public ManageUploads(){
    super(java.util.Collections.singleton("view_administrator_only"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/html/ManageUploads.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
      ).replace(
        "__PREFIX__",
        Initializer.getPrefix()
      ).replace(
        "__ACTIVE_FOLDER__",
        Utility.escapeHTML(Initializer.systemFolder.toString())
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
      if ((op.getPermissions()&Permissions.DATA_COLLECTION)==0){
        res.sendError(403, "You do not have sufficient permissions to access this webpage.");
      }else{
        final String type = req.getParameter("type");
        if (type==null){
          final StringBuilder sb = new StringBuilder(Uploads.size()<<7);
          Uploads.forEach(new java.util.function.Predicate<Upload>(){
            public boolean test(Upload x){
              sb.append("add(\"").append(x.getID()).append("\",\"");
              sb.append(Utility.escapeJS(x.getSourceString())).append("\",\"");
              sb.append(Utility.escapeJS(x.getDestination())).append("\",\"");
              sb.append(Utility.escapeJS(x.getExpression())).append("\",\"");
              sb.append(Utility.escapeJS(x.getNextString())).append("\");\n");
              return false;
            }
          });
          res.setContentType("text/html");
          res.getWriter().print(html.replace("//__INIT_SCRIPT__", sb.toString()));
        }else if (type.equals("create")){
          final String src = req.getParameter("src");
          final String dst = req.getParameter("dst");
          final String expr = req.getParameter("expr");
          if (src==null || dst==null || expr==null){
            res.setStatus(400);
          }else{
            final Upload x = new Upload(src,dst,expr);
            Uploads.add(x);
            Logger.logAsync(op.getUsername()+" created data collection mapping.");
            res.setContentType("text/plain");
            res.getWriter().print(String.valueOf(x.getID())+','+x.getNextString());
          }
        }else if (type.equals("modify")){
          final String ID = req.getParameter("ID");
          final String src = req.getParameter("src");
          final String dst = req.getParameter("dst");
          final String expr = req.getParameter("expr");
          if (ID==null || src==null || dst==null || expr==null){
            res.setStatus(400);
          }else{
            try{
              final Upload x = Uploads.get(Integer.parseInt(ID));
              if (x==null){
                res.setStatus(410);
              }else{
                x.setSource(src);
                x.setDestination(dst);
                x.setExpression(expr);
                Logger.logAsync(op.getUsername()+" modified data collection mapping.");
                res.setContentType("text/plain");
                res.getWriter().print(x.getNextString());
              }
            }catch(NumberFormatException e){
              res.setStatus(400);
            }
          }
        }else if (type.equals("delete")){
          final String ID = req.getParameter("ID");
          if (ID==null){
            res.setStatus(400);
          }else{
            try{
              if (Uploads.remove(Integer.parseInt(ID))!=null){
                Logger.logAsync(op.getUsername()+" deleted data collection mapping.");
              }
            }catch(NumberFormatException e){
              res.setStatus(400);
            }
          }
        }else if (type.equals("triggerAll")){
          if (Initializer.isConnected()){
            final ArrayList<Result<Byte>> rets = Uploads.forceTrigger();
            Logger.log(op.getUsername()+" triggered all data collection mappings.");
            for (Result<Byte> ret:rets){
              if (ret==null){
                res.setStatus(409);
                return;
              }
            }
            Byte b;
            for (Result<Byte> ret:rets){
              ret.waitForResult(-1);
              b = ret.getResult();
              if (b==null){
                res.setStatus(504);
                return;
              }else if (b!=Protocol.SUCCESS){
                res.setStatus(409);
                return;
              }
            }
          }else{
            res.setStatus(504);
          }
        }else if (type.equals("trigger")){
          if (Initializer.isConnected()){
            final String ID = req.getParameter("ID");
            if (ID==null){
              res.setStatus(400);
            }else{
              try{
                final Upload x = Uploads.get(Integer.parseInt(ID));
                Logger.log(op.getUsername()+" triggered a data collection mapping.");
                if (x==null){
                  res.setStatus(410);
                }else{
                  final Result<Byte> ret = x.forceTrigger();
                  if (ret==null){
                    res.setStatus(409);
                  }else{
                    ret.waitForResult(-1);
                    final Byte b = ret.getResult();
                    if (b==null){
                      res.setStatus(504);
                    }else if (b!=Protocol.SUCCESS){
                      res.setStatus(409);
                    }
                  }
                }
              }catch(NumberFormatException e){
                res.setStatus(400);
              }
            }
          }else{
            res.setStatus(504);
          }
        }else{
          res.sendError(400, "Invalid type parameter.");
        }
      }
    }
  }
}