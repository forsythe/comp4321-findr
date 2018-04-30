package com.findr.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;

/**
 * Represents a webpage result that we crawled
 */
public class Webpage {
    private String title, body, myUrl, metaDescription;
    private long size;
    private Date lastModified;
    private Double score;

    private Collection<String> children;
    private Collection<String> parents = new ArrayList<>();


    LinkedHashMap<String, Integer> titleKeywordsAndFrequencies;
    LinkedHashMap<String, Integer> keywordsAndFrequencies;
    ArrayList<ArrayList<String>> triples;

    public String getTitle() {
        return title;
    }

    public Webpage setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getBody() {
        return body;
    }

    public Webpage setBody(String body) {
        this.body = body;
        return this;
    }

    /**
     * @return the URL of the page. Does not have any query terms, just the base URL
     */
    public String getMyUrl() {
        return myUrl;
    }

    public Webpage setMyUrl(String myUrl) {
        this.myUrl = myUrl;
        return this;

    }


    public String getMetaDescription() {
        return metaDescription;
    }

    public Webpage setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
        return this;
    }

    public long getSize() {
        return size;
    }

    public Webpage setSize(long size) {
        this.size = size;
        return this;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public Webpage setLastModified(Date lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    public LinkedHashMap<String, Integer> getKeywordsAndFrequencies() {
        return keywordsAndFrequencies;
    }

    public Webpage setKeywordsAndFrequencies(LinkedHashMap<String, Integer> keywordsAndFrequencies) {
        this.keywordsAndFrequencies = keywordsAndFrequencies;
        return this;
    }
    
    public LinkedHashMap<String, Integer> getTitleKeywordsAndFrequencies() {
    	return titleKeywordsAndFrequencies;
    }
    
    public Webpage setTitleKeywordsAndFrequencies(LinkedHashMap<String, Integer> titleKeywordsAndFrequencies) {
    	this.titleKeywordsAndFrequencies = titleKeywordsAndFrequencies;
    	return this;
    }
    
    public ArrayList<ArrayList<String>> getTriples() {
    	return triples;
    }
    
    public Webpage setTriples(ArrayList<ArrayList<String>> triples) {
    	this.triples = triples;
    	return this;
    }

    public LinkedHashMap<String, Integer> getTopNKeywords(int n) {
        LinkedHashMap<String, Integer> topN = new LinkedHashMap<>();

        if (keywordsAndFrequencies != null) {
            keywordsAndFrequencies.entrySet()
                    .stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(n)
                    .forEach(e -> topN.put(e.getKey(), e.getValue()));
            return topN;
        } else {
            return new LinkedHashMap<>();
        }
    }

    public Collection<String> getChildren() {
        return children;
    }

    public Webpage setChildren(Collection<String> children) {
        this.children = children;
        return this;
    }
    
    public Double getScore() {
        return score;
    }

    public Webpage setScore(Double score) {
        this.score = score;
        return this;
    }

    private Webpage() {
    }

    public static Webpage create() {
        return new Webpage();
    }

    public Collection<String> getParents() {
        return parents;
    }
    
    public Webpage setParents(Collection<String> parents) {
    	this.parents = parents;
    	return this;
    }

}
