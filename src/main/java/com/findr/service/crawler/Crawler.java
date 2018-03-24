package com.findr.service.crawler;

import com.findr.object.Webpage;

import java.util.List;

/**
 * This interface describes what an ideal crawler should do: given a starting url and a max depth/time constraint,
 * start crawling and return a list of webpages crawled. We'll implement it separately, allowing us to easily swap
 * out/modify this class in the future
 */
public interface Crawler {
    List<Webpage> crawl(String startingUrl, int maxRunSeconds, int maxPagesCrawl);
}
