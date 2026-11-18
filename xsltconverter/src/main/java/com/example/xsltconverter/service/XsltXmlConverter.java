package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringWriter;

@Service
public class XsltXmlConverter implements XmlConverter {

    private static final String STYLESHEET_PATH = "/xslt/elements-to-attributes.xsl";
    private final Templates templates;

    public XsltXmlConverter() {
        this.templates = compileTemplates();
    }

    @Override
    public ConversionType getType() {
        return ConversionType.XML_TO_XML;
    }

    @Override
    public String convert(String xmlContent) {
        Document document = XmlDocumentParser.parse(xmlContent);
        try {
            Transformer transformer = templates.newTransformer();
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString().trim();
        } catch (Exception e) {
            throw new XmlConversionException("Failed to transform XML with XSLT", e);
        }
    }

    private Templates compileTemplates() {
        try (InputStream stream = getClass().getResourceAsStream(STYLESHEET_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Stylesheet not found: " + STYLESHEET_PATH);
            }
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return factory.newTemplates(new StreamSource(stream));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load XSLT stylesheet", e);
        }
    }
}
