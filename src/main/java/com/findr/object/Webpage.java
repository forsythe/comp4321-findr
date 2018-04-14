package com.findr.object;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

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
    HashMap<String, Integer> titleKeywordsAndFrequencies;
    HashMap<String, Integer> keywordsAndFrequencies;

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

    public HashMap<String, Integer> getKeywordsAndFrequencies() {
        return keywordsAndFrequencies;
    }

    public Webpage setKeywordsAndFrequencies(HashMap<String, Integer> keywordsAndFrequencies) {
        this.keywordsAndFrequencies = keywordsAndFrequencies;
        return this;
    }
    
    public HashMap<String, Integer> getTitleKeywordsAndFrequencies() {
    	return titleKeywordsAndFrequencies;
    }
    
    public Webpage setTitleKeywordsAndFrequencies(HashMap<String, Integer> titleKeywordsAndFrequencies) {
    	this.titleKeywordsAndFrequencies = titleKeywordsAndFrequencies;
    	return this;
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
