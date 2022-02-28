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
      }else if (Initializer.isConnected()){
        final int authID = op.getID();
        final String type = req.getParameter("type");
        if (type==null){
          final Result<SyncList> r1 = Initializer.getSyncTasks(authID);
          final Result<ServerList> r2 = Initializer.getServerList(authID);
          final long expiry = System.currentTimeMillis()+20000;
          r1.waitForResult(expiry);
          r2.waitForResult(expiry);
          final SyncList syncs = r1.getResult();
          final ServerList servers = r2.getResult();
          if (syncs==null || servers==null || syncs.tasks==null || servers.servers==null || syncs.status!=Protocol.SUCCESS || servers.status!=Protocol.SUCCESS){
            res.sendError(504, "Request timed out. Ensure the database is connected and refresh your login.");
          }else{
            final StringBuilder sb = new StringBuilder(2048);
            final TreeMap<Integer,Server> map = new TreeMap<Integer,Server>();
            for (Server s:servers.servers){
              if (map.put(s.getID(),s)==null){
                sb.append("serverMappings[\"").append(s.getID()).append("\"] = \"").append(Utility.escapeJS(s.getName())).append("\";\n");
              }
            }
            for (SyncTask s:syncs.tasks){
              sb.append("addTask(\"").append(s.getID()).append("\",\"");
              sb.append(Utility.escapeJS(s.getDescription())).append("\",\"");
              sb.append(Utility.escapeJS(s.getSource())).append("\",\"");
              sb.append(Utility.escapeJS(s.getDestination())).append("\",\"");
              sb.append(s.getSyncInterval()).append("\",");
              sb.append(s.isAllServers()).append(",[");
              final Container<Boolean> first = new Container<Boolean>(true);
              s.forEach(new java.util.function.Predicate<Integer>(){
                public boolean test(Integer x){
                  if (map.get(x)!=null){
                    if (first.x){
                      first.x = false;
                    }else{
                      sb.append(',');
                    }
                    sb.append('"').append(x).append('"');
                  }
                  return false;
                }
              });
              sb.append("]);\n");
            }
            res.setContentType("text/html");
            res.getWriter().print(html.replace("//__INIT_SCRIPT__", sb.toString()));
          }
        }else if (type.equals("triggerAll")){
          final Result<Byte> ret = Initializer.triggerSyncTasks(authID);
          ret.waitForResult(System.currentTimeMillis()+10000);
          final Byte b = ret.getResult();
          if (b==null){
            res.setStatus(504);
          }else if (b!=Protocol.SUCCESS){
            res.setStatus(403);
          }
        }else if (type.equals("trigger")){
          final String id = req.getParameter("id");
          if (id==null){
            res.setStatus(400);
          }else{
            try{
              final int ID = Integer.parseInt(id);
              final Result<Byte> ret = Initializer.triggerSyncTask(authID,ID);
              ret.waitForResult(System.currentTimeMillis()+10000);
              final Byte b = ret.getResult();
              if (b==null){
                res.setStatus(504);
              }else if (b!=Protocol.SUCCESS){
                res.setStatus(403);
              }
            }catch(NumberFormatException e){
              res.setStatus(400);
            }
          }
        }else if (type.equals("delete")){
          final String id = req.getParameter("id");
          if (id==null){
            res.setStatus(400);
          }else{
            try{
              final int ID = Integer.parseInt(id);
              final Result<Byte> ret = Initializer.deleteSyncTask(authID,ID);
              ret.waitForResult(System.currentTimeMillis()+10000);
              final Byte b = ret.getResult();
              if (b==null){
                res.setStatus(504);
              }else if (b!=Protocol.SUCCESS){
                res.setStatus(403);
              }
            }catch(NumberFormatException e){
              res.setStatus(400);
            }
          }
        }else if (type.equals("save")){
          final String id = req.getParameter("id");
          final String desc = req.getParameter("desc");
          final String src = req.getParameter("src");
          final String dst = req.getParameter("dst");
          final String sync = req.getParameter("sync");
          final String all = req.getParameter("all");
          final String servers = req.getParameter("servers");
          if (id==null || desc==null || src==null || dst==null || sync==null || all==null || servers==null){
            res.setStatus(400);
          }else{
            try{
              final SyncTask t = new SyncTask(Integer.parseInt(id), desc, Long.parseLong(sync), src, dst, Boolean.parseBoolean(all));
              final String[] ids = servers.split(" ");
              for (int i=0;i<ids.length;++i){
                if (ids[i].length()>0){
                  t.add(Integer.parseInt(ids[i]));
                }
              }
              final Result<Byte> ret = Initializer.modifySyncTask(authID, t);
              ret.waitForResult(System.currentTimeMillis()+12000);
              final Byte b = ret.getResult();
              if (b==null){
                res.setStatus(504);
              }else if (b!=Protocol.SUCCESS){
                res.setStatus(403);
              }
            }catch(NumberFormatException e){
              res.setStatus(400);
            }
          }
        }else if (type.equals("create")){
          final String desc = req.getParameter("desc");
          final String src = req.getParameter("src");
          final String dst = req.getParameter("dst");
          final String sync = req.getParameter("sync");
          final String all = req.getParameter("all");
          if (desc==null || src==null || dst==null || sync==null || all==null){
            res.setStatus(400);
          }else{
            try{
              final Result<SyncStatus> ret = Initializer.createSyncTask(authID, desc, Long.parseLong(sync), src, dst, Boolean.parseBoolean(all));
              ret.waitForResult(System.currentTimeMillis()+15000);
              final SyncStatus stat = ret.getResult();
              if (stat==null){
                res.setStatus(504);
              }else if (stat.status!=Protocol.SUCCESS || stat.ID==-1){
                res.setStatus(403);
              }else{
                res.setContentType("text/plain");
                res.getWriter().print(stat.ID);
              }
            }catch(NumberFormatException e){
              res.setStatus(400);
            }
          }
        }else{
          res.sendError(400, "Invalid type parameter.");
        }
      }else{
        res.sendError(504, "A database connection is not currently active.");
      }
    }
  }
}