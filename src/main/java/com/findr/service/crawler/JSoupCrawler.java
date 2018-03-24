package com.findr.service.crawler;

import com.findr.object.Webpage;
import com.findr.service.parser.JSoupParser;
import com.findr.service.parser.Parser;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The JSoup implementation of the Crawler service
 */
public class JSoupCrawler implements Crawler {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JSoupCrawler.class);

    private final int numThreads;
    private final int maxRunSeconds;
    private final int maxPagesCrawl;

    private BlockingQueue<String> crawlQueue;
    private BlockingQueue<Webpage> indexQueue;
    private Set<String> seenURLs;
    private volatile boolean running = true;
    private ReentrantLock lockSeenURLs = new ReentrantLock();

    /**
     * @param numThreads    Number of threads to use for crawling
     * @param maxRunSeconds How many seconds to crawl before trying to stop all threads
     * @param maxPagesCrawl How many pages to crawl before stopping
     */
    public JSoupCrawler(int maxRunSeconds, int maxPagesCrawl, int numThreads) {
        this.numThreads = numThreads;
        this.maxRunSeconds = maxRunSeconds;
        this.maxPagesCrawl = maxPagesCrawl;
        this.indexQueue = new LinkedBlockingDeque<>(maxPagesCrawl);
        this.crawlQueue = new LinkedBlockingDeque<>(maxPagesCrawl);
        this.seenURLs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    @Override
    public List<Webpage> crawl(String startURL) {
        ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        crawlQueue.add(startURL);

        long start = System.currentTimeMillis();
        for (int k = 0; k < numThreads; k++) {
            exec.execute(new CrawlTask());
        }
        exec.shutdown();

        try {
            if (exec.awaitTermination(maxRunSeconds, TimeUnit.SECONDS)) { //blocking
                log.info("Crawled the maximum pages before time ran out!");
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

        @Override
        public void run() {
            Parser parser = new JSoupParser();
            while (running) {
                try {
                    String crawlTarget = crawlQueue.take(); //blocks until there's a page to take
                    log.info("Crawl target is " + crawlTarget);

                    Optional<Webpage> result = parser.parse(crawlTarget, false);
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
                                log.info("{{}} saved", page.getMyUrl());
                            } else {
                                log.info("{{}} seen before, ignoring", page.getMyUrl());
                            }
                        }

                        for (String link : page.getLinks()) {
                            if (!seenURLs.contains(link)) {
                                boolean status = crawlQueue.offer(link); //may fail silently if queue full
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
