1. Edit build.properties - supply the jdbc.username and jdbc.password properties.
2. Run the 'ant war' target.

If you are deploying on Tomcat:
1. Stop Tomcat.
2. Copy build/dist/mods.war to <tomcathome>/webapps.
3. Copy jtds-1.0.2.jar to <tomcathome>/common/lib.
4. Start Tomcat.

If you are deploying on JBoss:
1. Stop Jboss.
2. Copy build/dist/mods.war to <jbosshome>/deploy.
3. Copy build/dist/11sybase-ds.xml to <jbosshome>/deploy.
4. Copy jtds-1.0.2.jar to <jbosshome>/lib.
5. Start Jboss.

Now you can get a Marc record for a certain bib by bringing up:
http://hostname/mods?bib=<bib #>&format=mods

Valid values for the 'format' parameter are:
mods (the default if the format parameter is not specified)
rdf
oai
srw
marc

You can visit the Wiki page for the MODS Servlet at https://wiki.library.jhu.edu/x/KhI.
Please report any issues via Jira in the SYSHELP project, component "Horizon: MODS Servlet".