/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.core.*;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
public class ManageOperators extends SecureServlet {
  private volatile static String html = null;
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/web/ManageOperators.html").replaceAll(
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
  @Override public void process(final HttpServletRequest req, final HttpServletResponse res) throws Throwable {
    if (req.isUserInRole("view_administrator_only")){
      boolean modify = false;
      Operator op = null;
      int p = 0;
      if (Initializer.isConnected()){
        CentralOperator webop = getOperator(req);
        if (webop!=null){
          op = webop.getOperator();
          p = op.getPermissions();
          if ((p&Permissions.OPERATOR_MANAGEMENT)!=0){
            modify = true;
          }
        }
      }
      final PrintWriter out = res.getWriter();
      String type = req.getParameter("type");
      if (type==null){
        res.setContentType("text/html");
        out.print(html.replace("__USERNAME__", "NULL"));
      }else if (type.equals("unlock")){
        if (modify){
          String name = req.getParameter("name");
          if (name==null){
            res.setStatus(400);
          }else{
            Operator o = Operators.get(name);
            if (o==null){
              res.setStatus(400);
            }else{
              if ((p|o.getPermissions())==p){
                Result<Byte> ret = Initializer.modifyOperator(op.getID(), o.getID(), Collections.singleton(OperatorModification.unlockOperator));
                if (ret.waitForResult(System.currentTimeMillis()+10000)){
                  Byte b = ret.getResult();
                  if (b!=Protocol.SUCCESS){
                    res.setStatus(b==null?504:403);
                  }
                }else{
                  res.setStatus(504);
                }
              }else{
                res.setStatus(403);
              }
            }
          }
        }else{
          res.setStatus(403);
        }
      }else if (type.equals("delete")){
        if (modify){
          String name = req.getParameter("name");
          if (name==null){
            res.setStatus(400);
          }else{
            Operator o = Operators.get(name);
            if (o==null){
              res.setStatus(400);
            }else{
              if ((p|o.getPermissions())==p){
                Result<Byte> ret = Initializer.deleteOperator(op.getID(), o.getID());
                if (ret.waitForResult(System.currentTimeMillis()+10000)){
                  Byte b = ret.getResult();
                  if (b!=Protocol.SUCCESS){
                    res.setStatus(b==null?504:403);
                  }
                }else{
                  res.setStatus(504);
                }
              }else{
                res.setStatus(403);
              }
            }
          }
        }else{
          res.setStatus(403);
        }
      }else if (type.equals("load")){
        String name = req.getParameter("name");
        if (name==null){
          StringBuilder sb = new StringBuilder(Operators.count()<<8);
          Operators.forEach(new java.util.function.Predicate<Operator>(){
            public boolean test(Operator o){
              sb.append(Utility.encodeAJAX(o.getUsername())).append(';');
              sb.append(Utility.encodeAJAX(o.getDisplayName())).append(';');
              sb.append(Utility.encodeAJAX(o.getDescription())).append(';');
              //TODO - replace new lines with <br>
              return true;
            }
          });
          res.setContentType("text/plain");
          out.print(sb.toString());
        }else if (modify){
          Operator o = Operators.get(name);
          if (o==null){
            res.setStatus(400);
          }else{
            //TODO

          }
        }else{
          res.setStatus(403);
        }
      }else if (type.equals("save")){
        //TODO

      }else{
        String username = req.getParameter("username");
        if (username==null){
          res.sendError(400);
        }else if (modify){
          res.setContentType("text/html");
          out.print(html.replace("__USERNAME__", username));
        }else{
          res.sendError(403);
        }
      }
    }else{
      res.sendError(403);
    }
  }
}