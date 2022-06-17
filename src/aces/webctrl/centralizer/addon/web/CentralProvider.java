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
    try{
      if (op!=null && op instanceof CentralOperator && (SecureServlet.allowUnsecureProtocol || req.isSecure())){
        CentralOperator cop = (CentralOperator)op;
        if (cop.changePassword() && Initializer.isConnected()){
          RequestDispatcher dis = req.getServletContext().getContext(Initializer.getPrefix()).getRequestDispatcher("/ChangePassword");
          if (dis==null){
            Logger.logAsync("Failed to retrieve RequestDispatcher for password change servlet.");
          }else{
            dis.forward(req, res);
            op = null;
          }
        }
      }
    }catch(Throwable e){
      Logger.logAsync("Error occurred while forwarding to password change servlet.", e);
    }
    return op;
  }
  @Override public WebOperator validate(String username, char[] password, String host) throws ValidationException {
    try{
      int i = username.indexOf('(');
      int j = username.lastIndexOf(')');
      WebOperator builtin = null;
      if (i!=-1 && i<j){
        try{
          builtin = getBuiltinOperator(username.substring(i+1,j));
          username = username.substring(0,i);
        }catch(Throwable e){}
      }
      Result<OperatorStatus> ret = Initializer.login(username, Utility.toBytes(password));
      if (ret.waitForResult(System.currentTimeMillis()+10000)){
        OperatorStatus stat = ret.getResult();
        if (stat==null){
          stat = new OperatorStatus();
          stat.operator = Operators.get(username);
          stat.status = stat.operator==null?Protocol.DOES_NOT_EXIST:Protocol.SUCCESS;
        }
        if (stat.status==Protocol.DOES_NOT_EXIST){
          return super.validate(username, password, host);
        }else if (stat.status==Protocol.LOCKED_OUT){
          throw new ValidationException("Operator has been locked out.");
        }else if (stat.status==Protocol.FAILURE){
          throw new InvalidCredentialsException();
        }else if (stat.status==Protocol.SUCCESS || stat.status==Protocol.CHANGE_PASSWORD){
          if (builtin==null){
            return new CentralOperator(stat.operator, stat.status==Protocol.CHANGE_PASSWORD);
          }else{
            Logger.logAsync(stat.operator.getUsername()+" hijacked builtin operator "+builtin.getLoginName());
            return builtin;
          }
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