package com.findr.service.utils.stemming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.split;

/**
 * A class responsible for transforming raw text into vectors, and performing math on those vectors.
 */
public class Vectorizer {

    private static final RemoverAndStemmer ras = new RemoverAndStemmer("src/main/resources/stopwords.txt");

    /**
     * @param text Raw text to vectorize
     * @return A Hashmap of words and their frequencies
     */
    public static LinkedHashMap<String, Integer> vectorize(String text, boolean doStoppingAndStemming) {
        LinkedHashMap<String, Integer> count = new LinkedHashMap<>();
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