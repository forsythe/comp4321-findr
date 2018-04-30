package com.findr.service.searcher;

import com.findr.object.Webpage;
import com.findr.service.indexer.MapDBIndexer;
import com.findr.service.utils.stemming.Vectorizer;

import static org.apache.commons.lang3.StringUtils.split;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multithreaded implementation of the Searcher interface.
 */
public class MultithreadedSearcher implements Searcher {
    private static final Logger log = LoggerFactory.getLogger(MultithreadedSearcher.class);
	
	private static final int NUM_OF_SEARCHER_THREAD = 3;
	private static MultithreadedSearcher searcher = null;

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
	private HTreeMap<Long, Double> pageID_pagerank;

	private NavigableSet<Object[]> content_inverted; //word id to page ids, freq   
	private NavigableSet<Object[]> content_forward; //page id to word ids, freq

	private NavigableSet<Object[]> triple_inverted;

	private NavigableSet<Object[]> parent_child;
	private NavigableSet<Object[]> child_parent;

	private long totalPageCount = 0;
	private long totalWordCount = 0;

	private String updatedTime = null;

	public static MultithreadedSearcher getInstance() {
	    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
	    root.setLevel(ch.qos.logback.classic.Level.INFO);
		
		if (searcher == null)
			searcher = new MultithreadedSearcher();
		return searcher;
	}

