/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.centralizer.addon.web;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.web.menus.*;
public class SystemMenuEditor implements SystemMenuProvider{
  @Override public void updateMenu(Operator op, Menu menu){
    menu.addMenuEntry(MenuEntryFactory
      .newEntry("aces.webctrl.centralizer.ManageOperators")
      .display("Central Operators")
      .action(Actions.openWindow("ManageOperators"))
      .create()
    );
  }
}