package com.findr.service.indexer;

import com.findr.object.Webpage;

import java.util.List;

/**
 * This interface describes what an ideal indexer should do: be able to save a page into the index and update all
 * corresponding hashtables (e.g. page->keywords). We'll implement it separately, allowing us to easily swap out/modify
 * this class in the future
 */
public interface Indexer {
    void addWebpageEntry(Webpage webpage);

    void addAllWebpageEntries(List<Webpage> listOfWebpages);

    void deleteWebpageEntry(Webpage webpage);

    void readDBFromDisk();

    void writeInfoToDisk();

    void commitAndClose();

}
