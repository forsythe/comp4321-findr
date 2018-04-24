package com.findr.object;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;

import javax.swing.plaf.BorderUIResource.TitledBorderUIResource;

/**
 * Represents a webpage result that we crawled
 */
public class Webpage {
    String title, body, myUrl, parentUrl, metaDescription;
    long size;
    Date lastModified;
    double score;

    Collection<String> links;
    LinkedHashMap<String, Integer> titleKeywordsAndFrequencies;
    LinkedHashMap<String, Integer> keywordsAndFrequencies;

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

    public String getParentUrl() {
        return parentUrl;
    }

    public Webpage setParentUrl(String parentUrl) {
        this.parentUrl = parentUrl;
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

    public Collection<String> getLinks() {
        return links;
    }

    public Webpage setLinks(Collection<String> links) {
        this.links = links;
        return this;
    }

    private Webpage() {
    }

    public static Webpage create() {
        return new Webpage();
    }
}
