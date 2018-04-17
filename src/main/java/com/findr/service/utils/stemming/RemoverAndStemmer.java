package com.findr.service.utils.stemming;

import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class RemoverAndStemmer {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(RemoverAndStemmer.class);

    private final Porter porter;
    private final HashSet<String> stopWords; //threadsafe if we never add/remove elements outside of constructor


    public RemoverAndStemmer(String str) {
        porter = new Porter();
        stopWords = new HashSet<>();
        /*reading the stop words list from file(str)*/
        try {
            FileReader freader = new FileReader(str);
            BufferedReader bfreader = new BufferedReader(freader);
            String word;
            while ((word = bfreader.readLine()) != null) {
                stopWords.add(word);
            }
            stopWords.add("");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean isStopWord(String str) {
        return stopWords.contains(str);
    }


    private String stem(String str) {
        return porter.stripAffixes(str);
    }


    public List<String> stopAndStem(String[] words) {
        List<String> answer = new ArrayList<>();

        for (String w : words) {
            w = w.toLowerCase();
            if (isStopWord(w)) {
                log.debug("ignoring encountered stopword: " + w);
                continue;
            }
            String result = stem(w);
            if (!result.isEmpty()) {
                log.debug("saving stemmed word: " + result + ", which previously was " + w);
                answer.add(result);
            }
        }
        return answer;


    }
}
