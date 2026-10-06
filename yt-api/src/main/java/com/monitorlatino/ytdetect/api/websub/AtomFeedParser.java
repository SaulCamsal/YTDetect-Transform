package com.monitorlatino.ytdetect.api.websub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class AtomFeedParser {

    private static final Logger log = LoggerFactory.getLogger(AtomFeedParser.class);

    public List<AtomVideoEntry> parseFeed(String xml) {
        List<AtomVideoEntry> entries = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return entries;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList entryNodes = doc.getElementsByTagName("entry");
            for (int i = 0; i < entryNodes.getLength(); i++) {
                if (entryNodes.item(i) instanceof Element entryElem) {
                    String videoId = getElementText(entryElem, "videoId");
                    String channelId = getElementText(entryElem, "channelId");
                    String title = getElementText(entryElem, "title");
                    String publishedStr = getElementText(entryElem, "published");

                    Instant publishedAt = null;
                    if (publishedStr != null && !publishedStr.isBlank()) {
                        try {
                            publishedAt = Instant.parse(publishedStr);
                        } catch (DateTimeParseException e) {
                            log.warn("Could not parse published date: {}", publishedStr);
                        }
                    }

                    if (videoId != null && !videoId.isBlank() && channelId != null && !channelId.isBlank()) {
                        entries.add(new AtomVideoEntry(videoId, channelId, title, publishedAt));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Atom XML feed: {}", e.getMessage(), e);
        }

        return entries;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() == 0) {
            list = parent.getElementsByTagName(tagName);
        }
        if (list.getLength() > 0 && list.item(0) != null) {
            return list.item(0).getTextContent().trim();
        }
        return null;
    }
}
