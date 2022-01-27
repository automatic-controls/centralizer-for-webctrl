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
public class LoginServlet extends SecureServlet {
  private volatile static String html = null;
  public LoginServlet(){
    super(java.util.Collections.singleton("login"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/web/LoginPage.html").replaceAll(
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
    if (Initializer.isConnected()){
      CentralOperator webop = getOperator(req);
      if (webop!=null){
        final Operator op = webop.getOperator();
        if (req.getParameter("submit")!=null){
          res.setContentType("text/plain");
          char[] password = req.getParameter("password").toCharArray();
          Utility.obfuscate(password);
          byte[] passwordBytes = Utility.toBytes(password);
          java.util.Arrays.fill(password,(char)0);
          password = null;
          Result<OperatorStatus> ret = Initializer.login(op.getUsername(), passwordBytes);
          if (ret.waitForResult(System.currentTimeMillis()+10000)){
            OperatorStatus opstat = ret.getResult();
            byte stat = opstat==null?Protocol.FAILURE:opstat.status;
            if (stat!=Protocol.SUCCESS && stat!=Protocol.CHANGE_PASSWORD){
              res.setStatus(403);
            }
          }else{
            res.setStatus(504);
          }
        }else{
          res.setContentType("text/html");
          res.getWriter().print(html.replace("__USERNAME__", op.getUsername()));
        }
      }else{
        res.sendError(403, "Local operators cannot access this webpage.");
      }
    }else{
      res.sendError(504, "WebCTRL is not currently connected to the credential database. Please try again later.");
    }
  }
}
