package com.findr.service.pagerank;

import com.findr.object.Webpage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

/**
 * A class that ranks webpages according to their links with the PageRank algorithm.
 */
public class PageRank {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(PageRank.class);
    
    /**
     * @param webpages List of webpages to rank
     * @param dampingFactor Damping factor of PageRank
     * @param numIter Number of iterations
     * @return List of webpage URLs with their ranks
     */
    public static List<PRNode> pagerank(List<Webpage> webpages, double dampingFactor, int numIter) {
    	log.info("Executing PageRank");
    	log.info("==================");
    	
    	if (dampingFactor < 0.0 || dampingFactor > 1.0) {
    		dampingFactor = 0.5;
    		log.error("Damping factor not in range [0.0,1.0]. Setting to {}.", dampingFactor);
    	}
    	
    	if (numIter < 0) {
    		numIter = 0;
    		log.error("[PageRank] Number of iterations not in range [0, inf). Setting to {} iterations.", numIter);
    	}
    	
    	// Initialise graph.
    	log.info("Initialising graph");
    	Map<String, PRNode> nodes = new HashMap<String, PRNode>();
    	Map<PRNode, List<PRNode>> edges = new HashMap<PRNode, List<PRNode>>();
    	
    	// Construct all PRNodes.
    	// This includes all webpages and their children.
    	log.info("Constructing nodes");
    	for (Webpage w : webpages) {
    		PRNode n = new PRNode();
    		n.myUrl = w.getMyUrl();
    		n.currRank = 1.0;
    		n.newRank = 1 - dampingFactor;
    		nodes.put(n.myUrl, n);
    		edges.put(n, new ArrayList<PRNode>());
    		
    		for (String e : w.getChildren()) {
    			if (!nodes.containsKey(e)) {
	        		PRNode en = new PRNode();
	        		en.myUrl = e;
	        		en.currRank = 1.0;
	        		en.newRank = 1 - dampingFactor;
	        		nodes.put(en.myUrl, en);
	        		edges.put(en, new ArrayList<PRNode>());
    			}
    		}
    	}
    	
    	// Construct all edges.
    	log.info("Constructing edges");
    	for (Webpage w : webpages) {
    		PRNode currNode = nodes.get(w.getMyUrl());
    		for (String e : w.getChildren()) {
    			PRNode edgeNode = nodes.get(e);
    			edges.get(currNode).add(edgeNode);
    		}
    	}
    	
    	// Iterative step
    	log.info("Calculating ranks");
    	for (int i = 0; i < numIter; i++) {   		
    		for (PRNode currNode : edges.keySet()) {
    			List<PRNode> destNodes = edges.get(currNode);
    			
    			int size = destNodes.size();
    			for (PRNode destNode : destNodes) {
    				destNode.newRank += dampingFactor*currNode.currRank/size;
    			}
    		}
    		
    		for (PRNode currPRNode : edges.keySet()) {
    			currPRNode.currRank = currPRNode.newRank;
    			currPRNode.newRank = 1 - dampingFactor;
    		}
    	}
    	
    	// Transform map to list.
    	List<PRNode> result = new ArrayList<PRNode>();
    	for (PRNode n : nodes.values()) {
    		result.add(n);
    		log.info("Rank of {}: {}", n.myUrl, n.currRank);
    	}

    	return result;
    }
}
