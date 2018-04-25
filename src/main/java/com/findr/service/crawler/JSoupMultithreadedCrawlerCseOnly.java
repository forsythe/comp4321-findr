package com.findr.service.crawler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.LoggerFactory;

import com.findr.object.Webpage;
import com.findr.service.crawler.JSoupMultithreadedCrawlerCseOnly.CrawlTask;
import com.findr.service.parser.JSoupParser;
import com.findr.service.parser.Parser;

public class JSoupMultithreadedCrawlerCseOnly implements Crawler {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JSoupMultithreadedCrawlerCseOnly.class);

    private final int numThreads;

    private BlockingQueue<String> crawlQueue;
    private BlockingQueue<Webpage> indexQueue;
    private Set<String> seenURLs;
    private volatile boolean running = true;
    private ReentrantLock lockSeenURLs = new ReentrantLock();

    /**
     * @param numThreads Number of threads to use for crawling
     */
    public JSoupMultithreadedCrawlerCseOnly(int numThreads) {
        this.numThreads = numThreads;
        this.seenURLs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    @Override
    public List<Webpage> crawl(String startURL, int maxRunSeconds, int maxPagesCrawl) {
        this.indexQueue = new LinkedBlockingDeque<>(maxPagesCrawl);
        this.crawlQueue = new LinkedBlockingDeque<>(maxPagesCrawl);


        ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        crawlQueue.add(startURL);

        long start = System.currentTimeMillis();
        for (int k = 0; k < numThreads; k++) {
            exec.execute(new CrawlTask(maxPagesCrawl));
        }
        exec.shutdown();

        try {
            if (exec.awaitTermination(maxRunSeconds, TimeUnit.SECONDS)) { //blocking
                //log.info("Crawled the maximum pages before time ran out!");
            } else {
                exec.shutdownNow();
                running = false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (!exec.isTerminated()) ; //spinlock
        long elapsedTimeMillis = System.currentTimeMillis() - start;
        float elapsedTimeSec = elapsedTimeMillis / 1000F;
        log.info("Crawl completed in {} seconds", elapsedTimeSec);

        return new ArrayList<>(indexQueue);
    }

    class CrawlTask implements Runnable {
        int maxPagesCrawl;

        public CrawlTask(int maxPagesCrawl) {
            this.maxPagesCrawl = maxPagesCrawl;
        }

        @Override
        public void run() {
            Parser parser = new JSoupParser();
            while (running) {
                try {
                    String crawlTarget = crawlQueue.take(); //blocks until there's a page to take
                    log.info("Crawl target is " + crawlTarget);

                    Optional<Webpage> result = parser.parse(crawlTarget, false, true);
                    if (!result.isPresent())
                        continue;

                    Webpage page = result.get();
                    log.info("Crawled " + page.getMyUrl());

                    lockSeenURLs.lockInterruptibly();
                    try {
                        if (seenURLs.size() >= maxPagesCrawl) {
                            running = false;
                                  log.info("max seen pages reached, killing this thread");
                            return;
                        } else {
                            if (seenURLs.add(page.getMyUrl())) {
                                indexQueue.put(page); //blocks if full. Indexer threads NEED to actively remove them!
                                System.out.println("Page in Crawler: " + page.getMyUrl());
                                log.info("{{}} saved", page.getMyUrl());
                            } else {
                                      log.info("{{}} seen before, ignoring", page.getMyUrl());
                            }
                        }

                        for (String link : page.getChildren()) {
                            System.out.println("-- child: " + link);
                            if (!seenURLs.contains(link)) {
                            	boolean status = false;
                            	if (link.contains("cse.ust.hk"))
                            		status = crawlQueue.offer(link); //may fail silently if queue full
                                   log.info("Insert {{}} into crawlQueue was {{}}", link, status);
                            } else {
                                    log.info("already saw {{}}, skip", link);
                            }
                        }
                    } finally {
                        lockSeenURLs.unlock();
                    }

                } catch (InterruptedException e) {
                        log.info("INTERRUPTED");
                    return;
                }
            }
        }
    }
}
