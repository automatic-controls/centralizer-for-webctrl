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
import javax.servlet.*;
import javax.servlet.http.*;
public class ChangePassword extends SecureServlet {
  private volatile static String html = null;
  public ChangePassword(){
    super(null);
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/html/ChangePassword.html").replaceAll(
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
    if (Initializer.isConnected()){
      final PrintWriter out = res.getWriter();
      if (req.getParameter("submit")!=null){
        res.setContentType("text/plain");
        final String username = req.getParameter("username");
        final String oldPass = req.getParameter("oldPassword");
        final String newPass = req.getParameter("newPassword");
        if (username==null || oldPass==null || newPass==null){
          res.setStatus(400);
        }else{
          Operator op = Operators.get(username);
          if (op==null){
            res.setStatus(400);
          }else{
            char[] oldPassword = oldPass.toCharArray();
            Utility.obfuscate(oldPassword);
            byte[] oldPasswordBytes = Utility.toBytes(oldPassword);
            java.util.Arrays.fill(oldPassword,(char)0);
            oldPassword = null;
            Result<OperatorStatus> ret = Initializer.login(op.getUsername(), oldPasswordBytes);
            if (ret.waitForResult(System.currentTimeMillis()+10000)){
              OperatorStatus opstat = ret.getResult();
              byte stat = opstat==null?Protocol.FAILURE:opstat.status;
              if (Initializer.isConnected() && (stat==Protocol.SUCCESS || stat==Protocol.CHANGE_PASSWORD)){
                char[] password = newPass.toCharArray();
                Utility.obfuscate(password);
                byte[] passwordBytes = Utility.toBytes(password);
                java.util.Arrays.fill(password,(char)0);
                password = null;
                int id = op.getID();
                Result<Byte> ret2 = Initializer.modifyOperator(id, id, java.util.Arrays.asList(OperatorModification.changePassword(passwordBytes)));
                if (ret2.waitForResult(System.currentTimeMillis()+12000)){
                  Byte b = ret2.getResult();
                  if (b!=null && b==Protocol.SUCCESS){
                    out.write('1');
                  }else{
                    out.write('0');
                  }
                }else{
                  out.write('0');
                }
              }else{
                out.write('0');
              }
            }else{
              out.write('0');
            }
          }
        }
      }else{
        final String name = req.getParameter("name");
        final String pass = req.getParameter("pass");
        final String touchscr = req.getParameter("touchscr");
        final String loginTracker = req.getParameter("login-tracker");
        final String loginAuthTok = req.getParameter("login-auth-tok");
        if (name==null || pass==null || touchscr==null || loginTracker==null || loginAuthTok==null){
          if (webop==null){
            res.sendError(403, "Local operators cannot change their password using this webpage.");
          }else{
            final Operator op = webop.getOperator();
            res.setContentType("text/html");
            out.print(html.replace(
              "__USERNAME__",
              op.getUsername()
            ).replace(
              "__PASSWORD__",
              ""
            ).replace(
              "__REDIRECT_TO_LOGIN__",
              "false"
            ));
          }
        }else{
          res.setContentType("text/html");
          res.getWriter().print(html.replace(
            "__USERNAME__",
            name
          ).replace(
            "__PASSWORD__",
            Utility.escapeJS(new String(Utility.obfuscate(pass.toCharArray())))
          ).replace(
            "__REDIRECT_TO_LOGIN__",
            "true"
          ).replace(
            "__TOUCHSCR__",
            touchscr
          ).replace(
            "__LOGIN_TRACKER__",
            loginTracker
          ).replace(
            "__LOGIN_AUTH_TOK__",
            loginAuthTok
          ).replace(
            "css/main.css",
            Initializer.getName()+"/css/main.css"
          ));
        }
      }
    }else{
      res.sendError(504, "WebCTRL is not currently connected to the credential database. Please try again later.");
    }
  }
}