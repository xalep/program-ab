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

import org.alicebot.ab.Category;
import org.alicebot.ab.Chat;
import org.alicebot.ab.Clause;
import org.alicebot.ab.History;
import org.alicebot.ab.MagicBooleans;
import org.alicebot.ab.MagicNumbers;
import org.alicebot.ab.MagicStrings;
import org.alicebot.ab.Nodemapper;
import org.alicebot.ab.ParseState;
import org.alicebot.ab.Predicates;
import org.alicebot.ab.Sraix;
import org.alicebot.ab.StandardResponse;
import org.alicebot.ab.Tuple;
import org.alicebot.ab.Utilities;
import org.alicebot.ab.map.AIMLMap;
import org.alicebot.ab.utils.CalendarUtils;
import org.alicebot.ab.utils.DomUtils;
import org.alicebot.ab.utils.IOUtils;
import org.alicebot.ab.utils.IntervalUtils;
import org.alicebot.ab.utils.JapaneseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The core AIML parser and interpreter.
 * Implements the AIML 2.0 specification as described in
 * AIML 2.0 Working Draft document
 * https://docs.google.com/document/d/1wNT25hJRyupcG51aO89UcQEiG-HkXRXusukADpFnDs4/pub
 */
public final class AIMLProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AIMLProcessor.class);

    private static final Collection<AIMLProcessorExtension> extensions = new ArrayList<>();

    private final Chat chatSession;

    /** number of {@code <srai>} activations. */
    private int sraiCount;

    /**
     * @param chatSession current client session.
     */
    public AIMLProcessor(Chat chatSession) {
        this.chatSession = chatSession;
    }

    /**
     * generate a bot response to a single sentence input.
     *
     * @param input the input sentence.
     * @param that  the bot's last sentence.
     * @param topic current topic.
     * @return bot's response.
     */
    public String respond(String input, String that, String topic) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (false && checkForRepeat(input)) {
            return "Repeat!";
        } else {
            sraiCount = 0;
            return doRespond(input, that, topic);
        }
    }

    private boolean checkForRepeat(String input) {
        return input.equals(chatSession.inputHistory.get(1));
    }

    /**
     * generate a bot response to a single sentence input.
     *
     * @param input input statement.
     * @param that  bot's last reply.
     * @param topic current topic.
     * @return bot's reply.
     */
    private String doRespond(String input, String that, String topic) {
        logger.debug("input: {}, that: {}, topic: {}, chatSession: {}, sraiCount: {}",
            input, that, topic, chatSession, sraiCount);
        if (input == null || input.isEmpty()) { input = AIMLDefault.null_input; }
        try {
            Nodemapper leaf = chatSession.bot.brain.match(input, that, topic);
            if (leaf == null) { return StandardResponse.DEFAULT; }
            ParseState ps = new ParseState(0, that, topic, leaf);
            //chatSession.matchTrace += leaf.category.getTemplate()+"\n";
            String template = leaf.category.getTemplate();
            //MagicBooleans.trace("in AIMLProcessor.doRespond(), template: " + template);
            return evalTemplate(template, ps);
        } catch (Exception ex) {
            logger.error("doRespond error", ex);
            return StandardResponse.DEFAULT;
        }
    }

    /**
     * capitalizeString:
     * from http://stackoverflow.com/questions/1892765/capitalize-first-char-of-each-word-in-a-string-java
     *
     * @param string the string to capitalize
     * @return the capitalized string
     */

    private String capitalizeString(String string) {
        char[] chars = string.toLowerCase().toCharArray();
        boolean found = false;
        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i])) {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

    /**
     * explode a string into individual characters separated by one space
     *
     * @param input input string
     * @return exploded string
     */
    private String explode(String input) {
        StringBuilder result = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) { result.append(' ').append(input.charAt(i)); }
        return result.toString().replaceAll("  +", " ").trim();
    }

    // Parsing and evaluation functions:

    /**
     * evaluate the contents of an AIML tag.
     * calls recursEval on child tags.
     *
     * @param node             the current parse node.
     * @param ps               the current parse state.
     * @param ignoreAttributes tag names to ignore when evaluating the tag.
     * @return the result of evaluating the tag contents.
     */
    private String evalTagContent(Node node, ParseState ps, Set<String> ignoreAttributes) {
        //MagicBooleans.trace("AIMLProcessor.evalTagContent(node: " + node + ", ps: " + ps + ", ignoreAttributes: " + ignoreAttributes);
        //MagicBooleans.trace("in AIMLProcessor.evalTagContent, node string: " + DomUtils.nodeToString(node));
        StringBuilder result = new StringBuilder();
        try {
            NodeList childList = node.getChildNodes();
            for (int i = 0; i < childList.getLength(); i++) {
                Node child = childList.item(i);
                //MagicBooleans.trace("in AIMLProcessor.evalTagContent(), child: " + child);
                if (ignoreAttributes == null || !ignoreAttributes.contains(child.getNodeName())) {
                    result.append(recursEval(child, ps));
                }
                //MagicBooleans.trace("in AIMLProcessor.evalTagContent(), result: " + result);
            }
        } catch (Exception ex) {
            logger.error("Something went wrong with evalTagContent", ex);
        }
        //MagicBooleans.trace("AIMLProcessor.evalTagContent() returning: " + result);
        return result.toString();
    }

    /**
     * pass thru generic XML (non-AIML tags, such as HTML) as unevaluated XML
     *
     * @param node current parse node
     * @param ps   current parse state
     * @return unevaluated generic XML string
     */
    private String genericXML(Node node, ParseState ps) {
        String evalResult = evalTagContent(node, ps, null);
        return unevaluatedXML(evalResult, node);
    }

    /**
     * return a string of unevaluated XML.      When the AIML parser
     * encounters an unrecognized XML tag, it simply passes through the
     * tag in XML form.  For example, if the response contains HTML
     * markup, the HTML is passed to the requesting process.    However if that
     * markup contains AIML tags, those tags are evaluated and the parser
     * builds the result.
     *
     * @param node current parse node.
     * @return the unevaluated XML string
     */
    private String unevaluatedXML(String resultIn, Node node) {
        String nodeName = node.getNodeName();
        StringBuilder attributesBuilder = new StringBuilder();
        if (node.hasAttributes()) {
            NamedNodeMap XMLAttributes = node.getAttributes();
            for (int i = 0; i < XMLAttributes.getLength(); i++) {
                attributesBuilder.append(" ")
                    .append(XMLAttributes.item(i).getNodeName())
                    .append("=\"")
                    .append(XMLAttributes.item(i).getNodeValue())
                    .append("\"");
            }
        }
        // String contents = evalTagContent(node, ps, null);
        String attributes = attributesBuilder.toString();
        String result = "<" + nodeName + attributes + "/>";
        if (!resultIn.isEmpty()) { result = "<" + nodeName + attributes + ">" + resultIn + "</" + nodeName + ">"; }
        //MagicBooleans.trace("in AIMLProcessor.unevaluatedXML() returning: " + result);
        return result;
    }

    public static int trace_count = 0;

    /**
     * implements AIML <srai> tag
     *
     * @param node current parse node.
     * @param ps   current parse state.
     * @return the result of processing the <srai>
     */
    private String srai(Node node, ParseState ps) {
        //MagicBooleans.trace("AIMLProcessor.srai(node: " + node + ", ps: " + ps);
        sraiCount++;
        if (sraiCount > MagicNumbers.max_recursion_count || ps.depth > MagicNumbers.max_recursion_depth) {
            return AIMLDefault.too_much_recursion;
        }
        try {
            String result = evalTagContent(node, ps, null);
            result = result.trim();
            result = result.replaceAll("(\r\n|\n\r|\r|\n)", " ");
            result = chatSession.bot.preProcessor.normalize(result);
            result = JapaneseUtils.tokenizeSentence(result);
            String topic = chatSession.predicates.get("topic");     // the that stays the same, but the topic may have changed
            if (logger.isDebugEnabled()) {
                logger.debug("{}. <srai>{}</srai> from {} topic={}) ",
                    trace_count++, result, ps.leaf.category.inputThatTopic(), topic);
            }
            Nodemapper leaf = chatSession.bot.brain.match(result, ps.that, topic);
            if (leaf == null) { return StandardResponse.DEFAULT; }
            //System.out.println("Srai returned "+leaf.category.inputThatTopic()+":"+leaf.category.getTemplate());
            String response = evalTemplate(leaf.category.getTemplate(), new ParseState(ps.depth + 1, ps.that, topic, leaf));
            return response.trim();
        } catch (Exception ex) {
            logger.error("srai error", ex);
            return StandardResponse.DEFAULT;
        }
    }

    /**
     * in AIML 2.0, an attribute value can be specified by either an XML attribute value
     * or a subtag of the same name.  This function tries to read the value from the XML attribute first,
     * then tries to look for the subtag.
     *
     * @param node          current parse node.
     * @param ps            current parse state.
     * @param attributeName the name of the attribute.
     * @return the attribute value.
     */
    private String getAttributeOrTagValue(Node node, ParseState ps, String attributeName) {        // AIML 2.0
        Node m = node.getAttributes().getNamedItem(attributeName);
        if (m != null) {
            return m.getNodeValue();
        }
        NodeList childList = node.getChildNodes();
        String result = null;
        for (int i = 0; i < childList.getLength(); i++) {
            Node child = childList.item(i);
            if (child.getNodeName().equals(attributeName)) {
                result = evalTagContent(child, ps, null);
            }
        }
        return result;
    }

    /**
     * access external web service for response
     * implements <sraix></sraix>
     * and its attribute variations.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return response from remote service or string indicating failure.
     */
    private String sraix(Node node, ParseState ps) {
        Set<String> attributeNames = Utilities.stringSet("botid", "host");
        String host = getAttributeOrTagValue(node, ps, "host");
        String botid = getAttributeOrTagValue(node, ps, "botid");
        String hint = getAttributeOrTagValue(node, ps, "hint");
        String defaultResponse = getAttributeOrTagValue(node, ps, "default");
        String evalResult = evalTagContent(node, ps, attributeNames);
        return Sraix.sraix(chatSession, evalResult, defaultResponse, hint, host, botid);

    }

    /**
     * map an element of one string set to an element of another
     * Implements <map name="mapname"></map>   and <map><name>mapname</name></map>
     *
     * @param node current XML parse node
     * @param ps   current AIML parse state
     * @return the map result or a string indicating the key was not found
     */
    private String map(Node node, ParseState ps) {
        Set<String> attributeNames = Utilities.stringSet("name");
        String mapName = getAttributeOrTagValue(node, ps, "name");
        String contents = evalTagContent(node, ps, attributeNames);
        contents = contents.trim();
        if (mapName == null) {
            return "<map>" + contents + "</map>"; // this is an OOB map tag (no attribute)
        }
        AIMLMap map = chatSession.bot.mapMap.get(mapName);
        if (map == null) {
            logger.debug("Unknown map {}", mapName);
            return AIMLDefault.default_map;
        }
        String result = map.get(contents.toUpperCase());
        logger.trace("AIMLProcessor map {} {}", contents, result);
        return result == null ? AIMLDefault.default_map : result.trim();
    }

    /**
     * set the value of an AIML predicate.
     * Implements <set name="predicate"></set> and <set var="varname"></set>
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the result of the <set> operation
     */
    private String set(Node node, ParseState ps) {                    // add pronoun check
        //MagicBooleans.trace("AIMLProcessor.set(node: " + node + ", ps: " + ps + ")");
        Set<String> attributeNames = Utilities.stringSet("name", "var");
        String predicateName = getAttributeOrTagValue(node, ps, "name");
        String varName = getAttributeOrTagValue(node, ps, "var");
        String result = evalTagContent(node, ps, attributeNames).trim();
        result = result.replaceAll("(\r\n|\n\r|\r|\n)", " ");
        String value = result.trim();
        if (predicateName != null) {
            chatSession.predicates.put(predicateName, result);
            logger.debug("Set predicate {} to {} in {}", predicateName, result, ps.leaf.category.inputThatTopic());
        }
        if (varName != null) {
            ps.vars.put(varName, result);
            logger.debug("Set var {} to {} in {}", varName, value, ps.leaf.category.inputThatTopic());
        }
        if (chatSession.bot.pronounSet.contains(predicateName)) {
            result = predicateName;
        }
        logger.trace("in AIMLProcessor.set, returning: {}", result);
        return result;
    }

    /**
     * get the value of an AIML predicate.
     * implements <get name="predicate"></get>  and <get var="varname"></get>
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the result of the <get> operation
     */
    private String get(Node node, ParseState ps) {
        logger.trace("AIMLProcessor.get(node: {}, ps: {})", node, ps);
        String result = AIMLDefault.default_get;
        String predicateName = getAttributeOrTagValue(node, ps, "name");
        String varName = getAttributeOrTagValue(node, ps, "var");
        String tupleName = getAttributeOrTagValue(node, ps, "tuple");
        if (predicateName != null) {
            result = chatSession.predicates.get(predicateName).trim();
        } else if (varName != null && tupleName != null) {
            result = tupleGet(tupleName, varName);

        } else if (varName != null) {
            result = ps.vars.get(varName).trim();
        }
        logger.trace("in AIMLProcessor.get, returning: {}", result);
        return result;
    }

    private String tupleGet(String tupleName, String varName) {
        Tuple tuple = Tuple.forName(tupleName);
        //System.out.println("Tuple = "+tuple.toString());
        //System.out.println("Value = "+tuple.getValue(varName));
        return tuple == null ? AIMLDefault.default_get : tuple.getValue(varName);
    }

    /**
     * return the value of a bot property.
     * implements {{{@code <bot name="property"/>}}}
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the bot property or a string indicating the property was not found.
     */
    private String bot(Node node, ParseState ps) {
        String result = AIMLDefault.default_property;
        //HashSet<String> attributeNames = Utilities.stringSet("name");
        String propertyName = getAttributeOrTagValue(node, ps, "name");
        if (propertyName != null) {
            result = chatSession.bot.properties.get(propertyName).trim();
        }
        return result;
    }

    /**
     * implements formatted date tag <date jformat="format"/> and <date format="format"/>
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the formatted date
     */
    private String date(Node node, ParseState ps) {
        String jformat = getAttributeOrTagValue(node, ps, "jformat");      // AIML 2.0
        return CalendarUtils.date(jformat);
    }

    /**
     * <interval><style>years</style></style><jformat>MMMMMMMMM dd, yyyy</jformat><from>August 2, 1960</from><to><date><jformat>MMMMMMMMM dd, yyyy</jformat></date></to></interval>
     */

    private String interval(Node node, ParseState ps) {
        //HashSet<String> attributeNames = Utilities.stringSet("style","jformat","from","to");
        String style = getAttributeOrTagValue(node, ps, "style");      // AIML 2.0
        String jformat = getAttributeOrTagValue(node, ps, "jformat");      // AIML 2.0
        String from = getAttributeOrTagValue(node, ps, "from");
        String to = getAttributeOrTagValue(node, ps, "to");
        if (style == null) { style = "years"; }
        if (jformat == null) { jformat = "MMMMMMMMM dd, yyyy"; }
        if (from == null) { from = CalendarUtils.format(new Date(0), jformat); }
        if (to == null) { to = CalendarUtils.date(jformat); }
        return IntervalUtils.getInterval(from, to, jformat, style).map(Number::toString).orElse("unknown");
    }

    /**
     * get the value of an index attribute and return it as an integer.
     * if it is not recognized as an integer, return 0
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the the integer intex value
     */
    private int getIndexValue(Node node, ParseState ps) {
        String value = getAttributeOrTagValue(node, ps, "index");
        if (value != null) {
            try {
                return Integer.parseInt(value) - 1;
            } catch (Exception ex) {
                logger.error("getIndexValue error", ex);
            }
        }
        return 0;
    }

    /**
     * implements {@code <star index="N"/>}
     * returns the value of input words matching the Nth wildcard (or AIML Set).
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the word sequence matching a wildcard
     */
    private String inputStar(Node node, ParseState ps) {
        int index = getIndexValue(node, ps);
        return ps.starBindings.inputStars.star(index) == null ? "" : ps.starBindings.inputStars.star(index).trim();
    }

    /**
     * implements {@code <thatstar index="N"/>}
     * returns the value of input words matching the Nth wildcard (or AIML Set) in <that></that>.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the word sequence matching a wildcard
     */
    private String thatStar(Node node, ParseState ps) {
        int index = getIndexValue(node, ps);
        return ps.starBindings.thatStars.star(index) == null ? "" : ps.starBindings.thatStars.star(index).trim();
    }

    /**
     * implements <topicstar/> and <topicstar index="N"/>
     * returns the value of input words matching the Nth wildcard (or AIML Set) in a topic pattern.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the word sequence matching a wildcard
     */
    private String topicStar(Node node, ParseState ps) {
        int index = getIndexValue(node, ps);
        return ps.starBindings.topicStars.star(index) == null ? "" : ps.starBindings.topicStars.star(index).trim();
    }

    /**
     * return the client ID.
     * implements {@code <id/>}
     *
     * @return client ID
     */

    private String id() {
        return chatSession.customerId;
    }

    /**
     * return the size of the robot brain (number of AIML categories loaded).
     * implements {@code <size/>}
     *
     * @return bot brain size
     */
    private String size() {
        int size = chatSession.bot.brain.getCategories().size();
        return String.valueOf(size);
    }

    /**
     * return the size of the robot vocabulary (number of words the bot can recognize).
     * implements {@code <vocabulary/>}
     *
     * @return bot vocabulary size
     */
    private String vocabulary() {
        int size = chatSession.bot.brain.getVocabulary().size();
        return String.valueOf(size);
    }

    /**
     * return a string indicating the name and version of the AIML program.
     * implements {@code <program/>}
     *
     * @return AIML program name and version.
     */
    private String program() {
        return MagicStrings.program_name_version;
    }

    /**
     * implements the (template-side) {@code <that index="M,N"/>}    tag.
     * returns a normalized sentence.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the nth last sentence of the bot's mth last reply.
     */
    private String that(Node node, ParseState ps) {
        int index = 0;
        int jndex = 0;
        String value = getAttributeOrTagValue(node, ps, "index");
        if (value != null) {
            try {
                String[] spair = value.split(",");
                index = Integer.parseInt(spair[0]) - 1;
                jndex = Integer.parseInt(spair[1]) - 1;
                logger.info("That index={},{}", index, jndex);
            } catch (Exception ex) {
                logger.error("that error", ex);
            }
        }
        String that = AIMLDefault.unknown_history_item;
        History<String> hist = chatSession.thatHistory.get(index);
        if (hist != null) { that = hist.get(jndex); }
        return that.trim();
    }

    /**
     * implements {@code <input index="N"/>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the nth last sentence input to the bot
     */

    private String input(Node node, ParseState ps) {
        int index = getIndexValue(node, ps);
        return chatSession.inputHistory.get(index);
    }

    /**
     * implements {@code <request index="N"/>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the nth last multi-sentence request to the bot.
     */
    private String request(Node node, ParseState ps) {             // AIML 2.0
        int index = getIndexValue(node, ps);
        return chatSession.requestHistory.get(index).trim();
    }

    /**
     * implements {@code <response index="N"/>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the bot's Nth last multi-sentence response.
     */
    private String response(Node node, ParseState ps) {            // AIML 2.0
        int index = getIndexValue(node, ps);
        return chatSession.responseHistory.get(index).trim();
    }

    /**
     * implements {@code <system>} tag.
     * Evaluate the contents, and try to execute the result as
     * a command in the underlying OS shell.
     * Read back and return the result of this command.
     * <p>
     * The timeout parameter allows the botmaster to set a timeout
     * in ms, so that the <system></system>   command returns eventually.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return the result of executing the system command or a string indicating the command failed.
     */
    private String system(Node node, ParseState ps) {
        Set<String> attributeNames = Utilities.stringSet("timeout");
        //String stimeout = getAttributeOrTagValue(node, ps, "timeout");
        String evaluatedContents = evalTagContent(node, ps, attributeNames);
        return IOUtils.system(evaluatedContents, StandardResponse.SYSTEM_FAILED);
    }

    /**
     * implements {@code <think>} tag
     * <p>
     * Evaluate the tag contents but return a blank.
     * "Think but don't speak."
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return a blank empty string
     */
    private String think(Node node, ParseState ps) {
        evalTagContent(node, ps, null);
        return "";
    }

    /**
     * Transform a string of words (separtaed by spaces) into
     * a string of individual characters (separated by spaces).
     * Explode "ABC DEF" = "A B C D E F".
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return exploded string
     */
    private String explode(Node node, ParseState ps) {              // AIML 2.0
        String result = evalTagContent(node, ps, null);
        return explode(result);
    }

    /**
     * apply the AIML normalization pre-processor to the evaluated tag contenst.
     * implements {@code <normalize>} tag.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return normalized string
     */
    private String normalize(Node node, ParseState ps) {            // AIML 2.0
        String result = evalTagContent(node, ps, null);
        return chatSession.bot.preProcessor.normalize(result);
    }

    /**
     * apply the AIML denormalization pre-processor to the evaluated tag contenst.
     * implements {@code <normalize>} tag.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return denormalized string
     */
    private String denormalize(Node node, ParseState ps) {            // AIML 2.0
        String result = evalTagContent(node, ps, null);
        return chatSession.bot.preProcessor.denormalize(result);
    }

    /**
     * evaluate tag contents and return result in upper case
     * implements {@code <uppercase>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return uppercase string
     */
    private String uppercase(Node node, ParseState ps) {
        String result = evalTagContent(node, ps, null);
        return result.toUpperCase();
    }

    /**
     * evaluate tag contents and return result in lower case
     * implements {@code <lowercase>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return lowercase string
     */
    private String lowercase(Node node, ParseState ps) {
        String result = evalTagContent(node, ps, null);
        return result.toLowerCase();
    }

    /**
     * evaluate tag contents and capitalize each word.
     * implements {@code <formal>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return capitalized string
     */
    private String formal(Node node, ParseState ps) {
        String result = evalTagContent(node, ps, null);
        return capitalizeString(result);
    }

    /**
     * evaluate tag contents and capitalize the first word.
     * implements {@code <sentence>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return string with first word capitalized
     */
    private String sentence(Node node, ParseState ps) {
        String result = evalTagContent(node, ps, null);
        return result.length() > 1
            ? result.substring(0, 1).toUpperCase() + result.substring(1, result.length())
            : "";
    }

    /**
     * evaluate tag contents and swap 1st and 2nd person pronouns
     * implements {@code <person>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return sentence with pronouns swapped
     */
    private String person(Node node, ParseState ps) {
        String result;
        if (node.hasChildNodes()) {
            result = evalTagContent(node, ps, null);
        } else {
            result = ps.starBindings.inputStars.star(0);   // for <person/>
        }
        result = " " + result + " ";
        result = chatSession.bot.preProcessor.person(result);
        return result.trim();
    }

    /**
     * evaluate tag contents and swap 1st and 3rd person pronouns
     * implements {@code <person2>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return sentence with pronouns swapped
     */
    private String person2(Node node, ParseState ps) {
        String result;
        if (node.hasChildNodes()) {
            result = evalTagContent(node, ps, null);
        } else {
            result = ps.starBindings.inputStars.star(0);   // for <person2/>
        }
        result = " " + result + " ";
        result = chatSession.bot.preProcessor.person2(result);
        return result.trim();
    }

    /**
     * implements {@code <gender>} tag
     * swaps gender pronouns
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return sentence with gender ronouns swapped
     */
    private String gender(Node node, ParseState ps) {
        String result = evalTagContent(node, ps, null);
        result = " " + result + " ";
        result = chatSession.bot.preProcessor.gender(result);
        return result.trim();
    }

    /**
     * implements {@code <random>} tag
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return response randomly selected from the list
     */
    private String random(Node node, ParseState ps) {
        NodeList childList = node.getChildNodes();
        List<Node> liList = new ArrayList<>();
        for (int i = 0; i < childList.getLength(); i++) {
            if ("li".equals(childList.item(i).getNodeName())) { liList.add(childList.item(i)); }
        }
        int index = (int) (Math.random() * liList.size());
        if (MagicBooleans.qa_test_mode) { index = 0; }
        return evalTagContent(liList.get(index), ps, null);
    }

    private String unevaluatedAIML(Node node, ParseState ps) {
        String result = learnEvalTagContent(node, ps);
        return unevaluatedXML(result, node);
    }

    private String recursLearn(Node node, ParseState ps) {
        String nodeName = node.getNodeName();
        switch (nodeName) {
            case "#text":
                return node.getNodeValue();
            case "eval":
                return evalTagContent(node, ps, null);                // AIML 2.0
            default:
                return unevaluatedAIML(node, ps);
        }
    }

    private String learnEvalTagContent(Node node, ParseState ps) {
        StringBuilder result = new StringBuilder();
        NodeList childList = node.getChildNodes();
        for (int i = 0; i < childList.getLength(); i++) {
            Node child = childList.item(i);
            result.append(recursLearn(child, ps));
        }
        return result.toString();
    }

    private String learn(Node node, ParseState ps) {                 // learn, learnf AIML 2.0
        NodeList childList = node.getChildNodes();
        String pattern = "";
        String that = "*";
        String template = "";
        for (int i = 0; i < childList.getLength(); i++) {
            if ("category".equals(childList.item(i).getNodeName())) {
                NodeList grandChildList = childList.item(i).getChildNodes();
                for (int j = 0; j < grandChildList.getLength(); j++) {
                    if ("pattern".equals(grandChildList.item(j).getNodeName())) {
                        pattern = recursLearn(grandChildList.item(j), ps);
                    } else if ("that".equals(grandChildList.item(j).getNodeName())) {
                        that = recursLearn(grandChildList.item(j), ps);
                    } else if ("template".equals(grandChildList.item(j).getNodeName())) {
                        template = recursLearn(grandChildList.item(j), ps);
                    }
                }
                pattern = pattern.substring("<pattern>".length(), pattern.length() - "</pattern>".length());
                logger.debug("Learn Pattern = {}", pattern);
                if (template.length() >= "<template></template>".length()) {
                    template = template.substring("<template>".length(), template.length() - "</template>".length());
                }
                if (that.length() >= "<that></that>".length()) {
                    that = that.substring("<that>".length(), that.length() - "</that>".length());
                }
                pattern = pattern.toUpperCase();
                pattern = pattern.replaceAll("\n", " ");
                pattern = pattern.replaceAll("[ ]+", " ");
                that = that.toUpperCase();
                that = that.replaceAll("\n", " ");
                that = that.replaceAll("[ ]+", " ");
                logger.debug("Learn Pattern = {}", pattern);
                logger.debug("Learn That = {}", that);
                logger.debug("Learn Template = {}", template);
                Category c;
                if ("learn".equals(node.getNodeName())) {
                    c = new Category(0, pattern, that, "*", template, AIMLFile.NULL);
                    chatSession.bot.learnGraph.addCategory(c);
                } else {// learnf
                    c = new Category(0, pattern, that, "*", template, AIMLFile.LEARNF);
                    chatSession.bot.learnfGraph.addCategory(c);
                }
                chatSession.bot.brain.addCategory(c);
                //chatSession.bot.brain.printgraph();
            }
        }
        return "";
    }

    /**
     * implements {@code <condition> with <loop/>}
     * re-evaluate the conditional statement until the response does not contain {@code <loop/>}
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return result of conditional expression
     */
    private String loopCondition(Node node, ParseState ps) {
        boolean loop = true;
        StringBuilder result = new StringBuilder();
        int loopCnt = 0;
        while (loop && loopCnt < MagicNumbers.max_loops) {
            loopCnt++;
            String loopResult = condition(node, ps);
            if (loopResult.trim().equals(AIMLDefault.too_much_recursion)) { return AIMLDefault.too_much_recursion; }
            if (loopResult.contains("<loop/>")) {
                loopResult = loopResult.replace("<loop/>", "");
                loop = true;
            } else {
                loop = false;
            }
            result.append(loopResult);
        }
        if (loopCnt >= MagicNumbers.max_loops) { return AIMLDefault.too_much_looping; }
        return result.toString();
    }

    /**
     * implements all 3 forms of the {@code <condition> tag}
     * In AIML 2.0 the conditional may return a {@code <loop/>}
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     * @return result of conditional expression
     */
    private String condition(Node node, ParseState ps) {
        //boolean loop = true;
        NodeList childList = node.getChildNodes();
        List<Node> liList = new ArrayList<>();
        Set<String> attributeNames = Utilities.stringSet("name", "var", "value");
        // First check if the <condition> has an attribute "name".  If so, get the predicate name.
        String predicate = getAttributeOrTagValue(node, ps, "name");
        String varName = getAttributeOrTagValue(node, ps, "var");
        // Make a list of all the <li> child nodes:
        for (int i = 0; i < childList.getLength(); i++) {
            if ("li".equals(childList.item(i).getNodeName())) { liList.add(childList.item(i)); }
        }
        // if there are no <li> nodes, this is a one-shot condition.
        String value;
        if (liList.isEmpty() && (value = getAttributeOrTagValue(node, ps, "value")) != null) {
            if (predicate != null && chatSession.predicates.get(predicate).equalsIgnoreCase(value)) {
                return evalTagContent(node, ps, attributeNames);
            }
            if (varName != null && ps.vars.get(varName).equalsIgnoreCase(value)) {
                return evalTagContent(node, ps, attributeNames);
            }
        }
        // otherwise this is a <condition> with <li> items:
        String result = "";
        for (int i = 0; i < liList.size() && result.isEmpty(); i++) {
            Node n = liList.get(i);
            String liPredicate = predicate;
            String liVarName = varName;
            if (liPredicate == null) { liPredicate = getAttributeOrTagValue(n, ps, "name"); }
            if (liVarName == null) { liVarName = getAttributeOrTagValue(n, ps, "var"); }
            value = getAttributeOrTagValue(n, ps, "value");
            //System.out.println("condition name="+liPredicate+" value="+value);
            if (value == null) {
                // this is a terminal <li> with no predicate or value, i.e. the default condition.
                return evalTagContent(n, ps, attributeNames);
            }
            // if the predicate equals the value, return the <li> item.
            if (checkPredicate(liPredicate, chatSession.predicates, value)) {
                return evalTagContent(n, ps, attributeNames);
            }
            if (checkPredicate(liVarName, ps.vars, value)) {
                return evalTagContent(n, ps, attributeNames);
            }
        }
        return "";

    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean checkPredicate(String predicate, Predicates predicates, String value) {
        if (predicate == null) { return false; }
        if (predicates.get(predicate).equalsIgnoreCase(value)) { return true; }
        if (predicates.contains(predicate) && "*".equals(value)) { return true; }
        return false;
    }

    private String deleteTriple(Node node, ParseState ps) {
        String subject = getAttributeOrTagValue(node, ps, "subj");
        String predicate = getAttributeOrTagValue(node, ps, "pred");
        String object = getAttributeOrTagValue(node, ps, "obj");
        return chatSession.tripleStore.deleteTriple(subject, predicate, object);
    }

    private String addTriple(Node node, ParseState ps) {
        String subject = getAttributeOrTagValue(node, ps, "subj");
        String predicate = getAttributeOrTagValue(node, ps, "pred");
        String object = getAttributeOrTagValue(node, ps, "obj");
        return chatSession.tripleStore.addTriple(subject, predicate, object);
    }

    private String uniq(Node node, ParseState ps) {
        HashSet<String> vars = new HashSet<>();
        HashSet<String> visibleVars = new HashSet<>();
        String subj = "?subject";
        String pred = "?predicate";
        String obj = "?object";
        NodeList childList = node.getChildNodes();
        for (int j = 0; j < childList.getLength(); j++) {
            Node childNode = childList.item(j);
            String contents = evalTagContent(childNode, ps, null);
            if ("subj".equals(childNode.getNodeName())) {
                subj = contents;
            } else if ("pred".equals(childNode.getNodeName())) {
                pred = contents;
            } else if ("obj".equals(childNode.getNodeName())) {
                obj = contents;
            }
            if (contents.startsWith("?")) {
                visibleVars.add(contents);
                vars.add(contents);
            }
        }
        Tuple partial = new Tuple(vars, visibleVars);
        Clause clause = new Clause(subj, pred, obj);
        Set<Tuple> tuples = chatSession.tripleStore.selectFromSingleClause(partial, clause, true);
        String tupleList = tuples.stream()
            .map(Tuple::name).collect(Collectors.joining(" "))
            .trim();
        if (tupleList.isEmpty()) { tupleList = "NIL"; }
        String var = "";
        for (String x : visibleVars) {
            var = x;
        }
        String firstTuple = firstWord(tupleList);
        return tupleGet(firstTuple, var);
    }

    private String select(Node node, ParseState ps) {
        List<Clause> clauses = new ArrayList<>();
        NodeList childList = node.getChildNodes();
        //String[] splitTuple;
        HashSet<String> vars = new HashSet<>();
        Set<String> visibleVars = new HashSet<>();
        for (int i = 0; i < childList.getLength(); i++) {
            Node childNode = childList.item(i);
            if ("vars".equals(childNode.getNodeName())) {
                String contents = evalTagContent(childNode, ps, null);
                String[] splitVars = contents.split(" ");
                for (String var : splitVars) {
                    var = var.trim();
                    if (!var.isEmpty()) { visibleVars.add(var); }
                }
                // System.out.println("AIML Processor select: visible vars "+visibleVars);
            } else if ("q".equals(childNode.getNodeName()) || "notq".equals(childNode.getNodeName())) {
                Boolean affirm = !"notq".equals(childNode.getNodeName());
                NodeList grandChildList = childNode.getChildNodes();
                String subj = null;
                String pred = null;
                String obj = null;
                for (int j = 0; j < grandChildList.getLength(); j++) {
                    Node grandChildNode = grandChildList.item(j);
                    String contents = evalTagContent(grandChildNode, ps, null);
                    if ("subj".equals(grandChildNode.getNodeName())) {
                        subj = contents;
                    } else if ("pred".equals(grandChildNode.getNodeName())) {
                        pred = contents;
                    } else if ("obj".equals(grandChildNode.getNodeName())) {
                        obj = contents;
                    }
                    if (contents.startsWith("?")) { vars.add(contents); }

                }
                Clause clause = new Clause(subj, pred, obj, affirm);
                //System.out.println("Vars "+vars+" Clause "+subj+" "+pred+" "+obj+" "+affirm);
                clauses.add(clause);

            }
        }
        Set<Tuple> tuples = chatSession.tripleStore.select(vars, visibleVars, clauses);
        String result = tuples.stream()
            .map(Tuple::name).collect(Collectors.joining(" "))
            .trim();
        return result.isEmpty() ? "NIL" : result;
    }

    private String javascript(Node node, ParseState ps) {
        //MagicBooleans.trace("AIMLProcessor.javascript(node: " + node + ", ps: " + ps + ")");
        String script = evalTagContent(node, ps, null);
        try {
            String result = IOUtils.evalScript(script);
            logger.debug("in AIMLProcessor.javascript, returning result: {}", result);
            return result;
        } catch (Exception ex) {
            logger.error("javascript error", ex);
            return AIMLDefault.BAD_JAVASCRIPT;
        }
    }

    private String firstWord(String sentence) {
        String content = (sentence == null ? "" : sentence).trim();
        if (content.isEmpty()) {
            return AIMLDefault.default_list_item;
        } else if (content.contains(" ")) {
            return content.substring(0, content.indexOf(' '));
        } else {
            return content;
        }
    }

    private String restWords(String sentence) {
        String content = (sentence == null ? "" : sentence).trim();
        if (content.contains(" ")) {
            return content.substring(content.indexOf(' ') + 1);
        } else {
            return AIMLDefault.default_list_item;
        }
    }

    private String first(Node node, ParseState ps) {
        String content = evalTagContent(node, ps, null);
        return firstWord(content);

    }

    private String rest(Node node, ParseState ps) {
        String content = evalTagContent(node, ps, null);
        content = chatSession.bot.preProcessor.normalize(content);
        return restWords(content);

    }

    private String resetlearnf() {
        chatSession.bot.deleteLearnfCategories();
        return "Deleted Learnf Categories";

    }

    private String resetlearn() {
        chatSession.bot.deleteLearnCategories();
        return "Deleted Learn Categories";

    }

    /**
     * Recursively descend the XML DOM tree, evaluating AIML and building a response.
     *
     * @param node current XML parse node
     * @param ps   AIML parse state
     */

    private String recursEval(Node node, ParseState ps) {
        //MagicBooleans.trace("AIMLProcessor.recursEval(node: " + node + ", ps: " + ps + ")");
        try {
            //MagicBooleans.trace("in AIMLProcessor.recursEval(), node string: " + DomUtils.nodeToString(node));
            String nodeName = node.getNodeName();
            //MagicBooleans.trace("in AIMLProcessor.recursEval(), nodeName: " + nodeName);
            //MagicBooleans.trace("in AIMLProcessor.recursEval(), node.getNodeValue(): " + node.getNodeValue());
            if ("#text".equals(nodeName)) {
                return node.getNodeValue();
            } else if ("#comment".equals(nodeName)) {
                //MagicBooleans.trace("in AIMLProcessor.recursEval(), comment = "+node.getTextContent());
                return "";
            } else if ("template".equals(nodeName)) {
                return evalTagContent(node, ps, null);
            } else if ("random".equals(nodeName)) {
                return random(node, ps);
            } else if ("condition".equals(nodeName)) {
                return loopCondition(node, ps);
            } else if ("srai".equals(nodeName)) {
                return srai(node, ps);
            } else if ("sr".equals(nodeName)) {
                return doRespond(ps.starBindings.inputStars.star(0), ps.that, ps.topic);
            } else if ("sraix".equals(nodeName)) {
                return sraix(node, ps);
            } else if ("set".equals(nodeName)) {
                return set(node, ps);
            } else if ("get".equals(nodeName)) {
                return get(node, ps);
            } else if ("map".equals(nodeName)) { // AIML 2.0 -- see also <set> in pattern
                return map(node, ps);
            } else if ("bot".equals(nodeName)) {
                return bot(node, ps);
            } else if ("id".equals(nodeName)) {
                return id();
            } else if ("size".equals(nodeName)) {
                return size();
            } else if ("vocabulary".equals(nodeName)) {// AIML 2.0
                return vocabulary();
            } else if ("program".equals(nodeName)) {
                return program();
            } else if ("date".equals(nodeName)) {
                return date(node, ps);
            } else if ("interval".equals(nodeName)) {
                return interval(node, ps);
            }
            //else if (nodeName.equals("gossip"))       // removed from AIML 2.0
            //    return gossip(node, ps);
            else if ("think".equals(nodeName)) {
                return think(node, ps);
            } else if ("system".equals(nodeName)) {
                return system(node, ps);
            } else if ("explode".equals(nodeName)) {
                return explode(node, ps);
            } else if ("normalize".equals(nodeName)) {
                return normalize(node, ps);
            } else if ("denormalize".equals(nodeName)) {
                return denormalize(node, ps);
            } else if ("uppercase".equals(nodeName)) {
                return uppercase(node, ps);
            } else if ("lowercase".equals(nodeName)) {
                return lowercase(node, ps);
            } else if ("formal".equals(nodeName)) {
                return formal(node, ps);
            } else if ("sentence".equals(nodeName)) {
                return sentence(node, ps);
            } else if ("person".equals(nodeName)) {
                return person(node, ps);
            } else if ("person2".equals(nodeName)) {
                return person2(node, ps);
            } else if ("gender".equals(nodeName)) {
                return gender(node, ps);
            } else if ("star".equals(nodeName)) {
                return inputStar(node, ps);
            } else if ("thatstar".equals(nodeName)) {
                return thatStar(node, ps);
            } else if ("topicstar".equals(nodeName)) {
                return topicStar(node, ps);
            } else if ("that".equals(nodeName)) {
                return that(node, ps);
            } else if ("input".equals(nodeName)) {
                return input(node, ps);
            } else if ("request".equals(nodeName)) {
                return request(node, ps);
            } else if ("response".equals(nodeName)) {
                return response(node, ps);
            } else if ("learn".equals(nodeName) || "learnf".equals(nodeName)) {
                return learn(node, ps);
            } else if ("addtriple".equals(nodeName)) {
                return addTriple(node, ps);
            } else if ("deletetriple".equals(nodeName)) {
                return deleteTriple(node, ps);
            } else if ("javascript".equals(nodeName)) {
                return javascript(node, ps);
            } else if ("select".equals(nodeName)) {
                return select(node, ps);
            } else if ("uniq".equals(nodeName)) {
                return uniq(node, ps);
            } else if ("first".equals(nodeName)) {
                return first(node, ps);
            } else if ("rest".equals(nodeName)) {
                return rest(node, ps);
            } else if ("resetlearnf".equals(nodeName)) {
                return resetlearnf();
            } else if ("resetlearn".equals(nodeName)) {
                return resetlearn();
            } else if (hasExtensionFor(nodeName)) {
                return extensionProcess(node, ps);
            } else {
                return (genericXML(node, ps));
            }
        } catch (Exception ex) {
            logger.error("recursEval error", ex);
            return "";
        }
    }

    public static void registerExtensions(AIMLProcessorExtension... extensions) {
        Collections.addAll(AIMLProcessor.extensions, extensions);
    }

    private static boolean hasExtensionFor(String tagName) {
        return extensions.stream().anyMatch(e -> e.canProcessTag(tagName));
    }

    private String extensionProcess(Node node, ParseState ps) {
        return extensions.stream()
            .filter(e -> e.canProcessTag(node.getNodeName())).findFirst()
            .map(e -> e.recursEval(node, n -> evalTagContent(n, ps, null)))
            .orElseThrow(() -> new IllegalArgumentException("No extension for " + node.getNodeName()));
    }

    /**
     * evaluate an AIML template expression
     *
     * @param template AIML template contents
     * @param ps       AIML Parse state
     * @return result of evaluating template.
     */
    private String evalTemplate(String template, ParseState ps) {
        //MagicBooleans.trace("AIMLProcessor.evalTemplate(template: " + template + ", ps: " + ps + ")");
        try {
            template = "<template>" + template + "</template>";
            Node root = DomUtils.parseString(template);
            return recursEval(root, ps);
        } catch (Exception e) {
            logger.error("evalTemplate error", e);
            return AIMLDefault.template_failed;
        }
    }

}
