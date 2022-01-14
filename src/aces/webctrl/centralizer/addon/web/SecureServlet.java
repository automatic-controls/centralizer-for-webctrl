package aces.webctrl.centralizer.addon.web;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Ensures all data is sent and received securely with {@code UTF-8} character encoding.
 * The response will be committed with error code {@code 403} when an insecure connection is detected.
 * GET and POST requests are supported.
 */
public abstract class SecureServlet extends HttpServlet {
  @Override public void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override public void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    try{
      req.setCharacterEncoding("UTF-8");
      res.setCharacterEncoding("UTF-8");
      if (req.isSecure()){
        process(req,res);
      }else{
        res.sendError(403, "You are using an insecure connection protocol.");
      }
    }catch(Exception e){
      if (e instanceof ServletException){
        throw (ServletException)e;
      }else if (e instanceof IOException){
        throw (IOException)e;
      }else{
        throw new ServletException(e);
      }
    }
  }
  public abstract void process(HttpServletRequest req, HttpServletResponse res) throws Exception;
  /**
   * @return the {@code CentralOperator} attached to the given request, or {@code null} if none is attached.
   */
  public static CentralOperator getOperator(HttpServletRequest req) throws com.controlj.green.addonsupport.InvalidConnectionRequestException {
    com.controlj.green.addonsupport.access.Operator webop = com.controlj.green.addonsupport.access.DirectAccess.getDirectAccess().getUserSystemConnection(req).getOperator();
    return webop instanceof CentralOperator ? (CentralOperator)webop : null;
  }
}