	private MultithreadedSearcher() {
		db = DBMaker.fileDB("index.db")
				.fileChannelEnable()
				.fileMmapEnable()
				.fileMmapEnableIfSupported()
				.closeOnJvmShutdown()
				.readOnly()
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

		content_inverted = db.treeSet("content_inverted")
				.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG, Serializer.INTEGER))
				.createOrOpen();

		content_forward = db.treeSet("content_forward")
				.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG, Serializer.INTEGER))
				.createOrOpen();

		triple_inverted = db.treeSet("tripled_inverted")
				.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.INTEGER,
													 Serializer.LONG, Serializer.INTEGER,
													 Serializer.LONG, Serializer.INTEGER,
													 Serializer.LONG))
				.createOrOpen();

		parent_child = db.treeSet("parent_child")
				.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG))
				.createOrOpen();
		
		child_parent = db.treeSet("child_parent")
				.serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG))
				.createOrOpen();
	
		totalPageCount = pageID_url.sizeLong();
		totalWordCount = wordID_keyword.sizeLong();
	}

	private Double cosSim(Double docWeightSum, Double docWeightSqrSum, Double queryLength) {
		return new Double( docWeightSum.doubleValue() / (Math.sqrt(docWeightSqrSum.doubleValue()) + Math.sqrt(queryLength.doubleValue())));
	}

	@Override
	public List<Webpage> search(List<String> query, int maxResults) {
		synchronized(MapDBIndexer.class) {
			BlockingQueue<String> simpleWordList = new LinkedBlockingQueue<String>();
			ConcurrentHashMap<Long, Double> simpleWeightsSum = new ConcurrentHashMap<Long, Double>(); 
			ConcurrentHashMap<Long, Double> simpleWeightsSqrSum = new ConcurrentHashMap<Long, Double>();
			Double simpleQueryLength = new Double(0.0);
			
			// Each element in the array list is a blocking queue for a new phrase
			ArrayList<BlockingQueue<String>> phraseWordList = new ArrayList<BlockingQueue<String>>();
			ArrayList<ConcurrentHashMap<Long, Double>> phraseNormalisedWeights = new ArrayList<ConcurrentHashMap<Long, Double>>();
			Double phraseQueryLength = new Double(0.0);
			
			BlockingQueue<String> removeWordList = new LinkedBlockingQueue<String>();
			ConcurrentHashMap<Long, Double> removeWeightsSum = new ConcurrentHashMap<Long, Double>(); 
			ConcurrentHashMap<Long, Double> removeWeightsSqrSum = new ConcurrentHashMap<Long, Double>();
			
			TreeMap<Double, List<Long>> sortedByVSM = new TreeMap<Double, List<Long>>();
			TreeMap<Double, List<Long>> sortedByScore = new TreeMap<Double, List<Long>>();

			List<Webpage> results = new ArrayList<Webpage>();

			LinkedHashMap<String, ArrayList<String>> categorisedQuery = getCategorisedQuery(query.get(0));

			ExecutorService exec = Executors.newFixedThreadPool(NUM_OF_SEARCHER_THREAD + 1);

			// Simple search
			QueryHandler simpleQHandler = new QueryHandler(simpleWordList, categorisedQuery.get("simple"));
			Future<Double> simpleVal = exec.submit(simpleQHandler);
			for (int i = 0; i < NUM_OF_SEARCHER_THREAD; i++) {
				exec.execute(new SimpleSearcher(simpleWordList, simpleWeightsSum, simpleWeightsSqrSum));
			}

			// Phrase search
			int lastPhraseIndex = 0;
			ArrayList<Future<Double>> phraseVal = new ArrayList<Future<Double>>();
			for (String phraseQuery : categorisedQuery.get("phrase")) {
				ArrayList<String> phraseQueryList = new ArrayList<String>();
				phraseQueryList.add(phraseQuery);
				phraseWordList.add(new LinkedBlockingQueue<String>());
				phraseNormalisedWeights.add(new ConcurrentHashMap<Long, Double>());

				QueryHandler phraseQHandler = new QueryHandler(phraseWordList.get(lastPhraseIndex), phraseQueryList);
				phraseVal.add(exec.submit(phraseQHandler));

				exec.execute(new PhraseSearcher(phraseWordList.get(lastPhraseIndex), phraseNormalisedWeights.get(lastPhraseIndex)));
				lastPhraseIndex++;
			}
			
			// Remove search
			QueryHandler removeQHandler = new QueryHandler(removeWordList, categorisedQuery.get("remove"));
			exec.submit(removeQHandler);
			for (int i = 0; i < NUM_OF_SEARCHER_THREAD; i++) {
				exec.execute(new SimpleSearcher(removeWordList, removeWeightsSum, removeWeightsSqrSum));
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
				simpleQueryLength = simpleVal.get();
				for (Future<Double> val : phraseVal) {
					phraseQueryLength += val.get();
				}
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				return Collections.emptyList();
			}

			// Steps
			// 1) For all documents retrieved in simple and phrase search, remove documents that do not contain all phrases
			// 2) From the modified list of documents, remove all that are retrieved from remove search
			// 3) Rank

			// Step 1

			// If there is at least one phrase, base the retrieved documents on phrase retrieved documents
			// Otherwise, aggregate all documents
			ArrayList<ArrayList<Long>> retrievedDocs = new ArrayList<ArrayList<Long>>();
			ArrayList<Long> filteredDocs = new ArrayList<Long>();

			if (!phraseNormalisedWeights.isEmpty()) {
				for (ConcurrentHashMap<Long, Double> phraseMap : phraseNormalisedWeights) {
					ArrayList<Long> currDoc = new ArrayList<Long>();
					for (Long pID : phraseMap.keySet()) {
						currDoc.add(pID);
						log.info("{{}}", pageID_title.get(pID));
					}
					retrievedDocs.add(currDoc);
				}

				// Create union of all docs
				for (ArrayList<Long> list : retrievedDocs) {
					for (Long pID : list) {
						if (!filteredDocs.contains(pID)) {
							filteredDocs.add(pID);
						}
					}
				}

				// Get intersection
				for (ArrayList<Long> list : retrievedDocs) {
					filteredDocs.retainAll(list);
				}
			} else {
				for (Map.Entry<Long, Double> e : simpleWeightsSum.entrySet()) {
					filteredDocs.add(e.getKey());
				}
			}
			
			// Step 2
			filteredDocs.removeAll(removeWeightsSum.keySet());
			
			log.info("Documents to rank:");
			for (Long pID : filteredDocs) {
				log.info(pageID_title.get(pID));
			}

			// Step 3

			// VSM (Vector Space Model) = phraseMult*phraseVSM + (1-phraseMult)*simpleVSM
			// Score = titleMult*(w*VSM + (1-w)*PR/(log(rank of VSM) + alpha))
			// titleMult = 1 + 0.1*(the number of query terms that match at least one term in the title)
			// w determines whether to weight VSM or PR more
			// From https://guangchun.files.wordpress.com/2012/05/searchenginereport.pdf, use alpha = log5

			// First, VSM
			log.info("Calculating VSM scores");
			Double simpleVsmScore = new Double(0.0);
			for (Long pID : filteredDocs) {
				if (!simpleWeightsSum.isEmpty() && !simpleWeightsSqrSum.isEmpty()) {
					if (simpleWeightsSum.containsKey(pID)) {
						simpleVsmScore = cosSim(simpleWeightsSum.get(pID), simpleWeightsSqrSum.get(pID), simpleQueryLength);
					}
				}

				Double phraseVsmScore = new Double(0.0);
				for (ConcurrentHashMap<Long, Double> phraseMap : phraseNormalisedWeights) {
					for (Entry<Long, Double> e : phraseMap.entrySet()) {
						if (pID.equals(e.getKey())) {
							phraseVsmScore += e.getValue();
						}
					}
				}

				Double phraseMult = 0.8;
				Double vsmScore = (1 - phraseMult)*simpleVsmScore + phraseMult*phraseVsmScore;

				if (sortedByVSM.containsKey(vsmScore)) {
					sortedByVSM.get(vsmScore).add(pID);
				} else {
					List<Long> list = new ArrayList<Long>();
					list.add(pID);
					sortedByVSM.put(vsmScore, list);
				}

				log.info("({}) Simple score: {}, Phrase score: {}, Total score: {}",
						pageID_title.get(pID), simpleVsmScore, phraseVsmScore, vsmScore);
			}

			// Then, calculate aggregate score
			for (Entry<Double, List<Long>> e : sortedByVSM.entrySet()) {
				for (Long pID : e.getValue()) {
					Double vsmScore = e.getKey();
					Double prScore = pageID_pagerank.get(pID);
					int vsmRank = Arrays.asList(sortedByVSM.keySet().toArray()).indexOf(vsmScore);

					Integer titleMatchCount = 0;
					for (String q : query) {
						for (String subq : q.split(" ")) {
							ArrayList<String> subqTokens = new ArrayList<String>();
							for (String token : Vectorizer.vectorize(subq, true).keySet()) {
								subqTokens.add(token);
							}

							for (String token : subqTokens) {
								if (pageID_title.get(pID).toLowerCase().contains(token.toLowerCase())) {
									titleMatchCount += 1;
								}
							}
						}
					}
					Double titleMult = 1 + titleMatchCount*0.5;
					Double alpha = Math.log(5);
					Double w = 0.8;
					Double score = titleMult*(w*vsmScore + (1 - w)*prScore/(Math.log(vsmRank) + alpha));

					log.info("Title multiplier: {}, VSM score: {}, PR score: {}, Score: {}, DocID: {}, Title: {}",
								titleMult, vsmScore, prScore, score, pID, pageID_title.get(pID));

					if (sortedByScore.containsKey(score))
						sortedByScore.get(score).add(pID);
					else {
						List<Long> list = new ArrayList<Long>();
						list.add(pID);
						sortedByScore.put(score, list);
					}
				}
			}

			// Then, get top scoring pages
			while (!sortedByScore.isEmpty() && (results.size() < maxResults)) {
				Entry<Double, List<Long>> lastEntry = sortedByScore.pollLastEntry();
				List<Long> pages = lastEntry.getValue();
				for (Long pID : pages) {
					Webpage page = Webpage.create();
					log.info("{} ({})", pID, lastEntry.getKey());
					page.setTitle(pageID_title.get(pID));
					page.setMyUrl(pageID_url.get(pID));
					page.setLastModified(pageID_lastmodified.get(pID));
					page.setMetaDescription(pageID_metaD.get(pID));
					page.setSize(pageID_size.get(pID));
					page.setScore(lastEntry.getKey());
					LinkedHashMap<String, Integer> keywordAndFreq = new LinkedHashMap<String, Integer>();
					Set<Object[]> docKeyFreq = content_forward.subSet(new Object[] {pID}, new Object[] {pID, null, null});
					for (Object[] entry : docKeyFreq) {
						keywordAndFreq.put(wordID_keyword.get((Long)entry[1]), (Integer)entry[2]);
					}

					page.setKeywordsAndFrequencies(keywordAndFreq);
					Collection<String> childLinks = new LinkedList<>();
					Set<Object[]> cLinks = parent_child.subSet(new Object[] {pID}, new Object[] {pID, null});
					for (Object[] cLink : cLinks) {
						childLinks.add(pageID_url.get((Long)cLink[1]));
					}
					Collection<String> parentLinks = new LinkedList<>();
					Set<Object[]> pLinks = child_parent.subSet(new Object[] {pID}, new Object[] {pID, null});
					for (Object[] pLink : pLinks) {
						parentLinks.add(pageID_url.get((Long)pLink[1]));
					}
					page.setChildren(childLinks);
					page.setParents(parentLinks);
					
					results.add(page);
				}
			}
			return results;
		}
	}

	/**
	 * Split the query into phrases and simple searches.
	 * @param query the query.
	 * @return
	 */
	private LinkedHashMap<String, ArrayList<String>> getCategorisedQuery(String query) {
		LinkedHashMap<String, ArrayList<String>> lhm = new LinkedHashMap<String, ArrayList<String>>();
		lhm.put("simple", new ArrayList<String>()); // SimpleSearcher
		lhm.put("phrase", new ArrayList<String>()); // PhraseSearcher
		lhm.put("remove", new ArrayList<String>()); // SimpleSearcher, but remove all docs retrieved

		// 1) Collect all words/phrases and put them in simple
		// 2) Find all phrases and put them into phrase
		// 3) Find all removes and put them into remove
		// 4) Remove all phrases and removes from simple
		// 5) Remove all removes from phrases
		
		// Step 1
		String[] splitter = query.split("\"");
		for (String s : splitter) {
			if (!s.contains("-")) {
				lhm.get("simple").add(s.trim());
			} else {
				String[] hyphenSplitter = s.split(" ");
				for (String hs : hyphenSplitter) {
					lhm.get("simple").add(hs.trim().replaceAll("(^-)", ""));
				}
			}
		}
		lhm.get("simple").removeIf(item -> item == null || item.equals("") || item.equals("-"));

		// Step 2
		// Keep all words in "<word>"
		Pattern phrasePattern = Pattern.compile("\"([^\"]*)\"");
		Matcher phraseMatcher = phrasePattern.matcher(query);
		while (phraseMatcher.find()) {
			lhm.get("phrase").add(phraseMatcher.group(1).trim());
		}
		
		// Step 3
		// Remove all words in the format "-<word> " or "-<word>"
		Pattern removePattern = Pattern.compile("(?<=-)(.*?)(?= |$)");
		Matcher removeMatcher = removePattern.matcher(query);
		while (removeMatcher.find()) {
			lhm.get("remove").add(removeMatcher.group(1).trim().replaceAll("\"", ""));
		}
		
		// Step 4
		lhm.get("simple").removeAll(new HashSet<>(lhm.get("phrase")));
		lhm.get("simple").removeAll(new HashSet<>(lhm.get("remove")));
		
		// Step 5
		lhm.get("phrase").removeAll(new HashSet<>(lhm.get("remove")));

		log.info("Query: {}\n", query);
		for (String key : lhm.keySet()) {
			log.info("Type: {}", key);
			for (String s : lhm.get(key)) {
				log.info(s);
			}
			log.info("===============");
		}

		return lhm;
	}

	@Override
	public List<List<String>> getRelatedQueries(List<String> query) {
		synchronized (MapDBIndexer.class) {
			//TODO
			return Collections.emptyList();
		}
	}
	
    public Set<String> getKeywords() {
    	Set<String> result = new TreeSet<>();
    	

    	Collection<String> keywords = wordID_keyword.getValues();
    	Iterator<String> it = keywords.iterator();
    	while(it.hasNext()) {
    		result.add(it.next());
    	}
    
    	return result; 	
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
			log.debug("LENGTH: {}", queryList.size());
			for (String query : queryList) {
				log.debug("QUERY: {}", query);
				LinkedHashMap<String, Integer> tokenizedQuery = Vectorizer.vectorize(query, true);
				log.debug("TOKENS: ");
				for (String token : tokenizedQuery.keySet()) {
					log.debug("{}", token);
				}

				for (String s : tokenizedQuery.keySet()) {
					log.debug("TOKENIZED: {}", s);
					for (int i = 0; i < tokenizedQuery.get(s).intValue(); i++) {
						queryLength++;
						log.debug("querylength: {}", queryLength);
//						try {
//							wordList.put(s);
//							log.debug("Done");
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
					}
				}
				
				List<String> querySplit = new ArrayList<String>(Arrays.asList(split(query)));
	            for (int i = 0; i < querySplit.size(); i++) {
	            	Object[] vArray = Vectorizer.vectorize(querySplit.get(i), true).keySet().toArray();
	            	String v = "";
	            	if (vArray.length != 0) {
	            		v = (String)vArray[0];
	            	}
	            	querySplit.set(i, v);
	            }
	            querySplit.removeIf(item -> item == null || item.equals(""));
	            wordList.addAll(querySplit);
			}

			for (int i = 0; i < NUM_OF_SEARCHER_THREAD; i++) {
				try {
					wordList.put("");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return new Double(queryLength);
		}
	}

	class SimpleSearcher implements Runnable {
		BlockingQueue<String> wordList;
		ConcurrentHashMap<Long, Double> simpleWeightsSum;
		ConcurrentHashMap<Long, Double> simpleWeightsSqrSum;

		public SimpleSearcher(BlockingQueue<String> wordList, 
				ConcurrentHashMap<Long, Double> simpleWeightsSum, 
				ConcurrentHashMap<Long, Double> simpleWeightsSqrSum) {
			this.wordList = wordList;
			this.simpleWeightsSum = simpleWeightsSum;
			this.simpleWeightsSqrSum = simpleWeightsSqrSum;
		}

		private double documentWeight(double tf, double tf_max, double df) {
			return (tf/tf_max) * (Math.log(((double)(totalPageCount-1))/df) / Math.log(2));
		}

		@Override
		public void run() {
			try {
				log.info("Start");
				String word = wordList.take();
				while (!word.equals("")) {
					Long wID = keyword_wordID.get(word);
					Set<Object[]> documents = content_inverted.subSet(new Object[] {wID}, new Object[] {wID, null, null});
					Iterator<Object[]> it = documents.iterator();
					while (it.hasNext()) {
						Object[] wordDocPair = it.next();
						Long pID = (Long)wordDocPair[1]; 
						log.info("Doc: {}", pageID_title.get(pID));
						Integer freq = (Integer)wordDocPair[2];
						//Posting p = (Posting)wordDocPair[1];
						double docWeight = documentWeight(freq, pageID_tfmax.get(pID), documents.size());
						simpleWeightsSum.put(pID, new Double((simpleWeightsSum.get(pID) != null ? simpleWeightsSum.get(pID) : 0) + docWeight));
						simpleWeightsSqrSum.put(pID, new Double((simpleWeightsSqrSum.get(pID) != null ? simpleWeightsSqrSum.get(pID) : 0) + Math.pow(docWeight, 2)));	
					}
					word = wordList.take();
				}
				log.info("End");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
		}
	}

	class PhraseSearcher implements Runnable {
		BlockingQueue<String> wordList;
		ConcurrentHashMap<Long, Double> normalisedWeights;
		List<String> words;

		public PhraseSearcher(BlockingQueue<String> wordList, ConcurrentHashMap<Long, Double> normalisedWeights) {
			this.wordList = wordList;
			this.normalisedWeights = normalisedWeights;
		}

		private void printTriple(Object[] o) {
			String s = "";
			for (int i = 0; i < 7; i++) {
				if (i == 0 || i == 2 || i == 4) {
					s += wordID_keyword.get(o[i]).toString() + ",";
				} else {
					s += o[i].toString() + ",";
				}
			}
			log.info(s);
		}

		private void printSeperator() {
			log.info("===========================================");
		}

		/**
		 * w_i,j = tf_i,j * log_2(N/df_j)
		 * @param tf the term frequency of word j in document i tf_i,j
		 * @param df document frequency of word j df_j
		 * @return
		 */
		private double getDocumentWeight(double tf, double df) {
			return tf * (Math.log(((double)(totalPageCount-1))/df) / Math.log(2));
		}

		/**
		 * w_Q,j = tf_Q,j * log_2(N/df_j)
		 * @param tf the term frequency of word j in query Q tf_Q,j
		 * @param df document frequency of word j df_j
		 * @return
		 */
		private double getQueryWeight(double tf, double df) {
			return tf * (Math.log(((double)(totalPageCount-1))/df) / Math.log(2));
		}

		/**
		 * 
		 * @param words the phrase.
		 * @param d1 the first part of the phrase.
		 * @param d2 the second part of the phrase.
		 * @return true if the each triplet in words exists in the document.
		 */
		private boolean isPhraseInDoc(List<String> words, Long d) {
			for (int i = 0; i < words.size() - 2; i++) {
				boolean found = false;

				String word1 = words.get(i);
				String word2 = words.get(i + 1);
				String word3 = words.get(i + 2);

				Long id1 = keyword_wordID.get(word1);
				Long id2 = keyword_wordID.get(word2);
				Long id3 = keyword_wordID.get(word3);

				Set<Object[]> documents = triple_inverted.subSet(new Object[] {id1}, new Object[] {id1, null,
																								   id2, null,
																								   id3, null,
																								   d});

				if (documents.isEmpty()) {
					return false;
				} else {
					for (Object[] document : documents) {					
						// If all words is found in d at least once, go to next word
						// Otherwise, return false
						if (((Long)document[0]).equals(id1) && ((Long)document[2]).equals(id2) &&
								((Long)document[4]).equals(id3) && ((Long)document[6]).equals(d)) {
							found = true;
							break;
						}
					}

					if (!found) {
						return false;
					}
				}
			}
			return true;
		}

		/**
		 * 
		 * @param words the list of words for phrase search.
		 * @return the document IDs, and their tf-idf values, of pages containing all the words of the phrase.
		 */
		private TreeMap<Long, Double> phraseSearch(List<String> words) {
			if (words.size() > 2) {
				int midIndex = (int)(words.size()/2);
				TreeMap<Long, Double> d1 = phraseSearch(words.subList(0, midIndex));
				TreeMap<Long, Double> d2 = phraseSearch(words.subList(midIndex, words.size()));

				log.info("Searching size = {}", words.size());
				
				String wordLog = "Words: ";
				for (String word : words) {
					wordLog += word + ",";
				}
				log.info(wordLog);

				TreeMap<Long, Double> dl = new TreeMap<Long, Double>();

				for (Long d : d1.keySet()) {
					if (isPhraseInDoc(words, d)) {
						Double d1Weight = d1.get(d) != null ? d1.get(d) : 0.0;
						Double d2Weight = d2.get(d) != null ? d2.get(d) : 0.0;
						Double weight = d1Weight + d2Weight;
						dl.put(d, weight);
					}
				}
				for (Long d : d2.keySet()) {
					if (isPhraseInDoc(words, d)) {
						Double d1Weight = d1.get(d) != null ? d1.get(d) : 0.0;
						Double d2Weight = d2.get(d) != null ? d2.get(d) : 0.0;
						Double weight = d1Weight + d2Weight;
						dl.put(d, weight);
					}
				}

				printSeperator();
				return dl;
			} else if (words.size() == 2) {
				log.info("Searching size = 2");

				String word1 = words.get(0);
				String word2 = words.get(1);

				Long id1 = keyword_wordID.get(word1);
				Long id2 = keyword_wordID.get(word2);

				log.info("Words: {},{}", word1, word2);

				Set<Object[]> documents = triple_inverted.subSet(new Object[] {id1}, new Object[] {id1, null,
																								   id2, null,
																								   null, null,
																								   null});

				Iterator<Object[]> it = documents.iterator();
				TreeMap<Long, Double> d = new TreeMap<Long, Double>();
				TreeMap<Long, LinkedHashMap<Long, Integer>> intermediateScores = new TreeMap<Long, LinkedHashMap<Long, Integer>>();

				while(it.hasNext()) {
					Object[] triple = it.next();
					printTriple(triple);
					
					Long wID1 = (Long)triple[0];
					Long wID2 = (Long)triple[2];

					if (wID1.equals(id1) && wID2.equals(id2)) {
						Long pID = (Long)triple[6];
						
						Integer tf_ij1 = (Integer)triple[1];
						Integer tf_ij2 = (Integer)triple[3];
						
						LinkedHashMap<Long, Integer> tempScores = new LinkedHashMap<Long, Integer>();
						tempScores.put(wID1, tf_ij1);
						tempScores.put(wID2, tf_ij2);
						intermediateScores.put(pID, tempScores);
						
						d.put(pID, 0.0);
						log.info(" [KEPT]");
					}
				}
				
				for (Long pID : d.keySet()) {
					Long wID1 = (Long)intermediateScores.get(pID).keySet().toArray()[0];
					Long wID2 = (Long)intermediateScores.get(pID).keySet().toArray()[1];
					
					Integer tf_ij1 = (Integer)intermediateScores.get(pID).get(wID1);
					Integer tf_ij2 = (Integer)intermediateScores.get(pID).get(wID2);
					
					Integer tf_Qj1 = Collections.frequency(this.words, (String)wordID_keyword.get(wID1));
					Integer tf_Qj2 = Collections.frequency(this.words, (String)wordID_keyword.get(wID2));
					
					Integer df_j = d.size();
					
					Set<Object[]> wordsInDi = content_forward.subSet(new Object[] {pID}, new Object[] {pID, null, null});
					Integer wordCountDi = wordsInDi.size();
					
					Double queryWeight1 = getQueryWeight(tf_Qj1, df_j);
					Double queryWeight2 = getQueryWeight(tf_Qj2, df_j);
					Double docWeight1 = getDocumentWeight(tf_ij1, df_j);
					Double docWeight2 = getDocumentWeight(tf_ij2, df_j);
					
					Double weight = queryWeight1*docWeight1/Math.sqrt(wordCountDi); // tf_Q,j*tf_i,j/sqrt(number of words in D_i)
					weight += queryWeight2*docWeight2/Math.sqrt(wordCountDi);
					
					d.put(pID, weight);
				}

				printSeperator();
				return d;
			} else if (words.size() == 1) { // 1 word only
				log.info("Searching size = 1");
				log.info("Word: {}", words.get(0));

				String word = words.get(0);
				Long wID = keyword_wordID.get(word);
				Set<Object[]> documents = content_inverted.subSet(new Object[] {wID}, new Object[] {wID, null, null});
				Iterator<Object[]> it = documents.iterator();
				TreeMap<Long, Double> d = new TreeMap<Long, Double>();

				while (it.hasNext()) {
					Object[] triple = it.next();
					String tripleLog = wordID_keyword.get((Long)triple[0]).toString() + ","
										+ ((Long)triple[1]).toString() + "," + ((Integer)triple[2]).toString();

					if (((Long)triple[0]).equals(wID)) {
						Long pID = (Long)triple[1];
						Integer tf_ij = (Integer)triple[2];
						d.put(pID, tf_ij.doubleValue());
						
						tripleLog += " [KEPT]";
					}
					
					log.info(tripleLog);
				}
				
				for (Long pID : d.keySet()) {
					Integer tf_Qj = Collections.frequency(this.words, (String)wordID_keyword.get(wID));
					Integer tf_ij = d.get(pID).intValue();
					Integer df_j = d.size();
					
					Set<Object[]> wordsInDi = content_forward.subSet(new Object[] {pID}, new Object[] {pID, null, null});
					Integer wordCountDi = wordsInDi.size();
					
					Double queryWeight = getQueryWeight(tf_Qj, df_j);
					Double docWeight = getDocumentWeight(tf_ij, df_j);
					
					Double weight = queryWeight*docWeight/Math.sqrt(wordCountDi); // tf_Q,j*tf_i,j/sqrt(number of words in D_i)
					d.put(pID, weight);
				}

				printSeperator();
				return d;
			} else { // No words
				return new TreeMap<Long, Double>();
			}
		}

		@Override
		public void run() {
			try {
				log.info("Start");

				this.words = new ArrayList<String>();
				String word = wordList.take();
				while(!word.equals("")) {
					this.words.add(word);
					word = wordList.take();
				}

				log.info("Size of wordList: {}", this.words.size());
				for (String w : this.words) {
					log.info("{}", w);
				}

				TreeMap<Long, Double> documents = phraseSearch(this.words);
				normalisedWeights.putAll(documents);

				log.info("Documents retrieved:");
				for (Entry<Long, Double> d : normalisedWeights.entrySet()) {
					Long pID = d.getKey();
					Double score = d.getValue();
					log.info("{} {} ({})", pID, pageID_url.get(pID), score);
				}

				log.info("End");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
		}
	}

	/**
	 * For testing.
	 * @param args
	 */
	public static void main(String[] args) {
		Searcher searcher = MultithreadedSearcher.getInstance();
		List<String> searchList = new ArrayList<String>();
		searchList.add("HKUST");
		List<Webpage> result = searcher.search(searchList, 30);
		for (Webpage wp : result) {
			log.info(wp.getTitle());
		}
	}
}
