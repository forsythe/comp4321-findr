package com.findr.controller;

import com.findr.object.Webpage;
import com.findr.service.searcher.MultithreadedSearcher;
import com.findr.service.searcher.Searcher;
import com.findr.service.searcher.WolframSearcher;
import com.findr.service.utils.timer.Timer;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Responsible for showing the search queries page. Each user gets their own.
 */
@Controller
@Scope("session")
public class SearchController {
    private static final int RESULTS_PER_PAGE = 50;
    private static final int MAX_RESULTS = 50;
    private List<Webpage> results = new ArrayList<>();
    private String prevQuery = "";

    private Searcher searcher = MultithreadedSearcher.getInstance();

    private static final int QUERY_HISTORY_SIZE = 5;
    private final CircularFifoQueue<String> queryHistory = new CircularFifoQueue<>(QUERY_HISTORY_SIZE);
    private Set<String> sortedKeywords = new TreeSet<>();


    /**
     * Handles user query requests
     *
     * @param query A string containing (potentially multiple, space separated) query terms
     * @param page  The page number (e.g. on google, you can navigate between page 1, 2, etc). May potentially be invalid
     *              if user messes with the url, so need to check for safety.
     * @param map   The map object we can add attributes to and access later in the html using curly braces
     * @return The name of the html page to display
     */
    @RequestMapping(value = {"/search"}, method = RequestMethod.GET, params = {"query", "page"})
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
        
        query = query.trim();
        
        //TODO: access db in a thread safe manner
        Double crawlTime = 0.0;
        if (!prevQuery.equals(query)) {
            results.clear();
//            for (int k = 0; k < 35; k++)
//                results.add("This is (" + query + ") result #" + k);
            List<String> tempQueryHolder = new ArrayList<>();
            tempQueryHolder.add(query);

            crawlTime = Timer.measure(() -> results.addAll(searcher.search(tempQueryHolder, MAX_RESULTS)));
            prevQuery = query;
            queryHistory.add(prevQuery);
        }

        map.addAttribute("results", paginate(results, pageNum, RESULTS_PER_PAGE));
        int numResultPages = (int) Math.max(1, Math.ceil((double) results.size() / RESULTS_PER_PAGE));
        pageNum = Math.min(numResultPages, pageNum);

        map.addAttribute("crawlTime", String.format("%.2f", crawlTime));
        map.addAttribute("numResultPages", numResultPages);
        map.addAttribute("totalCrawledPages", results.size());

        map.addAttribute("pageNum", pageNum);
        map.addAttribute("query", query.trim());
        map.addAttribute("isMorning", HomeController.dayOrNight());

        map.addAttribute("similarPages", "");
        map.addAttribute("queryHistory", queryHistory);

        return "search";
    }
    
    
    @RequestMapping(value = {"/search"}, method = RequestMethod.GET, params = {"query", "similarPages", "page"})
    public String handleSimilarQueryRequest(
            @RequestParam("query") String query,
            @RequestParam("similarPages") String similarPages,
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
        
        query = query.trim();

        String[] querySplit = query.split(" ");
        LinkedHashSet<String> querySet = new LinkedHashSet<String>();
        for (String s : querySplit) {
        	querySet.add(s.trim());
        }
        
        String[] similarPagesSplit = similarPages.split(" ");
        LinkedHashSet<String> similarPagesSet = new LinkedHashSet<String>();
        for (String s : similarPagesSplit) {
        	similarPagesSet.add(s.trim());
        }
        
        LinkedHashSet<String> noUseSet = new LinkedHashSet<String>(querySet);
        noUseSet.retainAll(similarPagesSet);
        
        for (String s : similarPagesSet) {
        	if (!noUseSet.contains(s)) {
        		query += " " + s.trim();
        	}
        }
        query.trim();
        
        //TODO: access db in a thread safe manner
        Double crawlTime = 0.0;
        if (!prevQuery.equals(query)) {
            results.clear();
//            for (int k = 0; k < 35; k++)
//                results.add("This is (" + query + ") result #" + k);
            List<String> tempQueryHolder = new ArrayList<>();
            tempQueryHolder.add(query);

            crawlTime = Timer.measure(() -> results.addAll(searcher.search(tempQueryHolder, MAX_RESULTS)));
            prevQuery = query;
            queryHistory.add(prevQuery);
        }

        map.addAttribute("results", paginate(results, pageNum, RESULTS_PER_PAGE));
        int numResultPages = (int) Math.max(1, Math.ceil((double) results.size() / RESULTS_PER_PAGE));
        pageNum = Math.min(numResultPages, pageNum);

        map.addAttribute("crawlTime", String.format("%.2f", crawlTime));
        map.addAttribute("numResultPages", numResultPages);
        map.addAttribute("totalCrawledPages", results.size());

        map.addAttribute("pageNum", pageNum);
        map.addAttribute("query", query.trim());
        map.addAttribute("isMorning", HomeController.dayOrNight());
        map.addAttribute("similarPages", similarPages);

        map.addAttribute("queryHistory", queryHistory);

        return "search";
    }
    
    @RequestMapping(value = {"/keywords"}, method = RequestMethod.GET)
    public String listKeywords(Model model) {
    	
    	if (sortedKeywords.isEmpty())
    		sortedKeywords = searcher.getKeywords();
    	
    	model.addAttribute("wordSet", sortedKeywords);
    	model.addAttribute("isMorning",HomeController.dayOrNight());
    	return "keywords";
    }
    
    @RequestMapping("/wolframResult")
    public SseEmitter getWolframResult() {
    	String query = prevQuery;
        final SseEmitter sseemitter = new SseEmitter();
    	WolframSearcher wsearch = new WolframSearcher();
    	wsearch.search(query);
		String output = wsearch.outputHTML();
		try {
			if (output != null)
				sseemitter.send(output, MediaType.TEXT_HTML);
			else {
				sseemitter.send("", MediaType.TEXT_PLAIN);
			}
		} catch (IOException e) {
			e.printStackTrace();
			sseemitter.completeWithError(e);
		}
		sseemitter.complete();
		return sseemitter;
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
