package com.findr.service.searcher;

import com.findr.object.Webpage;
import com.findr.service.crawler.Crawler;
import com.findr.service.crawler.JSoupCrawler;
import com.findr.service.parser.JSoupParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Hongseo's implementation of the Searcher interface
 */
public class HongseoSearcher implements Searcher {
    @Override
    public List<Webpage> search(List<String> query, int maxResults) {
        //TODO: search from the index, not like this
        Crawler temp = new JSoupCrawler(20, 20, 10);
        List<Webpage> tempResults = temp.crawl("https://www.nytimes.com/");
        return tempResults;
    }

    @Override
    public List<List<String>> getRelatedQueries(List<String> query) {
        //TODO
        return Collections.emptyList();
    }
}
