package org.spl;

import java.sql.*;
import javax.naming.*;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import org.xml.sax.*;
// marc4j stuff for handling ANSEL -> Unicode
import org.marc4j.converter.impl.*;
import org.marc4j.converter.*;

// cache stuff
//import com.opensymphony.oscache.general.*;
//import com.opensymphony.oscache.base.*;
//import com.opensymphony.oscache.base.algorithm.*;

public class ModsServlet extends HttpServlet  {
	private static String dsn = "mods";
	private static Context ctx;
	private static String prefix;
	private static String realPath;
	private static DataSource ds;	
	private static PreparedStatement pstmt;
	private static Connection conn;
  
  	private static String modsXSL;
	private static String rdfXSL;
	private static String oaiXSL;
	private static String srwXSL;
	
	private static AnselToUnicode converter;

  public void init(ServletConfig config) throws ServletException {
      try {
        ctx = getInitialContext();
        prefix = System.getProperty("dynix.context.ds.lookup.prefix", "java:/");
        ds = (DataSource)PortableRemoteObject.narrow(ctx.lookup(prefix + dsn), javax.sql.DataSource.class);
        realPath = config.getServletContext().getRealPath(".");
      } catch (Exception e ) {
        e.printStackTrace();
      }
      // now let's load us some files
      modsXSL = readFile(realPath + "/stylesheets/MARC21slim2MODS3.xsl");
      rdfXSL = readFile(realPath + "/stylesheets/MARC21slim2RDFDC.xsl");
      oaiXSL = readFile(realPath + "/stylesheets/MARC21slim2OAIDC.xsl");
      srwXSL = readFile(realPath + "/stylesheets/MARC21slim2SRWDC.xsl");
      converter = new AnselToUnicode();
  }
	    
     


  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	  String indicators, catLinkXref, text;
	  String indicatorOne = "";
	  String indicatorTwo = "";
	  int tag, tagord;
	  byte[] bArray;
	  String bibNum = req.getParameter("bib");
	  String format = req.getParameter("format");
	  if(format == null || format.length() == 0) {
		  format = "mods";
	  }
	  int iBibNum = Integer.parseInt( bibNum );
	  StringBuffer sbRet = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?><collection xmlns=\"http://www.loc.gov/MARC21/slim\"><record>"); // may be a bad idea if no records found
	  ResultSet rs = null;
	  
	  try {
		  format = req.getParameter("format").toLowerCase();
	  } catch (Exception e) {
		  // do nothing.
	  }
	  try {
		  conn = ds.getConnection();
		  pstmt = conn.prepareStatement("select tagord, tag, indicators, text, cat_link_xref# from fullbib where bib# = ?");
		  pstmt.setInt( 1, iBibNum );
		  rs = pstmt.executeQuery();
		  while( rs.next() ) {
			  tagord = rs.getInt(1);
			  tag = Integer.parseInt(rs.getString(2));
			  indicators = rs.getString(3);
			  bArray = rs.getBytes(4);
			  catLinkXref = rs.getString(5);
			  text = converter.convert( new String( bArray ) );
			  text = text.replaceAll("", ""); // to deal with subvalue markers.. may not be needed with java.
			  String sTag = rs.getString(2);
			  if( tag <= 8 ) {
				  if( tag == 0 ) {
					  sbRet.append("<leader>" + text + "</leader>");
				  } else {
					  sbRet.append("<controlfield tag=\"" +  sTag + "\">" + text + "</controlfield>");
				  }
			  } else {
				  //dealing with a data field
				  //first deal with indicators
				  indicators = rstrip(indicators);
				  if(indicators.length() == 1) {
					  indicatorOne = indicators;
				  } else if(indicators.length() >= 2) {
					indicatorOne = indicators.substring( 0, 1);
					indicatorTwo = indicators.substring(1,2);
				  }
			  
				  sbRet.append("<datafield tag=\"" + sTag + "\" ind1=\"" + indicatorOne + "\" ind2=\"" + indicatorTwo + "\">\n");
				  if( catLinkXref != null && catLinkXref.length() > 0) {
					  //byte[] bAuthText;
					  String authText = text;
					  String authQuery = "select text from auth where auth# = " + catLinkXref + " and tag in( '100', '150', '151')";
					  Statement stmt2 = conn.createStatement();
					  ResultSet rs2 = stmt2.executeQuery( authQuery);
					  if( rs2.next() ) {
						  byte[] bAuthText = rs2.getBytes(1);
						  authText = converter.convert( new String( bAuthText ) );
					  } try {
						  rs2.close();
						  stmt2.close();
					  } catch( Exception e ) {
						  System.err.println("problem closing authQuery" );
					  }
					  
		
					authText = authText.replaceAll("", "");
				  }
			  
				  String[] subfields = text.split("");
				  String sfName, sfText, sfOn;
				  for(int i = 0; i < subfields.length; i++ ) {
				//	sfOn = subfields[i].replaceAll("\x1e", "").trim();
					sfOn = subfields[i].replaceAll("", "").trim();
					if( sfOn.length() > 0){
						sfName = sfOn.substring(0, 1);
						sfText = sfOn.substring(1);
						sbRet.append("<subfield code=\"" + sfName + "\">" + sfText + "</subfield>\n");	
					}
				  }
				  sbRet.append("</datafield>\n");
			  }
		}
	  } catch( SQLException sqe){
		  System.err.println("SQL Exception !" );
		  sqe.printStackTrace();
	  } catch( Exception e ) {
		  e.printStackTrace();
	  } finally {
		  try {
			  rs.close();
			  pstmt.close();
			  conn.close();
		  } catch(Exception e) {
			  e.printStackTrace();
		  }
	  }
	  
	  
	sbRet.append("</record></collection>");
	String ret = sbRet.toString();
	// now check if we need to XSL transform it.
	if(format.equals("mods") ) {
		ret = simpleTransform( ret, realPath + "/stylesheets/MARC21slim2MODS3.xsl");
	} else if( format.equals("rdf") ) {
		ret = simpleTransform( ret, realPath + "/stylesheets/MARC21slim2RDFDC.xsl");
	} else if( format.equals("oai") ) { 
		ret = simpleTransform( ret, realPath + "/stylesheets/MARC21slim2OAIDC.xsl");
	} else if( format.equals("srw") ) {
		ret = simpleTransform( ret, realPath + "/stylesheets/MARC21slim2SRWDC.xsl");
	}
	// otherwise return as marcxml

	resp.setContentType("text/xml; charset=UTF-8");
	PrintWriter pw = resp.getWriter();
	pw.write( ret );  
  }
  
  
  // begin misc. utilities
  public static Context getInitialContext()
		throws NamingException{
			Properties p = new Properties();
			p.put( Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory" );
			p.put( Context.PROVIDER_URL, "localhost:1099" ); //TODO: make not hardcoded
			return new InitialContext( p );
 }
  
  private static String rstrip(String target ) {
	int lastIndexOf = target.lastIndexOf(" ");
	if(lastIndexOf < 1) {
		return target;
	} else {
		int i = lastIndexOf;
		while( i > 0 && target.charAt( i ) == ' ' ){
			i--;
		}
		if (i > 0) {
			return target.substring(0, i);
		} else {
			return "";
		}
	}
	  
  }
    
  private static String readFile(String filePath ) {
        StringBuffer sbOut = new StringBuffer("");
        String lineOn = "";
        File fileOn = new File(filePath);
        try {
          FileReader in = new FileReader( fileOn);
          BufferedReader br = new BufferedReader( in );
          while( ( lineOn = br.readLine() ) != null ) {
            sbOut.append(lineOn);            
          }
        } catch (Exception e){
		e.printStackTrace();
        }
        
      return sbOut.toString();
   }
   public static String simpleTransform( String xmlData, String xslFileName){
		StringWriter ret = new StringWriter();
		try{
			File xslFile = new File( xslFileName);
			TransformerFactory f = TransformerFactory.newInstance();
			Transformer t = f.newTransformer(new StreamSource(xslFile) );
			Source s = new StreamSource( new StringReader(xmlData) );
			Result r = new StreamResult( ret );
			t.transform( s,r);
		} catch (Exception e){
			e.printStackTrace();
		}
		return ret.toString();
	}
	
	
    
  
}
