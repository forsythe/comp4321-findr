package com.findr.service.spider;

import com.findr.object.Webpage;
import com.findr.service.crawler.Crawler;
import com.findr.service.crawler.JSoupCrawler;
import com.findr.service.indexer.Indexer;
import com.findr.service.indexer.MapDBIndexer;

import java.util.List;

/**
 * Implementation of the spider interface. Combines the crawling and indexing jobs
 */
public class SpiderImpl implements Spider {
    @Override
    public void run(String startUrl, int maxPages, int maxRunSeconds) {
        int numCrawlerThreads = 10;
        Crawler crawler = new JSoupCrawler(maxPages, maxRunSeconds, numCrawlerThreads);
        List<Webpage> crawledPages = crawler.crawl(startUrl);

        Indexer hongseo = new MapDBIndexer();
        //TODO idk what hongseo does, i assume this is correct? -heng
        hongseo.readDBFromDisk();
        crawledPages.forEach(hongseo::addWebpageEntry);
        hongseo.writeInfoToDisk();
        hongseo.commitAndClose();
    }
}
