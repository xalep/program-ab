package org.alicebot.ab;

import org.alicebot.ab.map.MutableMap;
import org.alicebot.ab.set.MutableSet;
import org.alicebot.ab.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Verbs {

    private static final Logger logger = LoggerFactory.getLogger(Verbs.class);

    private Verbs() {}

    private static Set<String> es = Utilities.stringSet("sh", "ch", "th", "ss", "x");
    private static Set<String> ies = Utilities.stringSet("ly", "ry", "ny", "fy", "dy", "py");
    private static Set<String> ring = Utilities.stringSet("be", "me", "re", "se", "ve", "de", "le", "ce", "ze", "ke", "te", "ge", "ne", "pe", "ue");
    private static Set<String> bing = Utilities.stringSet("ab", "at", "op", "el", "in", "ur", "op", "er", "un", "in", "it", "et", "ut", "im", "id", "ol", "ig");
    private static Set<String> notBing = Utilities.stringSet("der", "eat", "ber", "ain", "sit", "ait", "uit", "eet", "ter", "lop", "ver", "wer", "aim", "oid", "eel", "out", "oin", "fer", "vel", "mit");

    private static Set<String> irregular = new HashSet<>();
    private static Map<String, String> be2was = new HashMap<>();
    private static Map<String, String> be2been = new HashMap<>();
    private static Map<String, String> be2is = new HashMap<>();
    private static Map<String, String> be2being = new HashMap<>();
    private static Set<String> allVerbs = new HashSet<>();

    private static String endsWith(String verb, Set<String> endings) {
        for (String x : endings) { if (verb.endsWith(x)) { return x; }}
        return null;
    }

    private static String is(String verb) {
        if (irregular.contains(verb)) { return be2is.get(verb); }
        if (verb.endsWith("go")) { return verb + "es"; }
        if (endsWith(verb, es) != null) { return verb + "es"; }
        if (endsWith(verb, ies) != null) { return verb.substring(0, verb.length() - 1) + "ies"; }
        return verb + "s";
    }

    private static String was(String verb) {
        String ending;
        verb = verb.trim();
        if ("admit".equals(verb)) { return "admitted"; }
        if ("commit".equals(verb)) { return "committed"; }
        if ("die".equals(verb)) { return "died"; }
        if ("agree".equals(verb)) { return "agreed"; }
        if (verb.endsWith("efer")) { return verb + "red"; }

        if (irregular.contains(verb)) {return be2was.get(verb); }
        if (endsWith(verb, ies) != null) {return verb.substring(0, verb.length() - 1) + "ied";}
        if (endsWith(verb, ring) != null) {return verb + "d"; }
        if ((ending = endsWith(verb, bing)) != null && (null == endsWith(verb, notBing))) {
            return verb + ending.substring(1, 2) + "ed";
        }
        return verb + "ed";
    }

    private static String being(String verb) {
        String ending;
        if (irregular.contains(verb)) { return be2being.get(verb); }
        if ("admit".equals(verb)) { return "admitting"; }
        if ("commit".equals(verb)) { return "committing"; }
        if ("quit".equals(verb)) { return "quitting"; }
        if ("die".equals(verb)) { return "dying"; }
        if ("lie".equals(verb)) { return "lying"; }
        if (verb.endsWith("efer")) { return verb + "ring"; }
        if (endsWith(verb, ring) != null) { return verb.substring(0, verb.length() - 1) + "ing";}
        if ((ending = endsWith(verb, bing)) != null && (null == endsWith(verb, notBing))) {
            return verb + ending.substring(1, 2) + "ing";
        }

        return verb + "ing";
    }

    private static String been(String verb) {
        if (irregular.contains(verb)) { return (be2been.get(verb)); }
        return was(verb);
    }

    private static void getIrregulars() {
        // Do, Did, Done, Does, Doing
        // be, was, been, is, being
        IOUtils.lines(IOUtils.rootPath.resolve("data/irrverbs.txt"))
            .map(String::toLowerCase).map(l -> l.split(",")).filter(l -> l.length == 5)
            .forEach(triple -> {
                irregular.add(triple[0]);
                allVerbs.add(triple[0]);
                be2was.put(triple[0], triple[1]);
                be2been.put(triple[0], triple[2]);
                be2is.put(triple[0], triple[3]);
                be2being.put(triple[0], triple[4]);
            });
    }

    public static void makeVerbSetsMaps(Bot bot) {
        getIrregulars();
        IOUtils.lines(IOUtils.rootPath.resolve("data/verb300.txt"))
            .forEachOrdered(allVerbs::add);
        MutableSet be = new MutableSet("be");
        MutableSet is = new MutableSet("is");
        MutableSet was = new MutableSet("was");
        MutableSet been = new MutableSet("been");
        MutableSet being = new MutableSet("being");
        MutableMap is2be = new MutableMap("is2be");
        MutableMap be2is = new MutableMap("be2is");
        MutableMap was2be = new MutableMap("was2be");
        MutableMap be2was = new MutableMap("be2was");
        MutableMap been2be = new MutableMap("been2be");
        MutableMap be2been = new MutableMap("be2been");
        MutableMap be2being = new MutableMap("be2being");
        MutableMap being2be = new MutableMap("being2be");

        for (String verb : allVerbs) {
            String isForm = is(verb);
            String wasForm = was(verb);
            String beenForm = been(verb);
            String beingForm = being(verb);
            logger.info("{},{},{},{},{}", verb, isForm, wasForm, beingForm, beenForm);
            be.add(verb);
            is.add(isForm);
            was.add(wasForm);
            been.add(beenForm);
            being.add(beingForm);
            be2is.put(verb, isForm);
            is2be.put(isForm, verb);
            be2was.put(verb, wasForm);
            was2be.put(wasForm, verb);
            be2been.put(verb, beenForm);
            been2be.put(beenForm, verb);
            be2being.put(verb, beingForm);
            being2be.put(beingForm, verb);

        }
        be.writeSet(bot);
        is.writeSet(bot);
        was.writeSet(bot);
        been.writeSet(bot);
        being.writeSet(bot);
        be2is.writeMap(bot);
        is2be.writeMap(bot);
        be2was.writeMap(bot);
        was2be.writeMap(bot);
        be2been.writeMap(bot);
        been2be.writeMap(bot);
        be2being.writeMap(bot);
        being2be.writeMap(bot);
    }
}
