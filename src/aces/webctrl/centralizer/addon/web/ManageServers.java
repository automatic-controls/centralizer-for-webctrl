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
public class ManageServers extends SecureServlet {
  private final static Comparator<Server> serverCompar = new Comparator<Server>(){
    @Override public int compare(Server a, Server b){
      return a.getName().compareTo(b.getName());
    }
  };
  private volatile static String html = null;
  public ManageServers(){
    super(java.util.Collections.singleton("view_administrator_only"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/html/ManageServers.html").replaceAll(
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
      if ((op.getPermissions()&Permissions.ADMINISTRATOR)==0){
        res.sendError(403, "Only database administrators can view this page.");
      }else if (Initializer.isConnected()){
        final String type = req.getParameter("type");
        if (type==null){
          res.setContentType("text/html");
          res.getWriter().print(html);
        }else{
          final int authID = op.getID();
          switch (type){
            case "refresh":{
              final Result<ServerList> ret = Initializer.getServerList(authID);
              ret.waitForResult(System.currentTimeMillis()+20000);
              final ServerList list = ret.getResult();
              if (list==null){
                res.setStatus(504);
              }else if (list.status==Protocol.SUCCESS){
                final StringBuilder sb = new StringBuilder(list.servers.size()<<8);
                list.servers.sort(serverCompar);
                for (Server s:list.servers){
                  sb.append(s.getID()).append(';');
                  sb.append(Utility.encodeAJAX(s.getName())).append(';');
                  sb.append(Utility.encodeAJAX(s.getDescription())).append(';');
                  sb.append(Utility.encodeAJAX(s.getIPAddress())).append(';');
                  sb.append(s.isConnected()).append(';');
                  sb.append(Utility.encodeAJAX(Logger.format.format(new Date(s.getLastConnectionTime())))).append(';');
                  sb.append(Utility.encodeAJAX(Logger.format.format(new Date(s.getCreationTime())))).append(';');
                }
                res.setContentType("text/plain");
                res.getWriter().print(sb.toString());
              }else{
                res.setStatus(403);
              }
              break;
            }
            case "save":{
              final String ID = req.getParameter("ID");
              final String name = req.getParameter("name");
              final String desc = req.getParameter("desc");
              if (ID==null || name==null || desc==null){
                res.setStatus(400);
              }else{
                try{
                  ArrayList<ServerModification> list = new ArrayList<ServerModification>(2);
                  list.add(ServerModification.changeName(name));
                  list.add(ServerModification.changeDescription(desc));
                  Result<Byte> ret = Initializer.modifyServer(authID, Integer.parseInt(ID), list);
                  ret.waitForResult(System.currentTimeMillis()+15000);
                  Byte b = ret.getResult();
                  if (b==null){
                    res.setStatus(504);
                  }else if (b!=Protocol.SUCCESS){
                    res.setStatus(403);
                  }
                }catch(NumberFormatException e){
                  res.setStatus(400);
                }
              }
              break;
            }
            case "delete":{
              final String ID = req.getParameter("ID");
              if (ID==null){
                res.setStatus(400);
              }else{
                try{
                  Result<Byte> ret = Initializer.deleteServer(authID, Integer.parseInt(ID));
                  ret.waitForResult(System.currentTimeMillis()+10000);
                  Byte b = ret.getResult();
                  if (b==null){
                    res.setStatus(504);
                  }else if (b!=Protocol.SUCCESS){
                    res.setStatus(403);
                  }
                }catch(NumberFormatException e){
                  res.setStatus(400);
                }
              }
              break;
            }
            default:{
              res.sendError(400);
            }
          }
        }
      }else{
        res.sendError(504, "Not currently connected to the centralizer database.");
      }
    }
  }
}