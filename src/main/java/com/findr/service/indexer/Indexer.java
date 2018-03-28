 	package com.findr.service.indexer;

import java.util.List;

import com.findr.object.Webpage;

/**
 * This interface describes what an ideal indexer should do: be able to save a page into the index and update all
 * corresponding hashtables (e.g. page->keywords). We'll implement it separately, allowing us to easily swap out/modify
 * this class in the future
 */
public interface Indexer {  
    void addAllWebpageEntries(List<Webpage> listOfWebpages);
    
    Webpage getWebpage(Long id);

    void readDBFromDisk();

    void commitAndClose();

}
