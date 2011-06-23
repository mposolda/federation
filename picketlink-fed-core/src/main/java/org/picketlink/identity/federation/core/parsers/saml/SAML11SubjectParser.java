/*
 * JBoss, Home of Professional Open Source. Copyright 2008, Red Hat Middleware LLC, and individual contributors as
 * indicated by the @author tags. See the copyright.txt file in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any
 * later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this software; if not, write to
 * the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF site:
 * http://www.fsf.org.
 */
package org.picketlink.identity.federation.core.parsers.saml;

import java.net.URI;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.picketlink.identity.federation.core.exceptions.ParsingException;
import org.picketlink.identity.federation.core.parsers.ParserNamespaceSupport;
import org.picketlink.identity.federation.core.parsers.util.StaxParserUtil;
import org.picketlink.identity.federation.core.saml.v1.SAML11Constants;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLConstants;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLURIConstants;
import org.picketlink.identity.federation.core.saml.v2.util.XMLTimeUtil;
import org.picketlink.identity.federation.core.wstrust.WSTrustConstants;
import org.picketlink.identity.federation.saml.v1.assertion.SAML11NameIdentifierType;
import org.picketlink.identity.federation.saml.v1.assertion.SAML11SubjectConfirmationType;
import org.picketlink.identity.federation.saml.v1.assertion.SAML11SubjectType;
import org.picketlink.identity.federation.saml.v1.assertion.SAML11SubjectType.SAML11SubjectTypeChoice;
import org.picketlink.identity.federation.saml.v2.assertion.SubjectConfirmationDataType;
import org.picketlink.identity.xmlsec.w3.xmldsig.KeyInfoType;
import org.picketlink.identity.xmlsec.w3.xmldsig.KeyValueType;
import org.picketlink.identity.xmlsec.w3.xmldsig.RSAKeyValueType;
import org.picketlink.identity.xmlsec.w3.xmldsig.X509CertificateType;
import org.picketlink.identity.xmlsec.w3.xmldsig.X509DataType;

/**
 * Parse the saml subject
 * 
 * @author Anil.Saldhana@redhat.com
 * @since Oct 12, 2010
 */
public class SAML11SubjectParser implements ParserNamespaceSupport
{
   /**
    * @see {@link ParserNamespaceSupport#parse(XMLEventReader)}
    */
   public Object parse(XMLEventReader xmlEventReader) throws ParsingException
   {
      StaxParserUtil.getNextEvent(xmlEventReader);

      SAML11SubjectType subject = new SAML11SubjectType();

      // Peek at the next event
      while (xmlEventReader.hasNext())
      {
         XMLEvent xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if (xmlEvent instanceof EndElement)
         {
            EndElement endElement = (EndElement) xmlEvent;
            if (StaxParserUtil.matches(endElement, JBossSAMLConstants.SUBJECT.get()))
            {
               endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
               break;
            }
            else
               throw new RuntimeException("Unknown End Element:" + StaxParserUtil.getEndElementName(endElement));
         }

         StartElement peekedElement = StaxParserUtil.peekNextStartElement(xmlEventReader);
         if (peekedElement == null)
            break;

         String tag = StaxParserUtil.getStartElementName(peekedElement);

         if (SAML11Constants.NAME_IDENTIFIER.equalsIgnoreCase(tag))
         {
            peekedElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            SAML11NameIdentifierType nameID = new SAML11NameIdentifierType();
            Attribute formatAtt = peekedElement.getAttributeByName(new QName(SAML11Constants.FORMAT));
            if (formatAtt != null)
            {
               nameID.setFormat(URI.create(StaxParserUtil.getAttributeValue(formatAtt)));
            }

            String val = StaxParserUtil.getElementText(xmlEventReader);
            nameID.setNameQualifier(val);
            SAML11SubjectTypeChoice subChoice = new SAML11SubjectTypeChoice(nameID);
            subject.setChoice(subChoice);
         }
         else if (JBossSAMLConstants.SUBJECT_CONFIRMATION.get().equalsIgnoreCase(tag))
         {
            SAML11SubjectConfirmationType subjectConfirmationType = new SAML11SubjectConfirmationType();
            peekedElement = StaxParserUtil.getNextStartElement(xmlEventReader);

            // There may be additional things under subject confirmation
            xmlEvent = StaxParserUtil.peek(xmlEventReader);
            if (xmlEvent instanceof StartElement)
            {
               StartElement startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
               String startTag = StaxParserUtil.getStartElementName(startElement);

               if (startTag.equals(SAML11Constants.CONFIRMATION_METHOD))
               {
                  String method = StaxParserUtil.getElementText(xmlEventReader);
                  subjectConfirmationType.addConfirmation(URI.create(method));
               }

               if (startTag.equals(JBossSAMLConstants.SUBJECT_CONFIRMATION_DATA.get()))
               {
                  SubjectConfirmationDataType subjectConfirmationData = parseSubjectConfirmationData(xmlEventReader);
                  subjectConfirmationType.setSubjectConfirmationData(subjectConfirmationData);
               }
            }

            subject.setSubjectConfirmation(subjectConfirmationType);

            // Get the end tag
            EndElement endElement = (EndElement) StaxParserUtil.getNextEvent(xmlEventReader);
            StaxParserUtil.matches(endElement, JBossSAMLConstants.SUBJECT_CONFIRMATION.get());
         }
         else
            throw new RuntimeException("Unknown tag:" + tag + "::location=" + peekedElement.getLocation());
      }
      return subject;
   }

