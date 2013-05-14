package org.spl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.marc4j.converter.impl.AnselToUnicode;
import com.ibm.icu.text.Normalizer;


public class RonsModsServlet extends HttpServlet  {
	
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
      Context ctx = null;
      Context envCtx = null;
      String dsn = "mods";
      
      // Tomcat places web-app specific JNDI stuff under java:comp/env while
      // it appears JBoss shoves all the JNDI stuff under java:/.  So this
      // is kinda ugly: we try to retrieve the DataSource from java:comp/env first,
      // then attempt to find it under java:/.      
      
      try {                    
          ctx = new InitialContext( );
          envCtx = (Context)ctx.lookup( "java:comp/env" );
          ds = (DataSource)envCtx.lookup( dsn );
      } catch ( NamingException ne ) {
          try {
              envCtx = (Context)ctx.lookup( "java:/" );
              ds = (DataSource)envCtx.lookup( dsn );
          } catch ( NamingException e ) {
              System.out.println( "Unable to find DataSource under java:comp/env/" + dsn + " or java:/" + dsn );
          }
      }

      realPath = config.getServletContext( ).getRealPath( "." ); 
            
      // now let's load us some files
      modsXSL = readFile( realPath + "/stylesheets/MARC21slim2MODS3.xsl" );
      rdfXSL = readFile( realPath + "/stylesheets/MARC21slim2RDFDC.xsl" );
      oaiXSL = readFile( realPath + "/stylesheets/MARC21slim2OAIDC.xsl" );
      srwXSL = readFile( realPath + "/stylesheets/MARC21slim2SRWDC.xsl" );
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
	  StringBuffer sbRet = new StringBuffer("<?xml version=\"1.0\" encoding=\"utf-8\"?><collection xmlns=\"http://www.loc.gov/MARC21/slim\"><record>"); // may be a bad idea if no records found
	  ResultSet rs = null;
	  
