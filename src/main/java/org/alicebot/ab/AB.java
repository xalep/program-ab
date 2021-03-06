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

import org.alicebot.ab.aiml.AIMLDefault;
import org.alicebot.ab.aiml.AIMLFile;
import org.alicebot.ab.set.MutableSet;
import org.alicebot.ab.utils.IOUtils;
import org.alicebot.ab.utils.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AB {

    private static final Logger logger = LoggerFactory.getLogger(AB.class);
    // default templates
    private static final String DELETED_TEMPLATE = "deleted";
    private static final String UNFINISHED_TEMPLATE = "unfinished";

    /**
     * Experimental class that analyzes log data and suggests
     * new AIML patterns.
     */
    private boolean shuffle_mode = true;
    private boolean sort_mode = !shuffle_mode;
    private boolean filter_atomic_mode = false;
    private boolean filter_wild_mode = false;
    private boolean offer_alice_responses = true;

    private final Path logFile;

    private int runCompletedCnt;
    private Bot bot;
    private Bot alice;
    private MutableSet passed;
    private MutableSet testSet;

    private final Graphmaster inputGraph;
    private final Graphmaster patternGraph;
    private final Graphmaster deletedGraph;
    private ArrayList<Category> suggestedCategories;
    private static int limit = 500000;

    public AB(Bot bot, String sampleFile) {
        logFile = IOUtils.rootPath.resolve("data").resolve(sampleFile);
        logger.info("AB with sample file {}", logFile);
        this.bot = bot;
        this.inputGraph = new Graphmaster(bot, "input");
        this.deletedGraph = new Graphmaster(bot, "deleted");
        this.patternGraph = new Graphmaster(bot, "pattern");
        bot.brain.getCategories().forEach(patternGraph::addCategory);
        this.suggestedCategories = new ArrayList<>();
        passed = new MutableSet("passed");
        testSet = new MutableSet("1000");
        readDeletedIFCategories();
    }

    /**
     * Calculates the botmaster's productivity rate in
     * categories/sec when using Pattern Suggestor to create content.
     *
     * @param runCompletedCnt number of categories completed in this run
     * @param timer           tells elapsed time in ms
     */

    private void productivity(int runCompletedCnt, Timer timer) {
        float time = timer.elapsedTimeMins();
        logger.info("Completed {} in {} min. Productivity {} cat/min",
            runCompletedCnt, time, (float) runCompletedCnt / time);
    }

    private void readDeletedIFCategories() {
        bot.readCertainIFCategories(deletedGraph, AIMLFile.DELETED);
        logger.debug("--- DELETED CATEGORIES -- read {} deleted categories", deletedGraph.getCategories().size());
    }

    private void writeDeletedIFCategories() {
        logger.info("--- DELETED CATEGORIES -- write");
        bot.writeCertainIFCategories(deletedGraph, AIMLFile.DELETED);
        logger.info("--- DELETED CATEGORIES -- write {} deleted categories", deletedGraph.getCategories().size());

    }

    /**
     * saves a new AIML category and increments runCompletedCnt
     *
     * @param pattern  the category's pattern (that and topic = *)
     * @param template the category's template
     * @param filename the filename for the category.
     */
    private void saveCategory(String pattern, String template, String filename) {
        String that = "*";
        String topic = "*";
        Category c = new Category(0, pattern, that, topic, template, filename);

        if (c.validate()) {
            bot.brain.addCategory(c);
            // bot.categories.add(c);
            bot.writeAIMLIFFiles();
            runCompletedCnt++;
        } else {
            logger.info("Invalid Category {}", c.validationMessage);
        }
    }

    /**
     * mark a category as deleted
     *
     * @param c the category
     */
    private void deleteCategory(Category c) {
        c.setFilename(AIMLFile.DELETED);
        c.setTemplate(DELETED_TEMPLATE);
        deletedGraph.addCategory(c);
        logger.info("--- bot.writeDeletedIFCategories()");
        writeDeletedIFCategories();
    }

    /**
     * skip a category.  Make the category as "unfinished"
     *
     * @param c the category
     */
    private void skipCategory(Category c) {
       /* c.setFilename(AIMLFile.UNFINISHED);
        c.setTemplate(UNFINISHED_TEMPLATE);
        bot.unfinishedGraph.addCategory(c);
        System.out.println(bot.unfinishedGraph.getCategories().size() + " unfinished categories");
        bot.writeUnfinishedIFCategories();*/
    }

    public void abwq() {
        Timer timer = new Timer();
        timer.start();
        classifyInputs();
        logger.info("{} classifying inputs", timer.elapsedTimeSecs());
        bot.writeQuit();
    }

    /**
     * read sample inputs from log file, turn them into Paths, and
     * add them to the graph.
     */
    private void graphInputs() {
        try {
            Files.lines(logFile).limit(limit).forEach(strLine -> {
                //strLine = preProcessor.normalize(strLine);
                Category c = new Category(0, strLine, "*", "*", "nothing", AIMLFile.UNKNOWN);
                Nodemapper node = inputGraph.findNode(c);
                if (node == null) {
                    inputGraph.addCategory(c);
                    c.incrementActivationCnt();
                } else {
                    node.category.incrementActivationCnt();
                }
            });
        } catch (Exception e) {
            logger.error("graphInputs error", e);
        }
    }

    private static int leafPatternCnt = 0;
    private static int starPatternCnt = 0;

    /**
     * find suggested patterns in a graph of inputs
     */
    private void findPatterns() {
        findPatterns(inputGraph.root, "");
        logger.info("{} Leaf Patterns {} Star Patterns", leafPatternCnt, starPatternCnt);
    }

    /**
     * find patterns recursively
     *
     * @param node                    current graph node
     * @param partialPatternThatTopic partial pattern path
     */
    private void findPatterns(Nodemapper node, String partialPatternThatTopic) {
        if (node.isLeaf()) {
            //System.out.println("LEAF: "+node.category.getActivationCnt()+". "+partialPatternThatTopic);
            if (node.category.getActivationCnt() > MagicNumbers.node_activation_cnt) {
                //System.out.println("LEAF: "+node.category.getActivationCnt()+". "+partialPatternThatTopic+" "+node.shortCut);    //Start writing to the output stream
                leafPatternCnt++;
                try {
                    String categoryPatternThatTopic;
                    if (node.shortCut) {
                        //System.out.println("Partial patternThatTopic = "+partialPatternThatTopic);
                        categoryPatternThatTopic = partialPatternThatTopic + " <THAT> * <TOPIC> *";
                    } else {
                        categoryPatternThatTopic = partialPatternThatTopic;
                    }
                    Category c = new Category(0, categoryPatternThatTopic, AIMLDefault.blank_template, AIMLFile.UNKNOWN);
                    //if (brain.existsCategory(c)) System.out.println(c.inputThatTopic()+" Exists");
                    //if (deleted.existsCategory(c)) System.out.println(c.inputThatTopic()+ " Deleted");
                    if (!bot.brain.existsCategory(c) && !deletedGraph.existsCategory(c)/* && !unfinishedGraph.existsCategory(c)*/) {
                        patternGraph.addCategory(c);
                        suggestedCategories.add(c);
                    }
                } catch (Exception e) {
                    logger.error("findPatterns error", e);
                }
            }
        }
        if (node.size() > MagicNumbers.node_size) {
            //System.out.println("STAR: "+node.size()+". "+partialPatternThatTopic+" * <that> * <topic> *");
            starPatternCnt++;
            try {
                Category c = new Category(0, partialPatternThatTopic + " * <THAT> * <TOPIC> *", AIMLDefault.blank_template, AIMLFile.UNKNOWN);
                //if (brain.existsCategory(c)) System.out.println(c.inputThatTopic()+" Exists");
                //if (deleted.existsCategory(c)) System.out.println(c.inputThatTopic()+ " Deleted");
                if (!bot.brain.existsCategory(c) && !deletedGraph.existsCategory(c)/* && !unfinishedGraph.existsCategory(c)*/) {
                    patternGraph.addCategory(c);
                    suggestedCategories.add(c);
                }
            } catch (Exception e) {
                logger.error("findPatterns error", e);
            }
        }
        for (String key : node.keySet()) {
            Nodemapper value = node.get(key);
            findPatterns(value, partialPatternThatTopic + " " + key);
        }

    }

    /**
     * classify inputs into matching categories
     */
    private void classifyInputs() {
        try {
            long count = Files.lines(logFile)
                .map(l -> l.startsWith("Human: ") ? l.substring("Human: ".length()) : l)
                .flatMap(l -> Stream.of(bot.preProcessor.sentenceSplit(l)))
                .filter(s -> !s.isEmpty())
                .limit(limit)
                .peek(sentence -> {
                    Nodemapper match = patternGraph.match(sentence, "unknown", "unknown");
                    if (match == null) {
                        logger.info("{} null match", sentence);
                    } else {
                        match.category.incrementActivationCnt();
                        //logger.debug("{} matched {}", sentence, match.category.inputThatTopic());
                    }
                }).count();
            logger.info("Finished classifying {} inputs", count);
        } catch (Exception e) {
            logger.error("classifyInputs error", e);
        }
    }

    /**
     * magically suggests new patterns for a bot.
     * Reads an input file of sample data called logFile.
     * Builds a graph of all the inputs.
     * Finds new patterns in the graph that are not already in the bot.
     * Classifies input log into those new patterns.
     */
    public void ab() {
        LogUtil.activateDebug(false);
        MagicBooleans.enable_external_sets = false;
        if (offer_alice_responses) { alice = new Bot("alice"); }
        Timer timer = new Timer();
        bot.brain.nodeStats();
        if (bot.brain.getCategories().size() < MagicNumbers.brain_print_size) { bot.brain.printgraph(); }
        timer.start();
        logger.info("Graphing inputs");
        graphInputs();
        logger.info("{} seconds Graphing inputs", timer.elapsedTimeSecs());
        inputGraph.nodeStats();
        if (inputGraph.getCategories().size() < MagicNumbers.brain_print_size) { inputGraph.printgraph(); }
        //bot.inputGraph.printgraph();
        timer.start();
        logger.info("Finding Patterns");
        findPatterns();
        logger.info("{} suggested categories", suggestedCategories.size());
        logger.info("{} seconds finding patterns", timer.elapsedTimeSecs());
        timer.start();
        patternGraph.nodeStats();
        if (patternGraph.getCategories().size() < MagicNumbers.brain_print_size) { patternGraph.printgraph(); }
        logger.info("Classifying Inputs from {}", logFile);
        classifyInputs();
        logger.info("{} classifying inputs", timer.elapsedTimeSecs());
    }

    private List<Category> nonZeroActivationCount(List<Category> suggestedCategories) {
        return suggestedCategories.stream()
            .filter(c -> c.getActivationCnt() > 0)
            .collect(Collectors.toList());
    }

    /**
     * train the bot through a terminal interaction
     */
    public void terminalInteraction() {
        sort_mode = !shuffle_mode;
        // if (sort_mode)
        Collections.sort(suggestedCategories, Category.ACTIVATION_COMPARATOR);
        ArrayList<Category> topSuggestCategories = new ArrayList<>();
        for (int i = 0; i < 10000 && i < suggestedCategories.size(); i++) {
            topSuggestCategories.add(suggestedCategories.get(i));
        }
        suggestedCategories = topSuggestCategories;
        if (shuffle_mode) { Collections.shuffle(suggestedCategories); }
        Timer timer = new Timer();
        timer.start();
        runCompletedCnt = 0;
        List<Category> filteredAtomicCategories = new ArrayList<>();
        List<Category> filteredWildCategories = new ArrayList<>();
        for (Category c : suggestedCategories) {
            if (!c.getPattern().contains("*")) {
                filteredAtomicCategories.add(c);
            } else {
                filteredWildCategories.add(c);
            }
        }
        List<Category> browserCategories;
        if (filter_atomic_mode) { browserCategories = filteredAtomicCategories; } else if (filter_wild_mode) {
            browserCategories = filteredWildCategories;
        } else {
            browserCategories = suggestedCategories;
        }
        // System.out.println(filteredAtomicCategories.size()+" filtered suggested categories");
        browserCategories = nonZeroActivationCount(browserCategories);
        boolean firstInteraction = true;
        String alicetemplate = null;
        for (Category c : browserCategories) {
            try {
                List<String> samples = new ArrayList<>(c.getMatches().values());
                Collections.shuffle(samples);
                int sampleSize = Math.min(MagicNumbers.displayed_input_sample_size, samples.size());
                samples.stream().limit(sampleSize).forEach(logger::info);
                logger.info("[{}] {}", c.getActivationCnt(), c.inputThatTopic());
                if (offer_alice_responses) {
                    Nodemapper node = alice.brain.findNode(c);
                    if (node != null) {
                        alicetemplate = node.category.getTemplate();
                        String displayAliceTemplate = alicetemplate;
                        displayAliceTemplate = displayAliceTemplate.replace("\n", " ");
                        if (displayAliceTemplate.length() > 200) {
                            displayAliceTemplate = displayAliceTemplate.substring(0, 200);
                        }
                        logger.info("ALICE: {}", displayAliceTemplate);
                    } else {
                        alicetemplate = null;
                    }
                }

                String textLine = IOUtils.readInputTextLine();
                if (firstInteraction) {
                    timer.start();
                    firstInteraction = false;
                }
                productivity(runCompletedCnt, timer);
                terminalInteractionStep(bot, "", textLine, c, alicetemplate);
            } catch (Exception ex) {
                logger.error("terminalInteraction error", ex);
                logger.info("Returning to Category Browser");
            }
        }
        logger.info("No more samples");
        bot.writeAIMLFiles();
        bot.writeAIMLIFFiles();
    }

    /**
     * process one step of the terminal interaction
     *
     * @param bot      the bot being trained.
     * @param request  used when this routine is called by benchmark testSuite
     * @param textLine response typed by the botmaster
     * @param c        AIML category selected
     */
    private void terminalInteractionStep(Bot bot, String request, String textLine, Category c, String alicetemplate) {
        if (textLine.contains("<pattern>") && textLine.contains("</pattern>")) {
            int index = textLine.indexOf("<pattern>") + "<pattern>".length();
            int jndex = textLine.indexOf("</pattern>");
            int kndex = jndex + "</pattern>".length();
            if (index < jndex) {
                String pattern = textLine.substring(index, jndex);
                c.setPattern(pattern);
                textLine = textLine.substring(kndex, textLine.length());
                logger.info("Got pattern = {} template = {}", pattern, textLine);
            }
        }
        String botThinks = "";
        String[] pronouns = {"he", "she", "it", "we", "they"};
        for (String p : pronouns) {
            if (textLine.contains("<" + p + ">")) {
                textLine = textLine.replace("<" + p + ">", "");
                botThinks = "<think><set name=\"" + p + "\"><set name=\"topic\"><star/></set></set></think>";
            }
        }
        String template;
        if ("q".equals(textLine)) {
            System.exit(0);       // Quit program
        } else if ("wq".equals(textLine)) {   // Write AIML Files and quit program
            bot.writeQuit();
         /*  Nodemapper udcNode = bot.brain.findNode("*", "*", "*");
           if (udcNode != null) {
               AIMLSet udcMatches = new AIMLSet("udcmatches");
               udcMatches.addAll(udcNode.category.getMatches());
               udcMatches.writeSet();
           }*/
          /* Nodemapper cNode = bot.brain.match("JOE MAKES BEER", "unknown", "unknown");
           if (cNode != null) {
               AIMLSet cMatches = new AIMLSet("cmatches");
               cMatches.addAll(cNode.category.getMatches());
               cMatches.writeSet();
           }
           if (passed.size() > 0) {
               AIMLSet difference = new AIMLSet("difference");
               AIMLSet intersection = new AIMLSet("intersection");
               for (String s : passed) if (testSet.contains(s)) intersection.add(s);
               passed = intersection;
               passed.name = "passed";
               difference.addAll(testSet);
               difference.removeAll(passed);
               difference.writeSet();

               passed.writeSet();
               testSet.writeSet();
               System.out.println("Wrote passed test cases");
           }*/
            System.exit(0);
        } else if ("skip".equals(textLine) || textLine.isEmpty()) { // skip this one for now
            skipCategory(c);
        } else if ("s".equals(textLine) || "pass".equals(textLine)) { //
            passed.add(request);
            MutableSet difference = new MutableSet("difference");
            difference.addAll(testSet);
            difference.removeAll(passed);
            difference.writeSet(bot);
            passed.writeSet(bot);
        } else if ("a".equals(textLine)) {
            template = alicetemplate;
            String filename;
            if (template.contains("<sr")) {
                filename = AIMLFile.REDUCTIONS_UPDATE;
            } else {
                filename = AIMLFile.PERSONALITY;
            }
            saveCategory(c.getPattern(), template, filename);
        } else if ("d".equals(textLine)) { // delete this suggested category
            deleteCategory(c);
        } else if ("x".equals(textLine)) {    // ask another bot
            template = "<sraix services=\"pannous\">" + c.getPattern().replace("*", "<star/>") + "</sraix>";
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.SRAIX);
        } else if ("p".equals(textLine)) {   // filter inappropriate content
            template = "<srai>" + AIMLDefault.INAPPROPRIATE_FILTER + "</srai>";
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.INAPPROPRIATE);
        } else if ("f".equals(textLine)) { // filter profanity
            template = "<srai>" + AIMLDefault.PROFANITY_FILTER + "</srai>";
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.PROFANITY);
        } else if ("i".equals(textLine)) {
            template = "<srai>" + AIMLDefault.INSULT_FILTER + "</srai>";
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.INSULT);
        } else if (textLine.contains("<srai>") || textLine.contains("<sr/>")) {
            template = textLine;
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.REDUCTIONS_UPDATE);
        } else if (textLine.contains("<oob>")) {
            template = textLine;
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.OOB);
        } else if (textLine.contains("<set name") || !botThinks.isEmpty()) {
            template = textLine;
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.PREDICATES);
        } else if (textLine.contains("<get name") && !textLine.contains("<get name=\"name")) {
            template = textLine;
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.PREDICATES);
        } else {
            template = textLine;
            template += botThinks;
            saveCategory(c.getPattern(), template, AIMLFile.PERSONALITY);
        }

    }

}

