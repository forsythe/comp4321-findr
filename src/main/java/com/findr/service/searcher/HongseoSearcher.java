package com.findr.service.searcher;

import com.findr.object.Posting;
import com.findr.object.Webpage;
import com.findr.service.crawler.Crawler;
import com.findr.service.crawler.JSoupMultithreadedCrawler;
import com.findr.service.stemming.Vectorizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.mapdb.*;
import org.mapdb.serializer.SerializerArrayTuple;

/**
 * Hongseo's implementation of the Searcher interface
 */
public class HongseoSearcher implements Searcher {
	private static final int NUM_OF_SEARCHER_THREAD = 3;
	private static HongseoSearcher hongseoSearcher = null;
	
	private DB db;
	
    private HTreeMap<String, Long> keyword_wordID;
    private HTreeMap<Long, String> wordID_keyword;
    
    private HTreeMap<Long, String> pageID_url;
    private HTreeMap<String, Long> url_pageID;
    
    private HTreeMap<Long, String> pageID_title;
    private HTreeMap<Long, Long> pageID_size; 
    private HTreeMap<Long, Date> pageID_lastmodified;
    private HTreeMap<Long, String> pageID_metaD;
    
    private HTreeMap<Long, Integer> pageID_tfmax;
    
    private NavigableSet<Object[]> content_inverted; //keyword to word id, freq   
    private NavigableSet<Object[]> content_forward; //page to keywords, freq
    
    private NavigableSet<Object[]> parent_child;
    
	private long pageID = 0;
	private long wordID = 0;
	
	private String updatedTime = null;
	
	public static HongseoSearcher getInstance() {
		if (hongseoSearcher == null)
			hongseoSearcher = new HongseoSearcher();
		return hongseoSearcher;
	}

	private HongseoSearcher() {
		if (!checkCurrentDBFile()) {
			loadFromOriginalDBInfo();
			copyDBFileandUpdateInfo();
		}
		
		db = DBMaker.fileDB("index_current.db")
				.fileChannelEnable()
				.fileMmapEnable()
				.fileMmapEnableIfSupported()
				.closeOnJvmShutdown()
				.make();
		
		pageID_url = db.hashMap("pageID_url")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.STRING)
        		.createOrOpen();
        
