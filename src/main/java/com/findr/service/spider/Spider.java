package com.findr.service.spider;


/**
 * Describes what an ideal spider should do. Crawl and index.
 */
public interface Spider {
    void run(String startUrl, int maxPages, int maxRunSeconds);
}
