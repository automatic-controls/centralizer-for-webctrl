package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.core.*;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.web.auth.*;
public class CentralProvider extends StandardWebOperatorProvider {
  @Override public void logout(WebOperator op, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
    if (op instanceof CentralOperator && Initializer.isConnected()){
      Initializer.logout(((CentralOperator)op).getOperator().getID());
    }
    super.logout(op,req,res);
  }
  @Override public WebOperator login(HttpServletRequest req, HttpServletResponse res) throws ValidationException, IOException, ServletException {
    WebOperator op = super.login(req,res);
    if (op!=null && op instanceof CentralOperator){
      CentralOperator cop = (CentralOperator)op;
      if (cop.changePassword() && Initializer.isConnected()){
        res.sendRedirect(Initializer.getPrefix()+"ChangePassword");
      }
    }
    return op;
  }
  @Override public WebOperator validate(final String username, char[] password, String host) throws ValidationException {
    try{
      Result<OperatorStatus> ret = Initializer.login(username, Utility.toBytes(password));
      if (ret.waitForResult(System.currentTimeMillis()+20000)){
        OperatorStatus stat = ret.getResult();
        if (stat==null){
          throw new ValidationException("Database connection has been reset. Please try again later.");
        }else if (stat.status==Protocol.DOES_NOT_EXIST){
          return super.validate(username, password, host);
        }else if (stat.status==Protocol.LOCKED_OUT){
          throw new ValidationException("Operator has been locked out.");
        }else if (stat.status==Protocol.FAILURE){
          throw new InvalidCredentialsException();
        }else if (stat.status==Protocol.SUCCESS || stat.status==Protocol.CHANGE_PASSWORD){
          return new CentralOperator(stat.operator, stat.status==Protocol.CHANGE_PASSWORD);
        }
      }else{
        throw new ValidationException("Validation request exceeded timeout. Please try again later.");
      }
    }catch(InterruptedException e){
      Logger.logAsync("Interrupted while validating operator "+username+".", e);
    }
    return super.validate(username, password, host);
  }
}