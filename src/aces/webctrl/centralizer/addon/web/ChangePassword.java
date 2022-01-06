package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.core.*;
import aces.webctrl.centralizer.addon.Utility;
import aces.webctrl.centralizer.common.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.access.*;
public class ChangePassword extends HttpServlet {
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    try{
      req.setCharacterEncoding("UTF-8");
      res.setCharacterEncoding("UTF-8");
      if (Initializer.isConnected()){
        com.controlj.green.addonsupport.access.Operator webop = DirectAccess.getDirectAccess().getUserSystemConnection(req).getOperator();
        if (webop instanceof CentralOperator){
          aces.webctrl.centralizer.common.Operator op = ((CentralOperator)webop).getOperator();
          PrintWriter out = res.getWriter();
          if (req.getParameter("submit")!=null){//AJAX request to submit new password
            res.setContentType("text/plain");
            boolean success = false;
            char[] oldPassword = req.getParameter("oldPassword").toCharArray();
            Utility.obfuscate(oldPassword);
            byte[] oldPasswordBytes = Utility.toBytes(oldPassword);
            java.util.Arrays.fill(oldPassword,(char)0);
            oldPassword = null;
            Result<OperatorStatus> opRet = Initializer.login(op.getUsername(), oldPasswordBytes);
            if (!opRet.waitForResult(0)){
              Initializer.pingNow();
            }
            if (opRet.waitForResult(System.currentTimeMillis()+Utility.ASYNC_TIMEOUT)){
              byte stat = opRet.getResult().status;
              if (Initializer.isConnected() && (stat==Protocol.SUCCESS || stat==Protocol.CHANGE_PASSWORD)){
                char[] password = req.getParameter("newPassword").toCharArray();
                Utility.obfuscate(password);
                byte[] passwordBytes = Utility.toBytes(password);
                java.util.Arrays.fill(password,(char)0);
                password = null;
                int id = op.getID();
                Result<Byte> ret = Initializer.modifyOperator(id, id, java.util.Arrays.asList(OperatorModification.changePassword(passwordBytes)));
                Initializer.pingNow();
                if (ret.waitForResult(System.currentTimeMillis()+Utility.ASYNC_TIMEOUT) && ret.getResult().byteValue()==Protocol.SUCCESS){
                  success = true;
                }
              }
            }
            out.write(success?'1':'0');
          }else{//Send HTML document that allows the operator to change his or her password
            res.setContentType("text/html");
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>");
            out.println("Password Changer");
            out.println("</title>");
            out.println("<style>");
            out.println("body {");
            out.println("background-color:black;");
            out.println("color:white;");
            out.println("}");
            out.println(".e {");
            out.println("margin-left:0.5em;");
            out.println("margin-right:0.5em;");
            out.println("margin-top:0.25em;");
            out.println("margin-bottom:0.25em;");
            out.println("}");
            out.println("</style>");
            out.println("<script>");
            out.println("function obfuscate(str){");
            out.println("let s = \"\"");
            out.println("for (var i=str.length-1;i>=0;--i){");
            out.println("s = s.concat(String.fromCharCode(str.charCodeAt(i)^4));");
            out.println("}");
            out.println("return s");
            out.println("}");
            out.println("function finish(){");
            out.println("window.location.href = \"/\"");
            out.println("}");
            out.println("function enableForm(){");
            out.println("submitButton.disabled = false");
            out.println("cancelButton.disabled = false");
            out.println("oldPassword.disabled = false");
            out.println("newPassword.disabled = false");
            out.println("conPassword.disabled = false");
            out.println("}");
            out.println("function submitChanges(){");
            out.println("submitButton.disabled = true");
            out.println("cancelButton.disabled = true");
            out.println("oldPassword.disabled = true");
            out.println("newPassword.disabled = true");
            out.println("conPassword.disabled = true");
            out.println("let reenable = true");
            out.println("let oldPass = oldPassword.value");
            out.println("let newPass = newPassword.value");
            out.println("if (newPass!=conPassword.value){");
            out.println("statusText.innerHTML = \"Passwords do not match.\"");
            out.println("}else if (newPass.length<8){");
            out.println("statusText.innerHTML = \"Password must be at least 8 characters long.\"");
            out.println("}else if (newPass.length>128){");
            out.println("statusText.innerHTML = \"Password can be at most 128 characters long.\"");
            out.println("}else if (oldPass.length==0){");
            out.println("statusText.innerHTML = \"Please enter your current password.\"");
            out.println("}else{");
            out.println("let min = newPass.charCodeAt(0)");
            out.println("let max = min");
            out.println("for (var i=1;i<newPass.length;++i){");
            out.println("var j = newPass.charCodeAt(i)");
            out.println("if (j<min){");
            out.println("min = j;");
            out.println("}");
            out.println("if (j>max){");
            out.println("max = j;");
            out.println("}");
            out.println("}");
            out.println("if (max-min>12){");
            out.println("statusText.innerHTML = \"Submitting...\"");
            out.println("reenable = false");
            out.println("oldPass = obfuscate(oldPass)");
            out.println("newPass = obfuscate(newPass)");
            out.println("let req = new XMLHttpRequest()");
            out.println("req.open(\"POST\",\"/"+Initializer.getName()+"/ChangePassword\",true)");
            out.println("req.setRequestHeader(\"content-type\", \"application/x-www-form-urlencoded\")");
            out.println("req.onreadystatechange = function(){");
            out.println("if (this.readyState==4){");
            out.println("if (this.status==200){");
            out.println("if (this.responseText==\"1\"){");
            out.println("finish()");
            out.println("}else{");
            out.println("statusText.innerHTML = \"Password change failed.\"");
            out.println("enableForm()");
            out.println("}");
            out.println("}else{");
            out.println("statusText.innerHTML = \"Password change failed with error code \"+this.status+\".\"");
            out.println("enableForm()");
            out.println("}");
            out.println("}");
            out.println("}");
            out.println("req.send(\"submit&oldPassword=\"+encodeURIComponent(oldPass)+\"&newPassword=\"+encodeURIComponent(newPass))");
            out.println("}else{");
            out.println("statusText.innerHTML = \"Complexity requirements have not been met. Try adding special characters.\"");
            out.println("}");
            out.println("}");
            out.println("if (reenable){");
            out.println("enableForm()");
            out.println("}");
            out.println("}");
            out.println("</script>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div style=\"text-align:center\">");
            out.println("<h1>Change Your Password</h1>");
            out.println("<div class=\"e\">Username: "+op.getUsername()+"</div>");
            out.println("<label for=\"oldPassword\">Current Password:</label>");
            out.println("<input id=\"oldPassword\" class=\"e\" type=\"password\" autocomplete=\"off\">");
            out.println("<br>");
            out.println("<label for=\"newPassword\">New Password:</label>");
            out.println("<input id=\"newPassword\" class=\"e\" type=\"password\" autocomplete=\"off\">");
            out.println("<br>");
            out.println("<label for=\"conPassword\">Confirm Password:</label>");
            out.println("<input id=\"conPassword\" class=\"e\" type=\"password\" autocomplete=\"off\">");
            out.println("<br>");
            out.println("<button id=\"cancelButton\" class=\"e\" onclick=\"finish()\">Cancel</button>");
            out.println("<button id=\"submitButton\" class=\"e\" onclick=\"submitChanges()\">Submit</button>");
            out.println("<h3 id=\"statusText\" class=\"e\" style=\"color:red\"></h3>");
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
          }
          out.flush();
        }else{
          //Consider redirecting to the correct password change webpage instead of sending this error
          res.sendError(403, "Local operators cannot change their password using this webpage.");
        }
      }else{
        res.sendError(500, "WebCTRL is not currently connected to the credential database. Please try again later.");
      }
    }catch(Exception e){
      throw new ServletException(e);
    }
  }
}