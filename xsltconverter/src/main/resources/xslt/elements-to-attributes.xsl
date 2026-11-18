<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" encoding="UTF-8" indent="yes" omit-xml-declaration="yes"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="*">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates select="*[not(*) and not(@*)]" mode="attr"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*" mode="attr">
        <xsl:variable name="tagName" select="name()"/>
        <xsl:if test="not(preceding-sibling::*[name() = $tagName]) and not(following-sibling::*[name() = $tagName]) and not(../@*[name() = $tagName])">
            <xsl:attribute name="{$tagName}">
                <xsl:value-of select="."/>
            </xsl:attribute>
        </xsl:if>
    </xsl:template>

    <xsl:template match="*[not(*) and not(@*)]">
        <xsl:variable name="tagName" select="name()"/>
        <xsl:if test="preceding-sibling::*[name() = $tagName] or following-sibling::*[name() = $tagName] or ../@*[name() = $tagName]">
            <xsl:copy>
                <xsl:value-of select="."/>
            </xsl:copy>
        </xsl:if>
    </xsl:template>

    <xsl:template match="/*[not(*) and not(@*)]" priority="2">
        <xsl:copy>
            <xsl:value-of select="."/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="text() | comment() | processing-instruction()">
        <xsl:copy/>
    </xsl:template>
</xsl:stylesheet>
