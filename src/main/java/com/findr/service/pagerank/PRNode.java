package com.findr.service.pagerank;

/**
 * A node to be used in PageRank that represents a webpage.
 */
public class PRNode {
	String myUrl;
	double currRank;
	double newRank;
	
	public String getMyUrl() {
		return myUrl;
	}
	
	public double getRank() {
		return currRank;
	}
}
