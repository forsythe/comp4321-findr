package com.findr.service.indexer;

import com.findr.object.Posting;
import com.findr.object.Webpage;
import com.findr.service.pagerank.PRNode;
import com.findr.service.pagerank.PageRank;
import org.mapdb.*;
import org.mapdb.serializer.SerializerArrayTuple;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An implementation of the Indexer service which uses mapdb
 */
public class MapDBIndexer implements Indexer {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(MapDBIndexer.class);
	
	private DB db;
	
	//for mapping keyword(String) <=> wordID(Long)  
    private HTreeMap<String, Long> keyword_wordID; //keyword to wordID table
    private HTreeMap<Long, String> wordID_keyword; //wordID to keyword table
    //for mapping Page URL(String) <=> pageID(Long)
    private HTreeMap<Long, String> pageID_url; //pageID to the page's URL table
    private HTreeMap<String, Long> url_pageID;//page's URL to its pageID table
    //for indexing all the other page attributes 
    private HTreeMap<Long, String> pageID_title; //pageID -> page title
    private HTreeMap<Long, Long> pageID_size; //pageID -> page size
    private HTreeMap<Long, Date> pageID_lastmodified;//pageID -> page's last modified date
    private HTreeMap<Long, String> pageID_metaD; //pageID -> meta description (used in search result page)
    //pageID to the tfmax in the page that corresponds to the pageID, used for cosSim calculation 
    private HTreeMap<Long, Integer> pageID_tfmax;
    
    // pageID to the PageRank of the page that corresponds to the pageID
    private HTreeMap<Long, Double> pageID_pagerank;
    
    //inverted index - uses NavigableSet of Object array to achieve a MultiMap
    //each array would contain {wordID, Posting(pageID, frequency)}, linking word to the page it appears on
    // + the frequency that it appears on that page
    private NavigableSet<Object[]> content_inverted;
    
    //forward index - uses NavigableSet of Object array to achieve a MultiMap
    //each array would contain {pageID, Posting(wordID, frequency)}, linking page to the keywords it contains
    // + the frequency of that keyword
    private NavigableSet<Object[]> content_forward;
    
    //for title keywords indexing
    private NavigableSet<Object[]> title_inverted;
    private NavigableSet<Object[]> title_forward;
    
    //Parent->Child table, also with a MultiMap with NavigableMap
    //each entry would be {parent pageID, child pageID}
    private NavigableSet<Object[]> parent_child;
    
    //current pageID to be used, initialized to 0 first
	private long pageID = 0;
	private long wordID = 0;
	
	//used when loading the DB file
	//when the DB file from previous indexing session exists, becomes "true"
	
	public MapDBIndexer() {}
	
	//readDBFromDisk() must be called first to load the DB before anything
	@Override
    public void readDBFromDisk() {
		db = DBMaker.fileDB("index.db")
					.fileChannelEnable()
					.fileMmapEnable()
					.fileMmapEnableIfSupported()
					.closeOnJvmShutdown()
					.make();

		pageID_url = db.hashMap("pageID_url")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.STRING)
        		.counterEnable()
        		.createOrOpen();
        
