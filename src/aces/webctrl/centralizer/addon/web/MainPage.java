package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.core.*;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
public class MainPage extends SecureServlet {
  private volatile static String html = null;
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/web/MainPage.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
      ).replace(
        "__PREFIX__",
        Initializer.getPrefix()
      ).replace(
        "__VERSION__",
        'v'+Config.VERSION
      );
    }catch(Exception e){
      if (e instanceof ServletException){
        throw (ServletException)e;
      }else{
        throw new ServletException(e);
      }
    }
  }
  @Override public void process(final HttpServletRequest req, final HttpServletResponse res) throws Exception {
    if (req.isUserInRole("view_administrator_only")){
      final PrintWriter out = res.getWriter();
      if (req.getParameter("status")!=null){
        res.setContentType("text/plain");
        out.print(Initializer.getStatus());
        out.flush();
      }else if (req.getParameter("blankConfig")!=null){
        final String user = req.getParameter("user");
        final String pass = req.getParameter("pass");
        final String dis = req.getParameter("dis");
        final String nav = req.getParameter("nav");
        final String desc = req.getParameter("desc");
        if (user==null || pass==null || dis==null || nav==null || desc==null){
          res.sendError(400);
        }else{
          int navNum;
          try{
            navNum = Integer.parseInt(nav);
          }catch(Exception e){
            res.sendError(400);
            return;
          }
          char[] password = pass.toCharArray();
          Utility.obfuscate(password);
          if (Initializer.configureBlank(user, password, dis, navNum, desc)){
            out.flush();
          }else{
            res.sendError(400);
          }
        }
      }else if (req.getParameter("config")!=null){
        final String host = req.getParameter("host");
        final String port = req.getParameter("port");
        final String backupTime = req.getParameter("backupTime");
        final String timeout = req.getParameter("timeout");
        final String deleteLog = req.getParameter("deleteLog");
        final String serverName = req.getParameter("serverName");
        final String serverDesc = req.getParameter("serverDesc");
        final String packetCapture = req.getParameter("packetCapture");
        if (host==null || port==null || backupTime==null || timeout==null || deleteLog==null || serverName==null || serverDesc==null || packetCapture==null){
          res.sendError(400);
        }else{
          try{
            final int portNum = Integer.parseInt(port);
            final long timeoutNum = Long.parseLong(timeout);
            final long deleteLogNum = Long.parseLong(deleteLog);
            final boolean packets = Boolean.parseBoolean(packetCapture);
            final String[] arr = backupTime.split(":");
            if (arr.length!=3){
              res.sendError(400);
              return;
            }
            final int backupHr = Integer.parseInt(arr[0]);
            final int backupMin = Integer.parseInt(arr[1]);
            final int backupSec = Integer.parseInt(arr[2]);
            if (backupHr<0 || backupHr>23 || backupMin<0 || backupMin>59 || backupSec<0 || backupSec>59){
              res.sendError(400);
              return;
            }
            if (packets){
              PacketLogger.start();
            }else{
              PacketLogger.stop();
            }
            ClientConfig.backupHr = backupHr;
            ClientConfig.backupMin = backupMin;
            ClientConfig.backupSec = backupSec;
            ClientConfig.deleteLogAfter = deleteLogNum;
            ClientConfig.timeout = timeoutNum;
            ClientConfig.ipLock.readLock().lock();
            boolean changeIP = !host.equals(ClientConfig.host) || portNum!=ClientConfig.port;
            ClientConfig.ipLock.readLock().unlock();
            String prevName = ClientConfig.name;
            String prevDesc = ClientConfig.description;
            ClientConfig.name = serverName;
            ClientConfig.description = serverDesc;
            if (changeIP){
              ClientConfig.ipLock.writeLock().lock();
              ClientConfig.host = host;
              ClientConfig.port = portNum;
              ClientConfig.ipLock.writeLock().unlock();
              Initializer.forceDisconnect();
              ClientConfig.ID = -1;
              ClientConfig.identifier = null;
              ClientConfig.databaseKey = null;
              out.flush();
            }else if (!serverName.equals(prevName) || !serverDesc.equals(prevDesc)){
              if (Initializer.isConnected()){
                CentralOperator webop = getOperator(req);
                if (webop==null){
                  res.sendError(401);
                }else{
                  Operator op = webop.getOperator();
                  if ((op.getPermissions()&Permissions.ADMINISTRATOR)==0){
                    res.sendError(401);
                  }else{
                    LinkedList<ServerModification> list = new LinkedList<ServerModification>();
                    if (!serverName.equals(prevName)){
                      list.add(ServerModification.changeName(serverName));
                    }
                    if (!serverDesc.equals(prevDesc)){
                      list.add(ServerModification.changeDescription(serverDesc));
                    }
                    final Object asyncLock = new Object();
                    final AsyncContext async = req.startAsync();
                    async.setTimeout(20000);
                    async.addListener(new AsyncAdapter(){
                      @Override public void onTimeout(AsyncEvent e){
                        synchronized (asyncLock){
                          if (res.isCommitted()){
                            return;
                          }
                          res.setStatus(504);
                          async.complete();
                        }
                      }
                    });
                    Initializer.modifyServer(op.getID(),ClientConfig.ID,list).onResult(new java.util.function.Consumer<Byte>(){
                      public void accept(Byte b){
                        synchronized (asyncLock){
                          if (res.isCommitted()){
                            return;
                          }
                          if (b!=Protocol.SUCCESS){
                            res.setStatus(403);
                          }
                          async.complete();
                        }
                      }
                    });
                  }
                }
              }else{
                res.sendError(504);
              }
            }else{
              out.flush();
            }
          }catch(Exception e){
            res.sendError(400);
          }
        }
      }else{
        res.setContentType("text/html");
        ClientConfig.ipLock.readLock().lock();
        String host = ClientConfig.host;
        int port = ClientConfig.port;
        ClientConfig.ipLock.readLock().unlock();
        if (host==null){
          host = "";
        }
        out.print(html.replace(
          "__PACKET_CAPTURE__",
          String.valueOf(PacketLogger.isWorking())
        ).replace(
          "__HOST__",
          Utility.escapeJS(host)
        ).replace(
          "__PORT__",
          String.valueOf(port)
        ).replace(
          "__BACKUP_TIME__",
          ClientConfig.backupHr+":"+ClientConfig.backupMin+":"+ClientConfig.backupSec
        ).replace(
          "__TIMEOUT__",
          String.valueOf(ClientConfig.timeout)
        ).replace(
          "__DELETE_LOG__",
          String.valueOf(ClientConfig.deleteLogAfter)
        ).replace(
          "__SERVER_NAME__",
          ClientConfig.name==null?"":ClientConfig.name
        ).replace(
          "__SERVER_DESC__",
          ClientConfig.description==null?"":Utility.escapeJS(ClientConfig.description)
        ));
        out.flush();
      }
    }else{
      res.sendError(401);
    }
  }
}
