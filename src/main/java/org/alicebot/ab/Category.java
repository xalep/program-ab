package org.alicebot.ab;
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

import org.alicebot.ab.aiml.AIMLFile;
import org.alicebot.ab.set.MutableSet;
import org.alicebot.ab.utils.DomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

/**
 * structure representing an AIML category and operations on Category
 */
public class Category {

    private static final Logger logger = LoggerFactory.getLogger(Category.class);
    private static final String AIMLIF_SPLIT_CHAR = ",";

    private String pattern;
    private String that;
    private String topic;
    private String template;
    private String filename;
    private int activationCnt;
    private final int categoryNumber; // for loading order
    private static int categoryCnt = 0;
    private MutableSet matches;

    /**
     * Return a set of inputs matching the category
     *
     * @return and AIML Set of elements matching this category
     */
    public MutableSet getMatches() {
        return matches != null ? matches : new MutableSet("No Matches");
    }

    /**
     * number of times a category was activated by inputs
     *
     * @return integer number of activations
     */
    public int getActivationCnt() {
        return activationCnt;
    }

    /**
     * get category pattern
     *
     * @return pattern
     */
    public String getPattern() {
        return pattern == null ? "*" : pattern;
    }

    /**
     * get category that pattern
     *
     * @return that pattern
     */
    public String getThat() {
        return that == null ? "*" : that;
    }

    /**
     * get category topic pattern
     *
     * @return topic pattern
     */
    public String getTopic() {
        return topic == null ? "*" : topic;
    }

    /**
     * get category template
     *
     * @return template
     */
    public String getTemplate() {
        return template == null ? "" : template;
    }

    /**
     * get name of AIML file for this category
     *
     * @return file name
     */
    public String getFilename() {
        return filename == null ? AIMLFile.UNKNOWN : filename;
    }

    /**
     * increment the category activation count
     */
    public void incrementActivationCnt() {
        activationCnt++;
    }

    /**
     * set category activation count
     *
     * @param cnt activation count
     */
    public void setActivationCnt(int cnt) {
        activationCnt = cnt;
    }

    /**
     * set category filename
     *
     * @param filename name of AIML file
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * set category template
     *
     * @param template AIML template
     */
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
     * set category pattern
     *
     * @param pattern AIML pattern
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * set category that pattern
     *
     * @param that AIML that pattern
     */
    public void setThat(String that) {
        this.that = that;
    }

    /**
     * set category topic
     *
     * @param topic AIML topic pattern
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * return a string representing the full pattern path as "{@code input pattern <THAT> that pattern <TOPIC> topic pattern}"
     */
    public String inputThatTopic() {
        return Graphmaster.inputThatTopic(pattern, that, topic);
    }

    /**
     * add a matching input to the matching input set
     *
     * @param input matching input
     */
    public void addMatch(String input) {
        if (matches == null) {
            String setName = this.inputThatTopic().replace("*", "STAR").replace("_", "UNDERSCORE").replace(" ", "-").replace("<THAT>", "THAT").replace("<TOPIC>", "TOPIC");
            // System.out.println("Created match set "+name);
            matches = new MutableSet(setName);
        }
        matches.add(input);
    }

    /**
     * convert a template to a single-line representation by replacing "," with #Comma and newline with #Newline
     *
     * @param template original template
     * @return template on a single line of text
     */
    public static String templateToLine(String template) {
        String result = template;
        result = result.replaceAll("(\r\n|\n\r|\r|\n)", "\\#Newline");
        result = result.replaceAll(AIMLIF_SPLIT_CHAR, MagicStrings.aimlif_split_char_name);
        return result;
    }

    /**
     * restore a template to its original form by replacing #Comma with "," and #Newline with newline.
     *
     * @param line template on a single line of text
     * @return original multi-line template
     */
    private static String lineToTemplate(String line) {
        String result = line.replaceAll("#Newline", "\n");
        result = result.replaceAll(MagicStrings.aimlif_split_char_name, AIMLIF_SPLIT_CHAR);
        return result;
    }

    /**
     * convert a category from AIMLIF format to a Category object
     *
     * @param IF Category in AIMLIF format
     * @return Category object
     */
    public static Category IFToCategory(String IF) {
        String[] split = IF.split(AIMLIF_SPLIT_CHAR);
        //System.out.println("Read: "+split);
        return new Category(Integer.parseInt(split[0]), split[1], split[2], split[3], lineToTemplate(split[4]), split[5]);
    }

    /**
     * convert a Category object to AIMLIF format
     *
     * @return category in AIML format
     */
    public String categoryToIF() {
        //System.out.println("categoryToIF: template="+templateToLine(category.getTemplate()));
        String c = AIMLIF_SPLIT_CHAR;
        return getActivationCnt() + c + getPattern() + c + getThat() + c + getTopic() + c + templateToLine(getTemplate()) + c + getFilename();
    }

