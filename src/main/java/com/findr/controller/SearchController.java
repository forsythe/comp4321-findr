package com.findr.controller;

import com.findr.object.Webpage;
import com.findr.service.searcher.HongseoSearcher;
import com.findr.service.searcher.Searcher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Responsible for showing the search queries page. Each user gets their own.
 */
@Controller
@Scope("session")
public class SearchController {

    private static final int RESULTS_PER_PAGE = 10;
    private List<Webpage> results = new ArrayList<>();
    private String prevQuery = "";

    private Searcher searcher = HongseoSearcher.getInstance();

    /**
     * Handles user query requests
     *
     * @param query A string containing (potentially multiple, space separated) query terms
     * @param page  The page number (e.g. on google, you can navigate between page 1, 2, etc). May potentially be invalid
     *              if user messes with the url, so need to check for safety.
     * @param map   The map object we can add attributes to and access later in the html using curly braces
     * @return The name of the html page to display
     */
    @RequestMapping
    public String handleQueryRequest(
            @RequestParam("query") String query,
            @RequestParam("page") String page,
            Model map) {
        int pageNum;
        try {
            pageNum = Integer.parseInt(page);
            if (pageNum < 1)
                throw new IllegalArgumentException();
        } catch (Exception e) {
            pageNum = 1;
        }
        map.addAttribute("prevQuery", prevQuery);

        //TODO: access db in a thread safe manner
        if (!prevQuery.equals(query)) {
            results.clear();
//            for (int k = 0; k < 35; k++)
//                results.add("This is (" + query + ") result #" + k);
            List<String> tempQueryHolder = new ArrayList<>();
            tempQueryHolder.add(query);
            results.addAll(searcher.search(tempQueryHolder, 12));
            prevQuery = query;
        }

        map.addAttribute("results", paginate(results, pageNum, RESULTS_PER_PAGE));
        int numResultPages = (int) Math.max(1, Math.ceil((double) results.size() / RESULTS_PER_PAGE));
        pageNum = Math.min(numResultPages, pageNum);

        map.addAttribute("numResultPages", numResultPages);
        map.addAttribute("totalCrawledPages", results.size());
        map.addAttribute("crawlTime", 5); //TODO: time it properly
        map.addAttribute("query", query.trim());
        map.addAttribute("pageNum", pageNum);

        return "search";
    }

    /**
     * A private helper function to split up a big list of results into several "pages"
     *
     * @param sourceList The original list of items
     * @param page       Which "page" of the items you want (e.g. google's page 1, page 2, etc)
     * @param pageSize   How many results to display per page
     * @param <T>        The type of object
     * @return A subset of the original list of items, corresponding to the "page" specified
     */
    private static <T> List<T> paginate(List<T> sourceList, int page, int pageSize) {
        if (pageSize <= 0 || page <= 0) {
            throw new IllegalArgumentException("invalid page size: " + pageSize);
        }

        int fromIndex = (page - 1) * pageSize;
        if (sourceList == null || sourceList.size() < fromIndex) {
            return Collections.emptyList();
        }

        // toIndex exclusive
        return sourceList.subList(fromIndex, Math.min(fromIndex + pageSize, sourceList.size()));
    }
}