        url_pageID = db.hashMap("url_pageID")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.LONG)
                .createOrOpen();
        
        wordID_keyword = db.hashMap("wordID_keyword")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.STRING)
        		.counterEnable()
        		.createOrOpen();
        
        keyword_wordID = db.hashMap("keyword_wordID")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.LONG)
                .createOrOpen();
        
        pageID_title = db.hashMap("pageID_title")
                .keySerializer(Serializer.LONG)
                .valueSerializer(Serializer.STRING)
                .createOrOpen();
        
        pageID_size = db.hashMap("pageID_size")
                .keySerializer(Serializer.LONG)
                .valueSerializer(Serializer.LONG)
                .createOrOpen();
        
        pageID_lastmodified = db.hashMap("pageID_lastmodified")
                .keySerializer(Serializer.LONG)
                .valueSerializer(Serializer.DATE)
                .createOrOpen();
        
        pageID_metaD = db.hashMap("pageID_metaD")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.STRING)
        		.createOrOpen();
        
        pageID_tfmax = db.hashMap("pageID_tfmax")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.INTEGER)
        		.createOrOpen();
        
        pageID_pagerank = db.hashMap("pageID_pagerank")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.DOUBLE)
        		.createOrOpen();
        
        //multi-map
        content_inverted = db.treeSet("content_inverted")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG, Serializer.INTEGER))
        		.createOrOpen();
        //multi-map   
        content_forward = db.treeSet("content_forward")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG, Serializer.INTEGER))
        		.createOrOpen();
        
        //multi-map
        title_inverted = db.treeSet("title_inverted")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG, Serializer.INTEGER))
        		.createOrOpen();
        
        //multi-map
        title_forward = db.treeSet("title_forward")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG, Serializer.INTEGER))
        		.createOrOpen();
        
        //multi-map
        parent_child = db.treeSet("parent_child")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG))
        		.createOrOpen();
        
        pageID = pageID_url.sizeLong();
        wordID = wordID_keyword.sizeLong();
    }
		
	//addWebpageEntry() adds a single page to the index
    private void addWebpageEntry(Webpage webpage) {
    	try { //try...catch for any DBException (e.g. DB is closed and a page is about to be added)
    		//"skip" indicates whether this webpage should be skipped (not indexed) or not 
			boolean skip = false;
			Long pID = pageID;

			//With the page URL, check if this entry was indexed before
			if (url_pageID.containsKey(webpage.getMyUrl())) { 
				//if it is found in the database, set the page ID of current page to be indexed (pID) to the matched one's page ID
				pID = url_pageID.get(webpage.getMyUrl());
				
				if (pageID_lastmodified.containsKey(pID)) {
					//Compare the lastmodified date of the current webpage to be indexed to that of the already indexed page
					if (pageID_lastmodified.get(pID).compareTo(webpage.getLastModified()) >= 0)
						skip = true; //the one in the index has the same or newer date than the current web page -> skip this webpage
					else {
						//if the current webpage is newer, delete the old entry and renew the indices with the current webpage information
						deleteWebpageEntry(webpage);
					}
				}
			}
			else {
				//if it is not found, just increment the pageID to prepare for the next webpage
				pageID++;
			}	
			
			if (!skip) {
				//index all the necessary page information
				//if this is an update to a previous webpage entry, pID (ID of the page) is set to that of the preivous webpage in the previous step
				System.out.println("Page ID:" + pID.toString());
				
				pageID_url.put(pID, webpage.getMyUrl());
				url_pageID.put(webpage.getMyUrl(), pID);
				pageID_title.put(pID, webpage.getTitle());
				pageID_size.put(pID, webpage.getSize());
				pageID_lastmodified.put(pID, webpage.getLastModified());
				pageID_metaD.put(pID, webpage.getMetaDescription());
				
				//get all keywords and frequency
				HashMap<String, Integer> keywordFreq = webpage.getKeywordsAndFrequencies();
				for (String keyword : keywordFreq.keySet()) { //for each keyword in the retrieved set,
					Long wID = new Long(wordID); //give a new word ID
					//check the keyword->wordID table to check if this keyword was indexed before
					if (keyword_wordID.containsKey(keyword)) {
						wID = keyword_wordID.get(keyword); //set wID (ID for the current keyword) to the matched ID
					}
					else {
						//if the keyword is a completely new keyword, then put it into the keyword->wordID and wordID->keyword table
						keyword_wordID.put(keyword, wID);
						wordID_keyword.put(wID, keyword);
						wordID++; //increment wordID to prepare for the next keyword
					}
					Integer freq = keywordFreq.get(keyword);
					content_inverted.add(new Object[] {wID, pID, freq}); //add to Inverted Index 
					content_forward.add(new Object[] {pID, wID, freq}); //add to the Forward Index
					
					//update the page->tfmax table 
					if (!pageID_tfmax.containsKey(pID)) //if there is no entry for the given page
						pageID_tfmax.put(pID, freq); //put this keyword frequency as tfmax (max value since there was nothing before)
					else if (pageID_tfmax.get(pID) < freq) //if there is an entry, then compare the current keyword frequency to the current tfmax 
						pageID_tfmax.put(pID, freq); //update it only if the current one is	larger in value than the one in the table
				}	
				
				//get children page URLs
				Collection<String> childLinks = webpage.getLinks();
				for (String link : childLinks) {
					
					System.out.println(link);
					
					Long childID = pageID; //give the child page a new pageID
					if (url_pageID.containsKey(link)) //check if this URL (webpage) was already given a pageID before
						childID = url_pageID.get(link); //if yes, then change the ID of this child page to that ID 
					else {
						//otherwise, update the page (URL)->pageID, pageID->page(URL) tables
						url_pageID.put(link, childID);
						pageID_url.put(childID, link);
						pageID++; //increment pageID for next page 
					}
					parent_child.add(new Object[] {pID, childID});
				}
				
				//Handle the title keywords in the same way as the normal inverted/forward indices
				HashMap<String, Integer> titleKeywords = webpage.getTitleKeywordsAndFrequencies();
				for (String tKeyword : titleKeywords.keySet()) {
					Long wID = wordID;
					if (keyword_wordID.containsKey(tKeyword)) {
						wID = keyword_wordID.get(tKeyword);
					}
					else {
						keyword_wordID.put(tKeyword, wID);
						wordID_keyword.put(wID, tKeyword);
						wordID++;
					}
					Integer freq = titleKeywords.get(tKeyword);
					title_inverted.add(new Object[] {wID, pID, freq});
					title_forward.add(new Object[] {pID, wID, freq});
				}
				
				System.out.println("-----------------------------");
			}
    	} catch (DBException e) {
    		e.printStackTrace();
    	}

    }

    //simply does addWebpageEntry() for each webpage, then calculates PageRank for each webpage
    @Override
    public void addAllWebpageEntries(List<Webpage> listOfWebpages) {
    	synchronized (MapDBIndexer.class) {
	    	for (Webpage webpage : listOfWebpages)
	    		addWebpageEntry(webpage);
	    	
	    	log.info("[MapDBIndexer] Executing PageRank after indexing all webpages");
	    	List<PRNode> pagerankResult = PageRank.pagerank(listOfWebpages, 0.85, 100);
	    	for (PRNode node : pagerankResult) {
	    		Long pID = url_pageID.get(node.getMyUrl());
	    		pageID_pagerank.put(pID, node.getRank());
	    	}
    	}
    }
    
    //deleteWebpageEntry() is only used by addWebpageEntry() in an update for an existing entry
    private void deleteWebpageEntry(Webpage webpage) {
    	try { //try ... catch for possible DBExceptions
    		//ID of the page to be removed = deleteID
	    	Long deleteID = url_pageID.get(webpage.getMyUrl());
	    	//These operations are not needed since put() will write over the existing values
	    	//only kept for completeness -----------------------
	    	pageID_url.remove(deleteID);
	    	pageID_title.remove(deleteID);
	    	pageID_size.remove(deleteID);
	    	pageID_lastmodified.remove(deleteID);
	    	pageID_metaD.remove(deleteID);
	    	url_pageID.remove(webpage.getMyUrl());
	    	//--------------------------------------------------
	    	//take a subset of the Forward Index to get all entries with the same pageID as the one to be deleted
	    	//removeKeywords has all the keywords entries for the given page
	    	Set<Object[]> removeKeywords = content_forward.subSet(new Object[] {deleteID}, new Object[] {deleteID, new Posting(null, 0)});
			for (Object[] deleteEntry : removeKeywords) {
				//need to take out the keyword->page entry in the invered index
				//simply flip the first and the second element in the Object array to swap the page and the word IDs
				content_inverted.remove(new Object[]{((Posting) deleteEntry[1]).id, new Posting((Long) deleteEntry[0], ((Posting) deleteEntry[1]).frequency)});
			}
	    	//remove all the elements in the subset from the original set
	    	content_forward.removeAll(removeKeywords);
	    	
	    	//get all the entries to remove in the subset
	    	Set<Object[]> pcLink = parent_child.subSet(new Object[] {deleteID}, new Object[] {deleteID, null});
	    	parent_child.removeAll(pcLink); //removeAll
    	} catch (DBException e) {
    		e.printStackTrace();
    	}
    }

    
    //getWebpage() retrieves all the page data for a page that corresponds to the given pageID (basically for phase1 txt file use)
    @Override
    public Webpage getWebpage(Long id) {
    	synchronized (MapDBIndexer.class) {
	    	if (id >= pageID || !pageID_title.containsKey(id))
	    		return null;
	    	//Webpage object to return
	    	Webpage result = Webpage.create();
	    	//Set all the values that we need 
	    	result.setTitle(pageID_title.get(id));
	    	result.setSize(pageID_size.get(id));
	    	result.setMyUrl(pageID_url.get(id));
	    	result.setSize(pageID_size.get(id));
	    	result.setLastModified(pageID_lastmodified.get(id));
	    	//HashMap to use in setKeywordsAndFrequencies()
	    	HashMap<String, Integer> keyFreq = new HashMap<String, Integer>();
	    	//From the Forward Index (NavigableSet), take a subset
	    	//subSet(new Object[] {id}, new Object[] {id, new Posting(null, 0)})
	    	//takes a subset of the entire forward index that has the first element = id and second element from the bottom to Posting(null, 0)
	    	//Posting object is ordered by its value in the ID field and ID=null is set to be larger than any value
	    	//taking subset this way will then give all entries for the given page ID
	    	Set<Object[]> docKeyFreq = content_forward.subSet(new Object[] {id}, new Object[] {id, null, null});
            for (Object[] entry : docKeyFreq) {
                // element 0 is Long object (=pageID) and element 1 is Posting object
                //Posting p = (Posting)((it.next())[1]); //get element 1 (=Posting)
                keyFreq.put(wordID_keyword.get((Long) entry[1]), (Integer) entry[2]);
            }
	    
	    	result.setKeywordsAndFrequencies(keyFreq); //set the keywordFrequency 
	    	
	    	List<String> childLinks = new LinkedList<String>();
	    	//take the subset of the child_parent navigable set in a similar way
	    	//from id -> id, null => gives all that matches ID in the first element and everything for the second element
	    	Set<Object[]> cLinks = parent_child.subSet(new Object[] {id}, new Object[] {id, null});
            for (Object[] cLink : cLinks) {
                String cUrl = pageID_url.get((Long) ((cLink)[1])); //the second element in the Object array ([1]) is the childID
                childLinks.add(cUrl); //add them to the temporary list
            }
	    	result.setLinks(childLinks); //set the list to the page's child links
	    	//Body, MetaDescription and ParentURL are all not needed for our use -> set to empty string
	    	result.setBody("");
	    	result.setMetaDescription("");
	    	result.setParentUrl("");
	    	
	    	return result;
    	}
    }
    
    //commitAndClose() must be called to finish indexing to make the actual change (otherwise the file will get corrupted) 
    @Override
    public void commitAndClose() {
        db.commit(); 
        db.close(); //closes the db file -> cannot work with it anymore!
    }
}
