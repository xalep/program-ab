package org.alicebot.ab.aiml;
/* Program AB Reference AIML 2.0 implementation
        Copyright (C) 2013 ALICE A.I. Foundation
        Contact: info@alicebot.org

        This library is free software; you can redistribute it and/or
        modify it under the terms of the GNU Library General Public
        License as published by the Free Software Foundation; either
        version 2 of the License, or (at your option) any later version.

        This library is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
        Library General Public License for more details.

        You should have received a copy of the GNU Library General Public
        License along with this library; if not, write to the
        Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
        Boston, MA  02110-1301, USA.
*/

import org.alicebot.ab.Contact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * This is just a stub to make the contactaction.aiml file work on a PC
 * with some extension tags that are defined for mobile devices.
 */
public enum PCAIMLProcessorExtension implements AIMLProcessorExtension {

    NEW_CONTACT("addinfo") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            Map<String, String> children = evalChildren(node, evalTagContent);
            String emailAddress = children.getOrDefault("emailaddress", "unknown");
            String displayName = children.getOrDefault("displayname", "unknown");
            String dialNumber = children.getOrDefault("dialnumber", "unknown");
            String emailType = children.getOrDefault("emailtype", "unknown");
            String phoneType = children.getOrDefault("phonetype", "unknown");
            String birthday = children.getOrDefault("birthday", "unknown");
            logger.info("Adding new contact {} {} {} {} {} {}",
                displayName, phoneType, dialNumber, emailType, emailAddress, birthday);
            // the contact adds itself to the contact list
            //noinspection ResultOfObjectAllocationIgnored
            new Contact(displayName, phoneType, dialNumber, emailType, emailAddress, birthday);
            return "";
        }
    },

    CONTACT_ID("contactid") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            String displayName = evalTagContent.apply(node);
            return Contact.contactId(displayName);
        }
    },

    MULTIPLE_IDS("multipleids") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            String contactName = evalTagContent.apply(node);
            return Contact.multipleIds(contactName);
        }
    },

    DISPLAY_NAME("displayname") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            String id = evalTagContent.apply(node);
            return Contact.displayName(id);
        }
    },

    DIAL_NUMBER("dialnumber") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            Map<String, String> children = evalChildren(node, evalTagContent);
            String id = children.getOrDefault("id", "unknown");
            String type = children.getOrDefault("type", "unknown");
            return Contact.dialNumber(type, id);
        }
    },

    EMAIL_ADDRESS("emailaddress") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            Map<String, String> children = evalChildren(node, evalTagContent);
            String id = children.getOrDefault("id", "unknown");
            String type = children.getOrDefault("type", "unknown");
            return Contact.emailAddress(type, id);
        }
    },

    BIRTHDAY("contactbirthday") {
        @Override
        public String recursEval(Node node, Function<Node, String> evalTagContent) {
            String id = evalTagContent.apply(node);
            return Contact.birthday(id);
        }
    };

    private static final Logger logger = LoggerFactory.getLogger(PCAIMLProcessorExtension.class);

    private final String tagName;

    PCAIMLProcessorExtension(String tagName) {
        this.tagName = tagName;
    }

    @Override
    public boolean canProcessTag(String tagName) {
        return this.tagName.equals(tagName);
    }

    protected Map<String, String> evalChildren(Node node, Function<Node, String> evalTagContent) {
        NodeList childList = node.getChildNodes();
        Map<String, String> children = new HashMap<>(childList.getLength());
        for (int i = 0; i < childList.getLength(); i++) {
            children.put(childList.item(i).getNodeName(), evalTagContent.apply(childList.item(i)));
        }
        return children;
    }

}
