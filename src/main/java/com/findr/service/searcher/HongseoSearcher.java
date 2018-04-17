package com.findr.service.searcher;

import com.findr.object.Webpage;
import com.findr.service.indexer.MapDBIndexer;
import com.findr.service.utils.stemming.Vectorizer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

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
    private HTreeMap<Long, Double> pageID_pagerank;

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

        parent_child = db.treeSet("parent_child")
                .serializer(new SerializerArrayTuple(Serializer.LONG, Serializer.LONG))
                .createOrOpen();

        pageID = pageID_url.sizeLong();
        wordID = wordID_keyword.sizeLong();
    }

    private Double cosSim(Double docWeightSum, Double docWeightSqrSum, Double queryLength) {
        return new Double(docWeightSum.doubleValue() / (Math.sqrt(docWeightSqrSum.doubleValue()) + Math.sqrt(queryLength.doubleValue())));
    }

    @Override
    public List<Webpage> search(List<String> query, int maxResults) {
        synchronized (MapDBIndexer.class) {
            BlockingQueue<String> wordList = new LinkedBlockingQueue<String>();
            ConcurrentHashMap<Long, Double> weightsSum = new ConcurrentHashMap<Long, Double>();
            ConcurrentHashMap<Long, Double> weightsSqrSum = new ConcurrentHashMap<Long, Double>();
            TreeMap<Double, List<Long>> sortedByVSM = new TreeMap<Double, List<Long>>();
            TreeMap<Double, List<Long>> sortedByScore = new TreeMap<Double, List<Long>>();
            Double queryLength = 0.0;

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
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }

            // VSM = Vector Space Model
            // Score = titleMult*(w*VSM + (1-w)*PR/(log(rank of VSM) + alpha))
            // titleMult = 1 + 0.1*(the number of query terms that match at least one term in the title)
            // w determines whether to weight VSM or PR more
            // From https://guangchun.files.wordpress.com/2012/05/searchenginereport.pdf, use alpha = log5
            //
            // First, Get all VSM scores
            for (Long pID : weightsSum.keySet()) {
                Double vsmScore = cosSim(weightsSum.get(pID), weightsSqrSum.get(pID), queryLength);
                if (sortedByVSM.containsKey(vsmScore))
                    sortedByVSM.get(vsmScore).add(pID);
                else {
                    List<Long> list = new ArrayList<Long>();
                    list.add(pID);
                    sortedByVSM.put(vsmScore, list);
                }
            }

            // Then, calculate aggregate score
            for (Long pID : weightsSum.keySet()) {
                Double vsmScore = cosSim(weightsSum.get(pID), weightsSqrSum.get(pID), queryLength);
                int vsmRank = Arrays.asList(sortedByVSM.keySet().toArray()).indexOf(vsmScore);
                Double prScore = pageID_pagerank.get(pID);

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
                Double titleMult = 1 + titleMatchCount * 0.1;
                Double alpha = Math.log(5);
                Double w = 0.8;
                Double score = titleMult * (w * vsmScore + (1 - w) * prScore / (Math.log(vsmRank) + alpha));

                System.out.print("Title multiplier: " + titleMult.toString() + " ");
                System.out.print("VSM score: " + vsmScore.toString() + "  ");
                System.out.print("PR score: " + prScore.toString() + " ");
                System.out.println("Score: " + score.toString() + " " + "DocID: " + pID.toString() + "  " + "Title: " + pageID_title.get(pID));

                if (sortedByScore.containsKey(score))
                    sortedByScore.get(score).add(pID);
                else {
                    List<Long> list = new ArrayList<Long>();
                    list.add(pID);
                    sortedByScore.put(score, list);
                }
            }

            while (!sortedByScore.isEmpty() && (results.size() < maxResults)) {
                Entry<Double, List<Long>> lastEntry = sortedByScore.pollLastEntry();
                List<Long> pages = lastEntry.getValue();
                for (Long pID : pages) {
                    Webpage page = Webpage.create();
                    System.out.println(pID.toString() + " (" + lastEntry.getKey().toString() + ")");
                    page.setTitle(pageID_title.get(pID));
                    page.setMyUrl(pageID_url.get(pID));
                    page.setLastModified(pageID_lastmodified.get(pID));
                    page.setMetaDescription(pageID_metaD.get(pID));
                    page.setSize(pageID_size.get(pID));
                    HashMap<String, Integer> keywordAndFreq = new HashMap<String, Integer>();
                    Set<Object[]> docKeyFreq = content_forward.subSet(new Object[] {pID}, new Object[] {pID, null, null});
                    for (Object[] entry : docKeyFreq) {
                    	keywordAndFreq.put(wordID_keyword.get((Long)entry[1]), (Integer)entry[2]);
                    }
                    
                    page.setKeywordsAndFrequencies(keywordAndFreq);
                    
                    results.add(page);
                }
            }
            return results;
        }
    }

    @Override
    public List<List<String>> getRelatedQueries(List<String> query) {
        synchronized (MapDBIndexer.class) {
            //TODO
            return Collections.emptyList();
        }
    }

    class QueryHandler implements Callable<Double> {
        BlockingQueue<String> wordList;
        List<String> queryList;

        public QueryHandler(BlockingQueue<String> wordList, List<String> queryList) {
            this.wordList = wordList;
            this.queryList = queryList;
        }

        @Override
        public Double call() {
            double queryLength = 0.0;
            System.out.println("LENGTH: " + queryList.size());
            for (String query : queryList) {
                System.out.println("QUERY:" + query);
                HashMap<String, Integer> tokenizedQuery = Vectorizer.vectorize(query, true);
                for (String s : tokenizedQuery.keySet()) {
                    System.out.println("TOKENIZED: " + s);
                    for (int i = 0; i < tokenizedQuery.get(s); i++) {
                        queryLength++;
                        System.out.println("querylength: " + queryLength);
                        try {
                            wordList.put(s);
                            System.out.println("Done");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            for (int i = 0; i < NUM_OF_SEARCHER_THREAD; i++) {
                try {
                    wordList.put("");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return queryLength;
        }
    }

    class SimpleSearcher implements Runnable {
        BlockingQueue<String> wordList;
        ConcurrentHashMap<Long, Double> weightsSum;
        ConcurrentHashMap<Long, Double> weightsSqrSum;

        SimpleSearcher(BlockingQueue<String> wordList,
                       ConcurrentHashMap<Long, Double> weightsSum,
                       ConcurrentHashMap<Long, Double> weightsSqrSum) {
            this.wordList = wordList;
            this.weightsSum = weightsSum;
            this.weightsSqrSum = weightsSqrSum;
        }

        private double documentWeight(double tf, double tf_max, double df) {
            return (tf / tf_max) * (Math.log(((double) (pageID - 1)) / df) / Math.log(2));
        }

        @Override
        public void run() {
            try {
                System.out.println("Start");
                String word = wordList.take();
                while (!word.equals("")) {
                    Long wID = keyword_wordID.get(word);
                    Set<Object[]> documents = content_inverted.subSet(new Object[]{wID}, new Object[]{wID, null, null});
                    for (Object[] wordDocPair : documents) {
                        Long pID = (Long) wordDocPair[1];
                        System.out.println("Doc:" + pageID_title.get(pID));
                        Integer freq = (Integer) wordDocPair[2];
                        //Posting p = (Posting)wordDocPair[1];
                        double docWeight = documentWeight(freq, pageID_tfmax.get(pID), documents.size());
                        weightsSum.put(pID, (weightsSum.get(pID) != null ? weightsSum.get(pID) : 0) + docWeight);
                        weightsSqrSum.put(pID, (weightsSqrSum.get(pID) != null ? weightsSqrSum.get(pID) : 0) + Math.pow(docWeight, 2));
                    }
                    word = wordList.take();
                }
                System.out.println("END");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

//    public static void main(String[] args) {
//    	Searcher searcher = HongseoSearcher.getInstance();
//    	List<String> searchList = new ArrayList<String>();
//    	searchList.add("HKUST");
//    	List<Webpage> result = searcher.search(searchList, 30);
//    	for (Webpage wp : result) {
//    		System.out.println(wp.getTitle());
//    	}
//
//    }

}
