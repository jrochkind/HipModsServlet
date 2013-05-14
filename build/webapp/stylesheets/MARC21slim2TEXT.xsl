<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:marc="http://www.loc.gov/MARC21/slim" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output media-type="text/plain" method="text"/>
	
	<xsl:template match="/">
			<xsl:apply-templates/>
	</xsl:template>
	
	<xsl:template match="marc:record">
LDR:&#160;<xsl:value-of select="marc:leader"/>
		<xsl:text>&#10;</xsl:text>
		<xsl:apply-templates select="marc:datafield|marc:controlfield"/>
		<xsl:text>&#10;</xsl:text>
	</xsl:template>
	
	<xsl:template match="marc:controlfield">
				<xsl:value-of select="@tag"/>:  <xsl:value-of select="."/>
		<xsl:text>&#10;</xsl:text>
	</xsl:template>
	
	<xsl:template match="marc:datafield">
		<xsl:value-of select="@tag"/>: &#160;<xsl:value-of select="@ind1"/><xsl:value-of select="@ind2"/>&#160;<xsl:apply-templates select="marc:subfield"/>
		<xsl:text>&#10;</xsl:text> 
	</xsl:template>
	
	<xsl:template match="marc:subfield">$<xsl:value-of select="@code"/><xsl:value-of select="."/></xsl:template>

</xsl:stylesheet>

<!-- Stylus Studio meta-information - (c)1998-2002 eXcelon Corp.
<metaInformation>
<scenarios ><scenario default="no" name="Ray Charles" userelativepaths="yes" externalpreview="no" url="..\xml\MARC21slim\raycharles.xml" htmlbaseurl="" outputurl="" processortype="internal" commandline="" additionalpath="" additionalclasspath="" postprocessortype="none" postprocesscommandline="" postprocessadditionalpath="" postprocessgeneratedext=""/><scenario default="yes" name="s7" userelativepaths="yes" externalpreview="no" url="..\ifla\sally7.xml" htmlbaseurl="" outputurl="" processortype="internal" commandline="" additionalpath="" additionalclasspath="" postprocessortype="none" postprocesscommandline="" postprocessadditionalpath="" postprocessgeneratedext=""/></scenarios><MapperInfo srcSchemaPath="" srcSchemaRoot="" srcSchemaPathIsRelative="yes" srcSchemaInterpretAsXML="no" destSchemaPath="" destSchemaRoot="" destSchemaPathIsRelative="yes" destSchemaInterpretAsXML="no"/>
</metaInformation>
-->