   /**
    * @see {@link ParserNamespaceSupport#supports(QName)}
    */
   public boolean supports(QName qname)
   {
      String nsURI = qname.getNamespaceURI();
      String localPart = qname.getLocalPart();

      return nsURI.equals(JBossSAMLURIConstants.ASSERTION_NSURI.get())
            && localPart.equals(JBossSAMLConstants.SUBJECT.get());
   }

   private SubjectConfirmationDataType parseSubjectConfirmationData(XMLEventReader xmlEventReader)
         throws ParsingException
   {
      StartElement startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
      StaxParserUtil.validate(startElement, JBossSAMLConstants.SUBJECT_CONFIRMATION_DATA.get());

      SubjectConfirmationDataType subjectConfirmationData = new SubjectConfirmationDataType();

      Attribute inResponseTo = startElement.getAttributeByName(new QName(JBossSAMLConstants.IN_RESPONSE_TO.get()));
      if (inResponseTo != null)
      {
         subjectConfirmationData.setInResponseTo(StaxParserUtil.getAttributeValue(inResponseTo));
      }

      Attribute notBefore = startElement.getAttributeByName(new QName(JBossSAMLConstants.NOT_BEFORE.get()));
      if (notBefore != null)
      {
         subjectConfirmationData.setNotBefore(XMLTimeUtil.parse(StaxParserUtil.getAttributeValue(notBefore)));
      }

      Attribute notOnOrAfter = startElement.getAttributeByName(new QName(JBossSAMLConstants.NOT_ON_OR_AFTER.get()));
      if (notOnOrAfter != null)
      {
         subjectConfirmationData.setNotOnOrAfter(XMLTimeUtil.parse(StaxParserUtil.getAttributeValue(notOnOrAfter)));
      }

      Attribute recipient = startElement.getAttributeByName(new QName(JBossSAMLConstants.RECIPIENT.get()));
      if (recipient != null)
      {
         subjectConfirmationData.setRecipient(StaxParserUtil.getAttributeValue(recipient));
      }

      Attribute address = startElement.getAttributeByName(new QName(JBossSAMLConstants.ADDRESS.get()));
      if (address != null)
      {
         subjectConfirmationData.setAddress(StaxParserUtil.getAttributeValue(address));
      }

      XMLEvent xmlEvent = StaxParserUtil.peek(xmlEventReader);
      if (!(xmlEvent instanceof EndElement))
      {
         startElement = StaxParserUtil.peekNextStartElement(xmlEventReader);
         String tag = StaxParserUtil.getStartElementName(startElement);
         if (tag.equals(WSTrustConstants.XMLDSig.KEYINFO))
         {
            KeyInfoType keyInfo = parseKeyInfo(xmlEventReader);
            subjectConfirmationData.setAnyType(keyInfo);
         }
         else if (tag.equals(WSTrustConstants.XMLEnc.ENCRYPTED_KEY))
         {
            subjectConfirmationData.setAnyType(StaxParserUtil.getDOMElement(xmlEventReader));
         }
         else
            throw new RuntimeException("Handle:" + tag);
      }

      // Get the end tag
      EndElement endElement = (EndElement) StaxParserUtil.getNextEvent(xmlEventReader);
      StaxParserUtil.matches(endElement, JBossSAMLConstants.SUBJECT_CONFIRMATION_DATA.get());
      return subjectConfirmationData;
   }

