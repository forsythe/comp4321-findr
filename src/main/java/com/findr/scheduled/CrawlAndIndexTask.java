package com.findr.scheduled;

import com.findr.service.spider.Spider;
import com.findr.service.spider.SpiderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Crawls and indexes new pages in a scheduled manner
 */
@Component
public class CrawlAndIndexTask {

    private static final Logger log = LoggerFactory.getLogger(CrawlAndIndexTask.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Scheduled(fixedRate = 30000)
    public void reportCurrentTime() {
        log.info("I am scheduled to say hi every 30 seconds. Replace me with some crawling/indexing task, " +
                "and change it to 1 hour or something {}", dateFormat.format(new Date()));
    }
    
    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void crawlAndIndex() {
        Spider spider = new SpiderImpl();
        //spider.run("https://www.nytimes.com/", 50, 50);
        spider.run("http://www.cse.ust.hk", 50, 50);
    }
   
}
