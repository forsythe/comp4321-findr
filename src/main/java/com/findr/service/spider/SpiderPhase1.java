package com.findr.service.spider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.findr.object.Webpage;
import com.findr.service.crawler.Crawler;
import com.findr.service.crawler.JSoupMultithreadedCrawler;
import com.findr.service.indexer.Indexer;
import com.findr.service.indexer.MapDBIndexer;

public class SpiderPhase1 implements Spider {

    @Override
    public void run(String startUrl, int maxPages, int maxRunSeconds) {
        int numCrawlerThreads = 10;

        Crawler crawler = new JSoupMultithreadedCrawler(numCrawlerThreads);
        ((JSoupMultithreadedCrawler) crawler).setRequiredSubdomain("cse.ust.hk");

        List<Webpage> crawledPages = crawler.crawl(startUrl, maxRunSeconds, maxPages);

//        for (Webpage wp : crawledPages) {
//        	System.out.println(wp.getTitle());
//        	System.out.println(wp.getLastModified().toString());
//        	
//        }

        Indexer indexer = new MapDBIndexer();
        indexer.readDBFromDisk();
        indexer.addAllWebpageEntries(crawledPages);
        /*
        try {
            File output = new File("spider_result.txt");
            BufferedWriter bw = new BufferedWriter(new FileWriter(output));

            long pageID = 0;
            int count = 30;
            while (count > 0) {
                Webpage p = indexer.getWebpage(Long.valueOf(pageID));
                if (p == null) {
                    pageID++;
                    continue;
                }
                bw.write(p.getTitle());
                bw.newLine();
                bw.write(p.getMyUrl());
                bw.newLine();
                bw.write(p.getLastModified().toString() + ", " + p.getSize() + " bytes");
                bw.newLine();
                HashMap<String, Integer> keywordFreq = p.getKeywordsAndFrequencies();
                for (String keyword : keywordFreq.keySet()) {
                    bw.write(keyword + " " + keywordFreq.get(keyword).toString() + "; ");
                }
                bw.newLine();

                Collection<String> childLinks = p.getChildren();
                for (String link : childLinks) {
                    bw.write(link);
                    bw.newLine();
                }
                bw.write("-------------------------------------------------------------------------------------------");
                bw.newLine();
                pageID++;
                count--;
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
		*/
        indexer.commitAndClose();
    }

}