   private KeyInfoType parseKeyInfo(XMLEventReader xmlEventReader) throws ParsingException
   {
      KeyInfoType keyInfo = new KeyInfoType();
      StartElement startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
      StaxParserUtil.validate(startElement, WSTrustConstants.XMLDSig.KEYINFO);

      XMLEvent xmlEvent = null;
      String tag = null;

      while (xmlEventReader.hasNext())
      {
         xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if (xmlEvent instanceof EndElement)
         {
            tag = StaxParserUtil.getEndElementName((EndElement) xmlEvent);
            if (tag.equals(WSTrustConstants.XMLDSig.KEYINFO))
            {
               xmlEvent = StaxParserUtil.getNextEndElement(xmlEventReader);
               break;
            }
            else
               throw new RuntimeException("unknown end element:" + tag);
         }
         startElement = (StartElement) xmlEvent;
         tag = StaxParserUtil.getStartElementName(startElement);
         if (tag.equals(WSTrustConstants.XMLEnc.ENCRYPTED_KEY))
         {
            keyInfo.addContent(StaxParserUtil.getDOMElement(xmlEventReader));
         }
         else if (tag.equals(WSTrustConstants.XMLDSig.X509DATA))
         {
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            X509DataType x509 = new X509DataType();

            // Let us go for the X509 certificate
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            StaxParserUtil.validate(startElement, WSTrustConstants.XMLDSig.X509CERT);

            X509CertificateType cert = new X509CertificateType();
            String certValue = StaxParserUtil.getElementText(xmlEventReader);
            cert.setEncodedCertificate(certValue.getBytes());
            x509.add(cert);

            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate(endElement, WSTrustConstants.XMLDSig.X509DATA);
            keyInfo.addContent(x509);
         }
         else if (tag.equals(WSTrustConstants.XMLDSig.KEYVALUE))
         {
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            KeyValueType keyValue = new KeyValueType();

            startElement = StaxParserUtil.peekNextStartElement(xmlEventReader);
            tag = StaxParserUtil.getStartElementName(startElement);
            if (tag.equals(WSTrustConstants.XMLDSig.RSA_KEYVALUE))
            {
               keyValue.getContent().add(this.parseRSAKeyValue(xmlEventReader));
            }
            else if (tag.equals(WSTrustConstants.XMLDSig.DSA_KEYVALUE))
            {
               // TODO: parse the DSA key contents.
            }
            else
               throw new ParsingException("Unknown element: " + tag);

            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate(endElement, WSTrustConstants.XMLDSig.KEYVALUE);

            keyInfo.addContent(keyValue);
         }
      }
      return keyInfo;
   }

   private RSAKeyValueType parseRSAKeyValue(XMLEventReader xmlEventReader) throws ParsingException
   {
      StartElement startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
      StaxParserUtil.validate(startElement, WSTrustConstants.XMLDSig.RSA_KEYVALUE);

      XMLEvent xmlEvent = null;
      String tag = null;

      RSAKeyValueType rsaKeyValue = new RSAKeyValueType();

      while (xmlEventReader.hasNext())
      {
         xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if (xmlEvent instanceof EndElement)
         {
            tag = StaxParserUtil.getEndElementName((EndElement) xmlEvent);
            if (tag.equals(WSTrustConstants.XMLDSig.RSA_KEYVALUE))
            {
               xmlEvent = StaxParserUtil.getNextEndElement(xmlEventReader);
               break;
            }
            else
               throw new RuntimeException("unknown end element:" + tag);
         }

         startElement = (StartElement) xmlEvent;
         tag = StaxParserUtil.getStartElementName(startElement);
         if (tag.equals(WSTrustConstants.XMLDSig.MODULUS))
         {
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            String text = StaxParserUtil.getElementText(xmlEventReader);
            rsaKeyValue.setModulus(text.getBytes());
         }
         else if (tag.equals(WSTrustConstants.XMLDSig.EXPONENT))
         {
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            String text = StaxParserUtil.getElementText(xmlEventReader);
            rsaKeyValue.setExponent(text.getBytes());
         }
         else
            throw new ParsingException("Unknown element: " + tag);
      }
      return rsaKeyValue;
   }
}