	  try {
		  format = req.getParameter("format").toLowerCase();
	  } catch (Exception e) {
		  // do nothing.
	  }
	  try {
		  conn = ds.getConnection();
		  String column_name;
		  pstmt = conn.prepareStatement("select tagord, tag, indicators, text, cat_link_xref#, xref_text, longtext, xref_longtext from fullbib where bib# = ?");
		  pstmt.setInt( 1, iBibNum );
		  rs = pstmt.executeQuery();
/*
           1> sp_help fullbib
2> go
 Name                           Owner                          Object_type                      
 ------------------------------ ------------------------------ -------------------------------- 
 fullbib                        dbo                            view                             

(1 row affected)
 Data_located_on_segment        When_created               
 ------------------------------ -------------------------- 
 not applicable                        Aug  8 2004 10:16AM 
 Column_name     Type            Length      Prec Scale Nulls Default_name    Rule_name       Access_Rule_name               Identity 
 --------------- --------------- ----------- ---- ----- ----- --------------- --------------- ------------------------------ -------- 
 bib#            int                       4 NULL  NULL     0 NULL            NULL            NULL                                  0 
 tagord          tagord_type               4 NULL  NULL     1 NULL            NULL            NULL                                  0 
 tag             tag_type                  5 NULL  NULL     1 NULL            NULL            NULL                                  0 
 indicators      varchar                   4 NULL  NULL     1 NULL            NULL            NULL                                  0 
 text            varchar                 255 NULL  NULL     1 NULL            NULL            NULL                                  0 
 cat_link_type#  key#                      4 NULL  NULL     1 NULL            NULL            NULL                                  0 
 cat_link_xref#  int                       4 NULL  NULL     1 NULL            NULL            NULL                                  0 
 link_type       tinyint                   1 NULL  NULL     1 NULL            NULL            NULL                                  0 
 longtext        text                     16 NULL  NULL     1 NULL            NULL            NULL                                  0 
 xref_text       varchar                 255 NULL  NULL     1 NULL            NULL            NULL                                  0 
 xref_longtext   text                     16 NULL  NULL     1 NULL            NULL            NULL                                  0 
 timestamp       timestamp                 8 NULL  NULL     1 NULL            NULL            NULL                                  0 
 auth_timestamp  timestamp                 8 NULL  NULL     1 NULL            NULL            NULL                                  0 
No defined keys for this object.
(return status = 0)
*/
		  while( rs.next() ) {
			  tagord = rs.getInt(1);
			  tag = Integer.parseInt(rs.getString(2));
			  indicators = rs.getString(3);
			  // bArray = rs.getBytes(4);
			  if (rs.getBytes(8) != null ) {
				  column_name = "column 8 (xref_longtext)";
				  bArray = rs.getBytes(8);
			  } else if (rs.getBytes(6) != null ) {
                  column_name = "column 6 (xref_text)";
				  bArray = rs.getBytes(6);
			  } else if (rs.getBytes(7) != null){
                  column_name = "column 7 (longtext)";
				  bArray = rs.getBytes(7);
			  } else {
                  column_name = "column 4 (text)";
				  bArray = rs.getBytes(4);
			  }
			  catLinkXref = rs.getString(5);
              
              /*
               * Test case bib nos: 2323997 2423171 2498632
               */
			     
              
			  //System.out.println( "Bytes from database table 'fullbib' " + column_name + " bib# " + iBibNum + " :" );
        //      System.out.println( );
			  //printBytes( bArray );
        //      System.out.println( );
              
        //      printStringDebugging( bArray );
			  
              
			  // text = AnselToUnicode.convert( convertedString );
			  text = converter.convert( new String( bArray, "Cp1252") );
        text = Normalizer.compose(text, false);

              // text = converter.convert( cp437convertedString );
			  text = text.replaceAll("", ""); // to deal with subvalue markers.. may not be needed with java.
			  text = text.replaceAll("&", "&amp;");
			  text = text.replaceAll("<", "&lt;");
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
					  String authQuery = "select text from auth where auth# = " + catLinkXref + " and tag in ( '100', '150', '151')";
					  Statement stmt2 = conn.createStatement();
					  ResultSet rs2 = stmt2.executeQuery( authQuery);
					  if( rs2.next() ) {
						  byte[] bAuthText = rs2.getBytes(1);
						  authText = converter.convert( new String( bAuthText ) );
              authText = Normalizer.compose(authText, false);

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
	} else if( format.equals("marc") ) {
		ret = simpleTransform( ret, realPath + "/stylesheets/MARC21slim2TEXT.xsl");
	}
	// otherwise return as marcxml

	if(format.equals("marc") ) {
		resp.setContentType("text/plain; charset=UTF-8");		
	} else {
		resp.setContentType("text/xml; charset=UTF-8");		
	}
	PrintWriter pw = resp.getWriter();
	pw.write( ret );  
  }
  
  
  // begin misc. utilities
  public static Context getInitialContext()
		throws NamingException{
			Properties p = new Properties();
			p.put( Context.INITIAL_CONTEXT_FACTORY, "javax.naming.spi.InitialContextFactory" );
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
	    System.out.println( "Reading: " + filePath );
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
   
   private void printBytes( byte[] bytes ) {
	   int offset = 10;
	   int indent = 5;
	   for ( int i = 0; i < bytes.length; i++ ) {
		   if ( ( i % offset ) == 0 ) {
			 if ( i != 0 )
				 System.out.println( );
			 int currentOffset = offset * i / 10;
			 String offsetStr = currentOffset + ":";
			 StringBuffer spaces = new StringBuffer( );
			 for ( int m = offsetStr.length( ); m < indent; m++ ) {
				 spaces.append( " " );
			 }
		     System.out.print( offsetStr + spaces.toString( ) );   
		   }
		   System.out.print( Integer.toHexString( bytes[i] ) + "(" + bytes[i] + ") " );
	   }	
   }
	
   private void printBytes( String str, String charset ) {
	   byte[] bytes = {};
	   try {
		   bytes = str.getBytes( charset );
	   } catch ( UnsupportedEncodingException uee ) {
		   uee.printStackTrace( );
	   }
	   printBytes( bytes );
   }
   
   private void printStringDebugging( byte[] byteArray ) {
       byte[] bArray = byteArray;
       
       String cp437convertedString = null;
       String iso88591convertedString = null;
       String cp850convertedString = null;
       String cp1252convertedString = null;
       String utf8convertedString = null;
              
       try {
           BufferedReader cp437reader = new BufferedReader( new InputStreamReader( new ByteArrayInputStream( bArray ), "Cp437" ) );
           cp437convertedString = cp437reader.readLine( );
           
           BufferedReader iso88591reader = new BufferedReader( new InputStreamReader( new ByteArrayInputStream( bArray ), "ISO-8859-1" ) );
           iso88591convertedString = iso88591reader.readLine( );
           
           BufferedReader cp850reader = new BufferedReader( new InputStreamReader( new ByteArrayInputStream( bArray ), "Cp850" ) );
           cp850convertedString = cp850reader.readLine( );
           
           BufferedReader cp1252reader = new BufferedReader( new InputStreamReader( new ByteArrayInputStream( bArray ), "Cp1252" ) );
           cp1252convertedString = cp1252reader.readLine( );
           
           BufferedReader utf8reader = new BufferedReader( new InputStreamReader ( new ByteArrayInputStream( bArray), "UTF-8") );
           utf8convertedString = utf8reader.readLine( );
       } catch ( UnsupportedEncodingException e ) {
           // TODO Auto-generated catch block
           e.printStackTrace();
       } catch ( IOException e ) {
           // TODO Auto-generated catch block
           e.printStackTrace();
       }              
       
       System.out.println( );             
       System.out.println( "CP437 String: " + cp437convertedString );
       System.out.println( "CP437 bytes: " );
       printBytes( cp437convertedString, "Cp437" );
       
       System.out.println( );
       System.out.println( "CP850 String: " + cp850convertedString );
       System.out.println( "CP850 bytes: " );
       printBytes( cp850convertedString, "Cp850" );
       
       System.out.println( );
       System.out.println( "ISO-8859-1 String: " + iso88591convertedString );
       System.out.println( "ISO-8859-1 bytes: " );
       printBytes( iso88591convertedString, "ISO-8859-1" );
       
       System.out.println( );
       System.out.println( "UTF8 String: " + utf8convertedString );
       System.out.println( "UTF8 bytes: " );
       printBytes( utf8convertedString, "UTF-8" );
       
       System.out.println( );
       System.out.println( "CP1252 String: " + cp1252convertedString );
       System.out.println( "CP1252 bytes: " );
       printBytes( cp1252convertedString, "Cp1252" );
       
       System.out.println( );
       System.out.println( );
   }
	
    
  
}
