package com.findr.service.searcher;

import com.findr.object.Webpage;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Describes what an ideal searcher should do: given a string of (potentially multiple) queries, returns the best set
 * of Webpage results from the index. We'll implement it separately, allowing us to easily swap out/modify this class
 * in the future
 */
public interface Searcher {
    List<Webpage> search(List<String> query, int maxResults);

    List<List<String>> getRelatedQueries(List<String> query);
    
    Set<String> getKeywords();
}
