package org.alicebot.ab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SubstitutionList {

    private static final Logger logger = LoggerFactory.getLogger(SubstitutionList.class);
    private static final Pattern linePattern = Pattern.compile("\"(.*?)\",\"(.*?)\"", Pattern.DOTALL);

    private final List<Substitution> substitutions;

    private SubstitutionList(List<Substitution> substitutions) {
        this.substitutions = substitutions;
    }

    public SubstitutionList(Path folder, String filename) {
        this(read(folder.resolve(filename)));
    }

    private static List<Substitution> read(Path path) {
        try {
            return Files.lines(path).map(String::trim)
                .filter(l -> !l.startsWith(MagicStrings.text_comment_mark))
                .map(linePattern::matcher).filter(Matcher::find).limit(MagicNumbers.max_substitutions)
                .map(matcher -> {
                    String quotedPattern = Pattern.quote(matcher.group(1));
                    //logger.debug("quoted pattern={}", quotedPattern);
                    Pattern pattern = Pattern.compile(quotedPattern, Pattern.CASE_INSENSITIVE);
                    return new Substitution(pattern, matcher.group(2));
                }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to read substitutions from {}", path, e);
            return Collections.emptyList();
        }
    }

    public String substitute(String request) {
        String result = " " + request + " ";
        for (Substitution substitution : substitutions) {
            result = substitution.substitute(result);
        }
        while (result.contains("  ")) { result = result.replace("  ", " "); }
        return result.trim();
    }

    public int size() {
        return substitutions.size();
    }

}
