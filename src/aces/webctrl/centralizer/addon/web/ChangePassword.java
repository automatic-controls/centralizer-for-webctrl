package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.core.*;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
public class ChangePassword extends SecureServlet {
  private volatile static String html = null;
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/web/ChangePassword.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
      ).replace(
        "__PREFIX__",
        Initializer.getPrefix()
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
    if (Initializer.isConnected()){
      CentralOperator webop = getOperator(req);
      if (webop!=null){
        final aces.webctrl.centralizer.common.Operator op = webop.getOperator();
        final PrintWriter out = res.getWriter();
        if (req.getParameter("submit")!=null){//AJAX request to submit new password
          res.setContentType("text/plain");
          char[] oldPassword = req.getParameter("oldPassword").toCharArray();
          Utility.obfuscate(oldPassword);
          byte[] oldPasswordBytes = Utility.toBytes(oldPassword);
          java.util.Arrays.fill(oldPassword,(char)0);
          oldPassword = null;
          //Prevents the response from being committed more than once in separate threads (AsyncAdapter.onTimeout and Result.setConsumer)
          final Object asyncLock = new Object();
          final AsyncContext async = req.startAsync();
          async.setTimeout(20000);
          async.addListener(new AsyncAdapter(){
            @Override public void onTimeout(AsyncEvent e){
              synchronized (asyncLock){
                if (res.isCommitted()){
                  return;
                }
                out.write('0');
                async.complete();
              }
            }
          });
          Initializer.login(op.getUsername(), oldPasswordBytes).onResult(new java.util.function.Consumer<OperatorStatus>(){
            public void accept(OperatorStatus opstat){
              synchronized (asyncLock){
                if (res.isCommitted()){
                  return;
                }
                byte stat = opstat.status;
                if (Initializer.isConnected() && (stat==Protocol.SUCCESS || stat==Protocol.CHANGE_PASSWORD)){
                  char[] password = req.getParameter("newPassword").toCharArray();
                  Utility.obfuscate(password);
                  byte[] passwordBytes = Utility.toBytes(password);
                  java.util.Arrays.fill(password,(char)0);
                  password = null;
                  int id = op.getID();
                  Initializer.modifyOperator(id, id, java.util.Arrays.asList(OperatorModification.changePassword(passwordBytes))).onResult(new java.util.function.Consumer<Byte>(){
                    public void accept(Byte b){
                      synchronized (asyncLock){
                        if (res.isCommitted()){
                          return;
                        }
                        out.write(b==Protocol.SUCCESS?'1':'0');
                        async.complete();
                      }
                    }
                  });
                }else{
                  out.write('0');
                  async.complete();
                }
              }
            }
          });
        }else{//Send HTML document that allows the operator to change his or her password
          res.setContentType("text/html");
          out.print(html.replace("__USERNAME__", op.getUsername()));
          out.flush();
        }
      }else{
        res.sendError(403, "Local operators cannot change their password using this webpage.");
      }
    }else{
      res.sendError(500, "WebCTRL is not currently connected to the credential database. Please try again later.");
    }
  }
}