    /**
     * convert a Category object to AIML syntax
     *
     * @return AIML Category
     */
    public String categoryToAIML() {
        String pattern = getPattern();
        if (pattern.contains("<SET>") || pattern.contains("<BOT")) {
            String[] splitPattern = pattern.split(" ");
            StringBuilder rpattern = new StringBuilder();
            for (String w : splitPattern) {
                if (w.startsWith("<SET>") || w.startsWith("<BOT") || w.startsWith("NAME=")) {w = w.toLowerCase();}
                rpattern.append(" ").append(w);
            }
            pattern = rpattern.toString().trim();
        }
        //if (pattern.contains("set")) System.out.println("Rebuilt pattern "+pattern);

        String NL = System.getProperty("line.separator");
        NL = "\n";
        String result = "";
        try {
            String topicStart = "";
            String topicEnd = "";
            if (!"*".equals(getTopic())) {
                topicStart = "<topic name=\"" + getTopic() + "\">" + NL;
                topicEnd = "</topic>" + NL;
            }
            String thatStatement = "";
            if (!"*".equals(getThat())) { thatStatement = "<that>" + getThat() + "</that>";}
            result = topicStart + "<category><pattern>" + pattern + "</pattern>" + thatStatement + NL +
                "<template>" + getTemplate() + "</template>" + NL +
                "</category>" + topicEnd;
        } catch (Exception ex) {
            logger.error("categoryToAIML error", ex);
        }
        return result;
    }

    /**
     * check to see if a pattern expression is valid in AIML 2.0
     *
     * @param pattern pattern expression
     * @return true or false
     */
    private boolean validPatternForm(String pattern) {
        if (pattern.length() < 1) {
            validationMessage += "Zero length. ";
            return false;
        }
        String[] words = pattern.split(" ");
        for (String word : words) {
            /*if (!(word.matches("[\\p{Hiragana}\\p{Katakana}\\p{Han}\\p{Latin}]*+") || word.equals("*") || word.equals("_"))) {
                System.out.println("Invalid pattern word "+word);
                return false;
            }*/
        }
        return true;
    }

    /**
     * check to see if a template is a valid XML expression.
     *
     * @param template AIML template contents
     * @return true or false.
     */
    private static boolean validTemplate(String template) {
        logger.debug("validTemplate(template: {})", template);
        try {
            template = "<template>" + template + "</template>";
            DomUtils.parseString(template);
            return true;
        } catch (Exception e) {
            logger.error("Invalid Template {}", template, e);
            return false;
        }

    }

    public String validationMessage = "";

    /**
     * check for valid Category format
     *
     * @return true or false
     */
    public boolean validate() {
        validationMessage = "";
        if (!validPatternForm(pattern)) {
            validationMessage += "Badly formatted <pattern>";
            return false;
        }
        if (!validPatternForm(that)) {
            validationMessage += "Badly formatted <that>";
            return false;
        }
        if (!validPatternForm(topic)) {
            validationMessage += "Badly formatted <topic>";
            return false;
        }
        if (!validTemplate(template)) {
            validationMessage += "Badly formatted <template>";
            return false;
        }
        if (!filename.endsWith(".aiml")) {
            validationMessage += "Filename suffix should be .aiml";
            return false;
        }
        return true;

    }

    /**
     * Constructor
     *
     * @param activationCnt category activation count
     * @param pattern       input pattern
     * @param that          that pattern
     * @param topic         topic pattern
     * @param template      AIML template
     * @param filename      AIML file name
     */

    public Category(int activationCnt, String pattern, String that, String topic, String template, String filename) {
        if (MagicBooleans.fix_excel_csv) {
            pattern = Utilities.fixCSV(pattern);
            that = Utilities.fixCSV(that);
            topic = Utilities.fixCSV(topic);
            template = Utilities.fixCSV(template);
            filename = Utilities.fixCSV(filename);
        }
        this.pattern = pattern.trim().toUpperCase();
        this.that = that.trim().toUpperCase();
        this.topic = topic.trim().toUpperCase();
        this.template = template.replace("& ", " and "); // XML parser treats & badly
        this.filename = filename;
        this.activationCnt = activationCnt;
        matches = null;
        this.categoryNumber = categoryCnt++;
        //System.out.println("Creating "+categoryNumber+" "+inputThatTopic());
    }

    /**
     * Constructor
     *
     * @param activationCnt    category activation count
     * @param patternThatTopic string representing Pattern Path
     * @param template         AIML template
     * @param filename         AIML category
     */
    public Category(int activationCnt, String patternThatTopic, String template, String filename) {
        this(activationCnt,
            patternThatTopic.substring(0, patternThatTopic.indexOf("<THAT>")),
            patternThatTopic.substring(patternThatTopic.indexOf("<THAT>") + "<THAT>".length(), patternThatTopic.indexOf("<TOPIC>")),
            patternThatTopic.substring(patternThatTopic.indexOf("<TOPIC>") + "<TOPIC>".length(), patternThatTopic.length()), template, filename);
    }

    /** Compare two categories for sorting purposes based on activation count (most activated first) */
    public static Comparator<Category> ACTIVATION_COMPARATOR = Comparator.comparing(Category::getActivationCnt).reversed();

    /** Compare two categories for sorting purposes based on category index number */
    public static Comparator<Category> CATEGORY_NUMBER_COMPARATOR = Comparator.comparing(c -> c.categoryNumber);

}
