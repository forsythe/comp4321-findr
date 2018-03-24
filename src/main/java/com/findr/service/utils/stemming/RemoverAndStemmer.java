package com.findr.service.utils.stemming;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class RemoverAndStemmer {
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
            if (isStopWord(w))
                continue;
            String result = stem(w);
            if (!result.isEmpty())
                answer.add(result);
        }
        return answer;


    }
}
