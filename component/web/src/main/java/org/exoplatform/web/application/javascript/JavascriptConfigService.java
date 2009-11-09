/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.web.application.javascript;

import org.exoplatform.container.ExoContainerContext;
import org.gatein.wci.impl.DefaultServletContainerFactory;
import org.picocontainer.Startable;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

public class JavascriptConfigService implements Startable
{

   private Collection<String> availableScripts_;

   private Collection<String> availableScriptsPaths_;

   private String mergedJavascript = "";

   private HashMap<String, String> extendedJavascripts;

   private ByteArrayOutputStream jsStream_ = null;

   /** . */
   private JavascriptDeployer deployer;

   private JavascriptRemoval removal;
   
   /** Used to clear merged Javascript on undeploying an webapp */
   private Map<String, List<String>> object_view_of_merged_JS;

   public JavascriptConfigService(ExoContainerContext context)
   {
      availableScripts_ = new ArrayList<String>();
      availableScriptsPaths_ = new ArrayList<String>();
      extendedJavascripts = new HashMap<String, String>();
      deployer = new JavascriptDeployer(context.getPortalContainerName(), this);
      removal = new JavascriptRemoval(context.getPortalContainerName(), this);
      object_view_of_merged_JS = new HashMap<String, List<String>>();
   }

   /**
    * return a collection list This method should return the availables scripts
    * in the service
    * 
    * @return
    */
   public Collection<String> getAvailableScripts()
   {
      return availableScripts_;
   }

   public Collection<String> getAvailableScriptsPaths()
   {
      return availableScriptsPaths_;
   }

   public void addExtendedJavascript(String module, String scriptPath, ServletContext scontext, String scriptData)
   {
      String servletContextName = scontext.getServletContextName();
      String path = "/" + servletContextName + scriptPath;
      availableScripts_.add(module);
      availableScriptsPaths_.add(path);
      extendedJavascripts.put(path, scriptData);
   }
   
   public void addJavascript(JavascriptKey key, ServletContext scontext){
      addJavascript(key.getModule(), key.getScriptPath(), scontext);
   }

   public void addJavascript(String module, String scriptPath, ServletContext scontext)
   {
      String servletContextName = scontext.getServletContextName();
      availableScripts_.add(module);
      availableScriptsPaths_.add("/" + servletContextName + scriptPath);
      
      List<String> mergedJS_list = object_view_of_merged_JS.get("/" + servletContextName);
      if(mergedJS_list == null){
         mergedJS_list = new ArrayList<String>();
         object_view_of_merged_JS.put("/" + servletContextName, mergedJS_list);
      }
      
      StringBuffer sB = new StringBuffer();
      String line = "";
      try
      {
         BufferedReader reader = new BufferedReader(new InputStreamReader(scontext.getResourceAsStream(scriptPath)));
         try
         {
            while ((line = reader.readLine()) != null)
            {
               line = line + "\n";
               sB.append(line);
               mergedJS_list.add(line);
            }
         }
         catch (Exception ex)
         {
            ex.printStackTrace();
         }
         finally
         {
            try
            {
               reader.close();
            }
            catch (Exception ex)
            {
            }
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
      sB.append("\n");
      mergedJS_list.add("\n");
      
      mergedJavascript = mergedJavascript.concat(sB.toString());
   }
   
   public void removeJavascript(JavascriptKey key, ServletContext scontext){
      String contextPath = scontext.getContextPath();
      availableScripts_.remove(key.getModule());
      availableScriptsPaths_.remove(contextPath + key.getScriptPath());
      object_view_of_merged_JS.remove(contextPath);
   }
   
   /** Refresh the mergedJavascript **/
   public void refreshMergedJavascript(){
      mergedJavascript = "";
      StringBuffer buffer = new StringBuffer();
      for(String webApp : object_view_of_merged_JS.keySet()){
         for(String jsPath : object_view_of_merged_JS.get(webApp)){
            buffer.append(jsPath);
         }
      }
      mergedJavascript = buffer.toString();
   }

   public byte[] getMergedJavascript()
   {
      if (jsStream_ == null)
      {
         jsStream_ = new ByteArrayOutputStream();
         StringBuffer allJavascript = new StringBuffer();
         allJavascript.append(mergedJavascript);
         for (String script : extendedJavascripts.values())
         {
            allJavascript.append(script);
         }
         ByteArrayInputStream input = new ByteArrayInputStream(allJavascript.toString().getBytes());
         JSMin jsMin = new JSMin(input, jsStream_);
         try
         {
            jsMin.jsmin();
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      }
      return jsStream_.toByteArray();
   }

   public boolean isModuleLoaded(CharSequence module)
   {
      return getAvailableScripts().contains(module);
   }

   public void removeExtendedJavascript(String module, String scriptPath, ServletContext scontext)
   {
      String servletContextName = scontext.getServletContextName();
      availableScripts_.remove(module);
      String path = "/" + servletContextName + scriptPath;
      availableScriptsPaths_.remove(path);
      extendedJavascripts.remove(path);
      jsStream_ = null;
   }

   public void start()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(deployer);
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(removal);
   }

   public void stop()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(deployer);
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(removal);
   }
   
}