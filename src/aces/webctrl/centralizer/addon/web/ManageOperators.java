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
  public ManageOperators(){
    super(Collections.singleton("view_administrator_only"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/html/ManageOperators.html").replaceAll(
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
    final boolean connected = Initializer.isConnected();
    boolean modify = false;
    Operator op = null;
    int p = 0;
    if (webop!=null){
      op = webop.getOperator();
      p = op.getPermissions();
      if ((p&Permissions.OPERATOR_MANAGEMENT)!=0){
        modify = true;
      }
    }
    final PrintWriter out = res.getWriter();
    String type = req.getParameter("type");
    if (type==null){
      String username = req.getParameter("username");
      res.setContentType("text/html");
      out.print(html.replace("__USERNAME__", username==null?"NULL":username));
    }else if (type.equals("unlock")){
      if (modify){
        String name = req.getParameter("name");
        if (name==null){
          res.setStatus(400);
        }else if (connected){
          Operator o = Operators.get(name);
          if (o==null){
            res.setStatus(400);
          }else{
            if ((p|o.getPermissions())==p){
              Result<Byte> ret = Initializer.modifyOperator(op.getID(), o.getID(), Collections.singleton(OperatorModification.unlockOperator));
              if (ret.waitForResult(System.currentTimeMillis()+10000)){
                Byte b = ret.getResult();
                if (b==null){
                  res.setStatus(504);
                }else if (b!=Protocol.SUCCESS){
                  res.setStatus(403);
                }
              }else{
                res.setStatus(504);
              }
            }else{
              res.setStatus(403);
            }
          }
        }else{
          res.setStatus(504);
        }
      }else{
        res.setStatus(403);
      }
    }else if (type.equals("delete")){
      if (modify){
        String name = req.getParameter("name");
        if (name==null){
          res.setStatus(400);
        }else if (connected){
          Operator o = Operators.get(name);
          if (o==null){
            res.setStatus(400);
          }else{
            if ((p|o.getPermissions())==p){
              Result<Byte> ret = Initializer.deleteOperator(op.getID(), o.getID());
              if (ret.waitForResult(System.currentTimeMillis()+10000)){
                Byte b = ret.getResult();
                if (b==null){
                  res.setStatus(504);
                }else if (b!=Protocol.SUCCESS){
                  res.setStatus(403);
                }
              }else{
                res.setStatus(504);
              }
            }else{
              res.setStatus(403);
            }
          }
        }else{
          res.setStatus(504);
        }
      }else{
        res.setStatus(403);
      }
    }else if (type.equals("load")){
      String name = req.getParameter("name");
      if (name==null){
        final StringBuilder sb = new StringBuilder(512);
        ArrayList<String> list = new ArrayList<String>(Operators.count());
        Operators.forEach(new java.util.function.Predicate<Operator>(){
          public boolean test(Operator o){
            sb.append(o.getUsername()).append(';');
            sb.append(Utility.encodeAJAX(Utility.escapeHTML(o.getDisplayName()))).append(';');
            sb.append(Utility.encodeAJAX(Utility.escapeHTML(o.getDescription()).replace("&#10;", "<br>"))).append(';');
            list.add(sb.toString());
            sb.setLength(0);
            return true;
          }
        });
        list.sort(null);
        res.setContentType("text/plain");
        out.print(String.join("", list));
      }else{
        Operator o = Operators.get(name);
        if (o==null){
          res.setStatus(400);
        }else if (modify || o==op){
          StringBuilder sb = new StringBuilder(1024);
          sb.append(Utility.encodeAJAX(o.getDisplayName())).append(';');
          sb.append(o.getUsername()).append(';');
          sb.append(o.changePassword()).append(';');
          sb.append(Utility.encodeAJAX(o.getDescription())).append(';');
          sb.append(o.getNavigationTimeout()).append(';');
          {
            final long lastLogin = o.getLastLogin();
            if (lastLogin==-1){
              sb.append("Never;");
            }else{
              sb.append(Utility.encodeAJAX(Utility.escapeHTML(Logger.format.format(new Date(lastLogin))))).append(';');
            }
          }
          sb.append(Utility.encodeAJAX(Utility.escapeHTML(Logger.format.format(new Date(o.getStamp()))))).append(';');
          sb.append(Utility.encodeAJAX(Utility.escapeHTML(Logger.format.format(new Date(o.getCreationTime()))))).append(';');
          {
            final int pp = o.getPermissions();
            sb.append((pp&Permissions.ADMINISTRATOR)!=0).append(';');
            sb.append((pp&Permissions.OPERATOR_MANAGEMENT)!=0).append(';');
            sb.append((pp&Permissions.FILE_SYNCHRONIZATION)!=0).append(';');
            sb.append((pp&Permissions.FILE_RETRIEVAL)!=0).append(';');
            sb.append((pp&Permissions.SCRIPT_EXECUTION)!=0).append(';');
          }
          res.setContentType("text/plain");
          out.print(sb.toString());
        }else{
          res.setStatus(403);
        }
      }
    }else if (type.equals("save")){
      final String selected = req.getParameter("selected");
      final String user = req.getParameter("user");
      final String pass = req.getParameter("pass");
      final String disname = req.getParameter("disname");
      final String desc = req.getParameter("desc");
      final String navtime = req.getParameter("navtime");
      final String force = req.getParameter("force");
      final String padmin = req.getParameter("padmin");
      final String pops = req.getParameter("pops");
      final String psync = req.getParameter("psync");
      final String pret = req.getParameter("pret");
      final String pscript = req.getParameter("pscript");
      boolean isNull = false;
      isNull|=selected==null;
      isNull|=user==null;
      isNull|=pass==null;
      isNull|=disname==null;
      isNull|=desc==null;
      isNull|=navtime==null;
      isNull|=force==null;
      isNull|=padmin==null;
      isNull|=pops==null;
      isNull|=psync==null;
      isNull|=pret==null;
      isNull|=pscript==null;
      if (isNull){
        res.setStatus(400);
      }else{
        boolean force_, padmin_, pops_, psync_, pret_, pscript_;
        int navtime_;
        try{
          navtime_ = Integer.parseInt(navtime);
          force_ = Boolean.parseBoolean(force);
          padmin_ = Boolean.parseBoolean(padmin);
          pops_ = Boolean.parseBoolean(pops);
          psync_ = Boolean.parseBoolean(psync);
          pret_ = Boolean.parseBoolean(pret);
          pscript_ = Boolean.parseBoolean(pscript);
        }catch(Exception e){
          res.setStatus(400);
          return;
        }
        byte[] password = null;
        if (pass.length()>0){
          char[] arr = pass.toCharArray();
          Utility.obfuscate(arr);
          if (!Database.validatePassword(arr)){
            java.util.Arrays.fill(arr,(char)0);
            res.setStatus(400);
            res.setContentType("text/plain");
            out.print("Password does not meet complexity requirements.");
            return;
          }
          password = Utility.toBytes(arr);
          java.util.Arrays.fill(arr,(char)0);
        }
        if (!Database.validateName(user, false)){
          res.setStatus(400);
          res.setContentType("text/plain");
          out.print("Invalid username.");
          return;
        }
        int pp = 0;
        if (padmin_){ pp|=Permissions.ADMINISTRATOR; }
        if (pops_){ pp|=Permissions.OPERATOR_MANAGEMENT; }
        if (psync_){ pp|=Permissions.FILE_SYNCHRONIZATION; }
        if (pret_){ pp|=Permissions.FILE_RETRIEVAL; }
        if (pscript_){ pp|=Permissions.SCRIPT_EXECUTION; }
        pp = Permissions.validate(pp);
        if ((p|pp)!=p){
          res.setStatus(403);
          return;
        }
        if (selected.equalsIgnoreCase("NULL")){
          if (!modify){
            res.setStatus(403);
          }else if (password==null){
            res.setStatus(400);
          }else if (Operators.get(user)!=null){
            res.setStatus(400);
            res.setContentType("text/plain");
            out.print("An operator with the same username already exists.");
          }else if (connected){
            Result<Byte> ret = Initializer.createOperator(op.getID(), user, password, pp, disname, navtime_, desc, force_);
            if (ret.waitForResult(System.currentTimeMillis()+20000)){
              Byte b = ret.getResult();
              if (b==null){
                res.setStatus(504);
              }else if (b!=Protocol.SUCCESS){
                res.setStatus(403);
              }
            }else{
              res.setStatus(504);
            }
          }else{
            res.setStatus(504);
          }
        }else{
          Operator o = Operators.get(selected);
          if (o==null){
            res.setStatus(400);
            res.setContentType("text/plain");
            out.print("Operator does not exist.");
          }else if (modify || o==op){
            boolean limited = o==op && !modify;
            boolean failed = false;
            LinkedList<OperatorModification> list = new LinkedList<OperatorModification>();
            if (!user.equalsIgnoreCase(selected)){
              if (limited){
                failed = true;
              }else{
                list.add(OperatorModification.changeUsername(user));
              }
            }
            if (failed){
              res.setStatus(403);
            }else{
              if (force_){
                list.add(OperatorModification.forcePasswordChange);
              }
              if (password!=null){
                list.add(OperatorModification.changePassword(password));
              }
              if (pp!=o.getPermissions()){
                list.add(OperatorModification.changePermissions(pp));
              }
              if (navtime_!=o.getNavigationTimeout()){
                list.add(OperatorModification.changeNavigationTimeout(navtime_));
              }
              if (!disname.equals(o.getDisplayName())){
                list.add(OperatorModification.changeDisplayName(disname));
              }
              if (!desc.equals(o.getDescription())){
                list.add(OperatorModification.changeDescription(desc));
              }
              if (list.size()==0){
                res.setStatus(400);
                res.setContentType("text/plain");
                out.print("No changes were made.");
              }else if (connected){
                Result<Byte> ret = Initializer.modifyOperator(op.getID(), o.getID(), list);
                if (ret.waitForResult(System.currentTimeMillis()+20000)){
                  Byte b = ret.getResult();
                  if (b==null){
                    res.setStatus(504);
                  }else if (b!=Protocol.SUCCESS){
                    if (b==Protocol.PARTIAL_SUCCESS){
                      res.setStatus(400);
                      res.setContentType("text/plain");
                      out.print("Unable to save all changes.");
                    }else{
                      res.setStatus(403);
                    }
                  }
                }else{
                  res.setStatus(504);
                }
              }else{
                res.setStatus(504);
              }
            }
          }else{
            res.setStatus(403);
          }
        }
      }
    }else{
      res.sendError(400);
    }
  }
}