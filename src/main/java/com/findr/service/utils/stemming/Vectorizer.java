package com.findr.service.stemming;


import com.findr.service.stemming.utils.RemoverAndStemmer;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.split;

/**
 * A class responsible for transforming raw text into vectors, and performing math on those vectors.
 */
public class Vectorizer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Vectorizer.class);
    private static final RemoverAndStemmer ras = new RemoverAndStemmer("src/main/resources/stopwords.txt");

    /**
     * @param text Raw text to vectorize
     * @return A Hashmap of words and their frequencies
     */
    public static HashMap<String, Integer> vectorize(String text, boolean doStoppingAndStemming) {

        HashMap<String, Integer> count = new HashMap<>();
        if (text.isEmpty()) return count;

        String[] words = split(text);
        List<String> processedWords = new ArrayList<>();

        if (doStoppingAndStemming) {
            processedWords = ras.stopAndStem(words);
        }

        for (String token : processedWords) {
            if (count.containsKey(token)) {
                count.put(token, 1 + count.get(token));
            } else {
                count.put(token, 1);
            }
        }
        return count;
    }
}