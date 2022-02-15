/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.web;
import aces.webctrl.centralizer.addon.Utility;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
public class LocalOperatorList extends SecureServlet {
  private volatile static String html = null;
  public LocalOperatorList(){
    super(Collections.singleton("view_administrator_only"));
  }
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/centralizer/addon/web/LocalOperatorList.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
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
    Map<String,String> map = new com.controlj.green.datatable.util.CoreHelper().getOperatorList();
    StringBuilder sb = new StringBuilder(map.size()<<5);
    for (Map.Entry<String,String> entry:map.entrySet()){
      sb.append("<tr><td>").append(Utility.escapeHTML(entry.getKey())).append("</td><td>").append(Utility.escapeHTML(entry.getValue())).append("</td></tr>\n");
    }
    res.setContentType("text/html");
    res.getWriter().print(html.replace("__LIST__", sb.toString()));
  }
}