        url_pageID = db.hashMap("url_pageID")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.LONG)
                .createOrOpen();
        
        wordID_keyword = db.hashMap("wordID_keyword")
        		.keySerializer(Serializer.LONG)
        		.valueSerializer(Serializer.STRING)
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
        
        content_inverted = db.treeSet("content_inverted")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.JAVA))
        		.createOrOpen();
           
        content_forward = db.treeSet("content_forward")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.JAVA))
        		.createOrOpen();
        
        parent_child = db.treeSet("parent_child")
        		.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG))
        		.createOrOpen();
        
        /*
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
		ses.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				reloadDB();
			}
		}, 1, 1, TimeUnit.HOURS);
         */	
	}
	
	private void loadFromOriginalDBInfo() {
		File infoFile = new File("index_info");		
		while (!infoFile.exists()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	
		try {
			FileReader fr = new FileReader("index_info");
			BufferedReader br = new BufferedReader(fr);
			updatedTime = br.readLine();
			pageID = Long.valueOf(br.readLine()).longValue();
			wordID = Long.valueOf(br.readLine()).longValue();
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	private void copyDBFileandUpdateInfo() {
		try {
			FileInputStream originalDB = new FileInputStream("index.db");
			FileOutputStream copyDB = new FileOutputStream("index_current.db");
			byte[] buffer = new byte[256];
			int end;
			while ((end = originalDB.read(buffer)) != -1)
				copyDB.write(buffer);
			copyDB.flush();
			copyDB.close();
			originalDB.close();
			
			FileWriter fw = new FileWriter("index_info_current");
			PrintWriter pw = new PrintWriter(new BufferedWriter(fw));
			pw.println(updatedTime);
			pw.println(pageID);
			pw.println(wordID);
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	private void cleanCurrentFiles() {
		File currentInfoFile = new File("index_info_current");
		File currentDBFile = new File("index_current.db");
		
		if (currentInfoFile.exists())
			currentInfoFile.delete();
		if (currentDBFile.exists())
			currentDBFile.delete();
	}
	
	private boolean checkCurrentDBFile() {
		String lastUpdated = null; 
		boolean infoFileMissing = false;
		long pID = 0; long wID = 0;
	
		File currentInfoFile = new File("index_info_current");
		if (currentInfoFile.exists()) {
			try {
				FileReader fr = new FileReader("index_info_current");
				BufferedReader br = new BufferedReader(fr);
				updatedTime = br.readLine();
				pageID = Long.valueOf(br.readLine()).longValue();
				wordID = Long.valueOf(br.readLine()).longValue();
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
				cleanCurrentFiles();
				return false;
			}
			
			File currentDBFile = new File("index_current.db");
			if (!currentDBFile.exists()) {
				cleanCurrentFiles();
				return false;
			}
			
			File infoFile = new File("index_info");
			if (infoFile.exists()) {
				try {
					FileReader fr = new FileReader("index_info");
					BufferedReader br = new BufferedReader(fr);
					lastUpdated = br.readLine();
					pID = Long.valueOf(br.readLine()).longValue();
					wID = Long.valueOf(br.readLine()).longValue();
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
					return true;
				}
				
				if (Long.valueOf(lastUpdated).compareTo(Long.valueOf(updatedTime)) > 0) {
					pageID = pID;
					wordID = wID;
					updatedTime = lastUpdated;
					return false;
				}
			}
			return true;	
		}
		else {
			cleanCurrentFiles();
			return false;
		}	
	}
	
	private void reloadDB() {
		 
	}
	
	private Double cosSim(Double docWeightSum, Double docWeightSqrSum, Double queryLength) {
		return new Double( docWeightSum.doubleValue() / (Math.sqrt(docWeightSqrSum.doubleValue()) + Math.sqrt(queryLength.doubleValue())));
	}
	
    @Override
    public synchronized List<Webpage> search(List<String> query, int maxResults) {
    	BlockingQueue<String> wordList= new LinkedBlockingQueue<String>();
    	ConcurrentHashMap<Long, Double> weightsSum = new ConcurrentHashMap<Long, Double>(); 
    	ConcurrentHashMap<Long, Double> weightsSqrSum = new ConcurrentHashMap<Long, Double>(); 
        TreeMap<Double, Long> sortedByScore = new TreeMap<Double, Long>();
    	Double queryLength = new Double(0.0);
        
    	List<Webpage> results = new ArrayList<Webpage>();
    	
    	ExecutorService exec = Executors.newFixedThreadPool(NUM_OF_SEARCHER_THREAD + 1);
    	QueryHandler qHandler = new QueryHandler(wordList, query);
    	Future<Double> val = exec.submit(qHandler);
    	
    	for (int i = 0; i < NUM_OF_SEARCHER_THREAD; i++) {
    		exec.execute(new SimpleSearcher(wordList, weightsSum, weightsSqrSum));
    	}
    	
    	try {
    		exec.shutdown();
    		exec.awaitTermination(10, TimeUnit.SECONDS);
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    		exec.shutdownNow();
    		return Collections.emptyList();
    	}
    	
    	try {
    		queryLength = val.get();
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    		return Collections.emptyList();
    	} catch (ExecutionException e) {
    		e.printStackTrace();
    		return Collections.emptyList();
    	}
    	
    	for (Long pID : weightsSum.keySet()) {
    		sortedByScore.put(cosSim(weightsSum.get(pID), weightsSqrSum.get(pID), queryLength), pID);
    	}
    	
    	while (!sortedByScore.isEmpty() || results.size() < maxResults) {
    		Webpage page = Webpage.create();
    		Long pID = sortedByScore.pollLastEntry().getValue();
    		page.setTitle(pageID_title.get(pID));
    		page.setMyUrl(pageID_url.get(pID));
    		page.setLastModified(pageID_lastmodified.get(pID));
    		page.setMetaDescription(pageID_metaD.get(pID));
    		
    		results.add(page);
    	}
    	return results;
    }

    @Override
    public synchronized List<List<String>> getRelatedQueries(List<String> query) {
        //TODO
        return Collections.emptyList();
    }
    
    class QueryHandler implements Callable<Double> {
    	BlockingQueue<String> wordList;
    	List<String> queryList;
    	
    	public QueryHandler(BlockingQueue<String> wordList, List<String> queryList) {
    		this.wordList = wordList;
    		this.queryList = queryList;
    	}
    	
		@Override
		public Double call() throws Exception {
			double queryLength = 0.0;
			for (String query : queryList) {
				HashMap<String, Integer> tokenizedQuery = Vectorizer.vectorize(query, true);
				for (String s : tokenizedQuery.keySet()) {
					for (int i = 0; i < tokenizedQuery.get(s).intValue(); i++) {
						queryLength++;
						try {
							wordList.put(s);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			
			for (int i = 0; i < NUM_OF_SEARCHER_THREAD; i++) {
				try {
					wordList.put(null);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return new Double(queryLength);
		}
    }

    class SimpleSearcher implements Runnable {
    	BlockingQueue<String> wordList;
    	ConcurrentHashMap<Long, Double> weightsSum;
    	ConcurrentHashMap<Long, Double> weightsSqrSum;
    	
    	public SimpleSearcher(BlockingQueue<String> wordList, 
    			ConcurrentHashMap<Long, Double> weightsSum, 
    			ConcurrentHashMap<Long, Double> weightsSqrSum) {
    		this.wordList = wordList;
    		this.weightsSum = weightsSum;
    		this.weightsSqrSum = weightsSqrSum;
    	}
    	
    	private double documentWeight(double tf, double tf_max, double df) {
			return (tf/tf_max) * (Math.log(((double)(pageID-1))/df) / Math.log(2));
		}

		@Override
		public void run() {
			try {
				String word = wordList.take();
				while (word != null) {
					Long wID = keyword_wordID.get(word);
					Set<Object[]> documents = content_inverted.subSet(new Object[] {wID}, new Object[] {wID, new Posting(null, 0)});
					Iterator<Object[]> it = documents.iterator();
					while (it.hasNext()) {
						Object[] wordDocPair = it.next();
						Posting p = (Posting)wordDocPair[1];
						double docWeight = documentWeight(p.frequency, pageID_tfmax.get(p.id), documents.size());
						weightsSum.put(p.id, new Double((weightsSum.get(p.id) != null ? weightsSum.get(p.id) : 0) + docWeight));
						weightsSqrSum.put(p.id, new Double((weightsSqrSum.get(p.id) != null ? weightsSqrSum.get(p.id) : 0) + Math.pow(docWeight, 2)));	
					}
					word = wordList.take();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
    	
    }
}
