package com.findr.service.searcher;

import com.findr.object.Webpage;
import com.findr.service.crawler.Crawler;
import com.findr.service.crawler.JSoupMultithreadedCrawler;

import java.util.Collections;
import java.util.List;

/**
 * Hongseo's implementation of the Searcher interface
 */
public class HongseoSearcher implements Searcher {
    //initialize now for thread safety
    private static HongseoSearcher instance = new HongseoSearcher();

    private HongseoSearcher() {
    }

    public static HongseoSearcher getInstance() {
        return instance;
    }

    @Override
    public synchronized List<Webpage> search(List<String> query, int maxResults) {
        //TODO: search from the index, not like this
        Crawler temp = new JSoupMultithreadedCrawler(10);
        return temp.crawl("https://www.nytimes.com/", 20, maxResults);
    }

    @Override
    public synchronized List<List<String>> getRelatedQueries(List<String> query) {
        //TODO
        return Collections.emptyList();
    }

}
