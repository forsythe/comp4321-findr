package com.findr.service.parser;

import com.findr.object.Webpage;
import com.findr.service.utils.stemming.Vectorizer;

import org.apache.commons.lang3.time.DateUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.split;

/**
 * The JSoup implementation of the Parser class
 */
public class JSoupParser implements Parser {

    private static final int TIMEOUT = 1000;
    private static final Logger log = LoggerFactory.getLogger(JSoupParser.class);

    @Override
    public Optional<Webpage> parse(String url, boolean handleRedirects, boolean doStoppingAndStemming) {
        log.debug("Parsing {}...", url);
        url = url.split("\\?")[0]; //remove query parameters
        log.debug("After removing query terms {}", url);
        Optional<Webpage> page = Optional.empty();

        try {
            Document doc;

            HttpURLConnection httpCon = (HttpURLConnection) new URL(url).openConnection();
            httpCon.setRequestProperty("Accept-Encoding", "identity");
            httpCon.setConnectTimeout(TIMEOUT);
            String rawBody;

            if (!handleRedirects && httpCon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return page;
            }

            httpCon.setInstanceFollowRedirects(true);

            httpCon = handleRedirectHttpURLConnection(httpCon);
            url = httpCon.getURL().toString();
            rawBody = getBody(httpCon);

            doc = Jsoup.parse(rawBody);

            String metaDest;

            //Check for html level meta-refresh redirection
            if (null != (metaDest = handleMetaRefreshHttpURLConnection(doc))) {
                url = metaDest;
                httpCon = (HttpURLConnection) new URL(url).openConnection();
                httpCon.setRequestProperty("Accept-Encoding", "identity");
                httpCon.setInstanceFollowRedirects(true);

                httpCon = handleRedirectHttpURLConnection(httpCon);
                rawBody = getBody(httpCon);
                doc = Jsoup.parse(rawBody);
            }

            LinkedHashMap<String, Integer> keywords = Vectorizer.vectorize(doc.text(), true);
            LinkedHashMap<String, Integer> titleKeywords = Vectorizer.vectorize(doc.title(), true);
            
            ArrayList<ArrayList<String>> triples = new ArrayList<ArrayList<String>>();
            List<String> docSplit = new ArrayList<String>(Arrays.asList(split(doc.text())));
            for (int i = 0; i < docSplit.size(); i++) {
            	Object[] vArray = Vectorizer.vectorize(docSplit.get(i), true).keySet().toArray();
            	String v = "";
            	if (vArray.length != 0) {
            		v = (String)vArray[0];
            	}
            	docSplit.set(i, v);
            }
            docSplit.removeIf(item -> item == null || item.equals(""));
            
            if (docSplit.size() >= 3) {
	            for (int i = 0; i < docSplit.size() - 2; i++) {
	            	ArrayList<String> triple = new ArrayList<String>();
	            	triple.add(docSplit.get(i));
	            	triple.add(docSplit.get(i + 1));
	            	triple.add(docSplit.get(i + 2));
	            	triples.add(triple);
	            }
            } else {
            	ArrayList<String> triple = new ArrayList<String>();
            	
            	switch (docSplit.size()) {
            	case 3:
	            	triple.add(docSplit.get(2));
	            	
            	case 2:
	            	triple.add(docSplit.get(1));
	            	
            	case 1:
            		triple.add(docSplit.get(0));
            		
            	default:
            		break;
            	}
            	
            	Collections.reverse(triple);
            	triples.add(triple);
            }

            String contentLength = httpCon.getHeaderField("Content-Length");
            long contentLengthLong;

            try {
                contentLengthLong = Long.parseLong(contentLength);
            } catch (Exception e) {
                contentLengthLong = rawBody.length();
            }

            String baseURL = httpCon.getURL().toString();
            String originalTitle = doc.title().isEmpty() ? baseURL : doc.title();
            String title = originalTitle;
    		Pattern urlTitlePattern = Pattern.compile("\\/([^\\/]*)(.htm)");
    		Matcher urlTitleMatcher = urlTitlePattern.matcher(baseURL);
    		while (urlTitleMatcher.find()) {
    			if (!urlTitleMatcher.group(1).toLowerCase().equals("index")) {
    				title = urlTitleMatcher.group(1).trim() + " - " + originalTitle;
    			} else {
    				title = originalTitle;
    			}
    		}
    		System.out.println("TITLING: " + title);

            Webpage result = Webpage.create()
                    .setLastModified(getLastModifiedDate(httpCon))
                    .setSize(contentLengthLong)
                    .setBody(rawBody)
                    .setChildren(getLinks(doc, baseURL))
                    .setTitle(title)
                    .setMyUrl(url)
                    .setKeywordsAndFrequencies(keywords)
                    .setTitleKeywordsAndFrequencies(titleKeywords)
                    .setTriples(triples)
                    .setMetaDescription(getMetaDescription(doc));

            //            if (result.getSize() == -1)
//                result.setSize(result.getBody().length());

            page = Optional.of(result);
            log.debug("Successfully downloaded {{}}", url);
        } catch (ClassCastException e) {
            log.warn("Link {{}} wasn't HTTP, skipping", url);
            //e.printStackTrace();
        } catch (SocketTimeoutException e) {
            log.warn("Socket timeout {{}}, skipping", url);
        } catch (MalformedURLException e) {
            log.warn("MalformedURLException, Couldn't download {{}}", url);
            //e.printStackTrace();
        } catch (IOException e) {
            log.warn("IOException, Couldn't download {{}}", url);
            //e.printStackTrace();
        }

        return page;
    }

    private static String handleMetaRefreshHttpURLConnection(Document d) {
        for (Element refresh : d.select("html head meta[http-equiv=refresh]")) {

            Matcher m = Pattern.compile("(?si)\\d+;\\s*url=(.+)|\\d+")
                    .matcher(refresh.attr("content"));

            // find the first one that is valid
            if (m.matches()) {
                if (m.group(1) != null) {
                    String newUrl = m.group(1);
                    newUrl = newUrl.replace("'", "");
                    //newUrl = newUrl.substring(1, newUrl.length() - 1); //trim the quote marks
                    log.warn("Handled meta refresh: {{}}", newUrl);
                    return newUrl;
                }
                break;
            }
        }
        return null;
    }

    private static HttpURLConnection handleRedirectHttpURLConnection(HttpURLConnection httpCon) throws IOException {
        int status = httpCon.getResponseCode();
        boolean redirect = false;
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER)
                redirect = true;
        }
        if (redirect) {

            // get redirect url from "location" header field
            String newUrl = httpCon.getHeaderField("Location");

            // open the new connnection again
            httpCon = (HttpURLConnection) new URL(newUrl).openConnection();
            httpCon.setRequestProperty("Accept-Encoding", "identity");
            httpCon.setConnectTimeout(TIMEOUT);
        }
        return httpCon;
    }

    private static String getBody(HttpURLConnection httpCon) throws IOException {
        int responseCode = httpCon.getResponseCode();
        InputStream inputStream;
        //LOG.info(responseCode);

        if ((200 <= responseCode && responseCode <= 299)
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
            inputStream = httpCon.getInputStream();
        } else {
            log.info("For {}, Response code: {}, getting error stream", httpCon.getURL(), responseCode);
            inputStream = httpCon.getErrorStream();
        }
        if (null == inputStream) {
            throw new IOException();
        }

        StringBuilder response = new StringBuilder();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(inputStream))) {
            String currentLine;
            while ((currentLine = in.readLine()) != null) {
                response.append(currentLine);
            }
        }
        return response.toString();
    }

    /**
     * Gets the last modified date of an HTTP header. If the date is null, it returns the current time, rounded down to
     * the nearest day
     *
     * @param httpCon The HttpURLConnection to the website
     * @return The last modified date, or today, rounded to nearest day
     */
    private static Date getLastModifiedDate(HttpURLConnection httpCon) {
        long date = httpCon.getLastModified();
        Date lastModified = DateUtils.truncate(new Date(), Calendar.DATE); //by default, set to today, rounded down. (in case info missing)
        if (date != 0) {
            lastModified.setTime(date);
        }
        return lastModified;
    }

    private static Collection<String> getLinks(Document doc, String baseURL) {
        //Elements linkElements = doc.select("a[href]");

        Elements linkElements = doc.select("a");
        /*
        Iterator<Element> it = linkElements.iterator();
        while (it.hasNext()) {
            Element e = (Element) it.next();
            System.out.println(e.toString());
        }
        */
        
        Collection<String> links = new ArrayList<>();

        linkElements.stream()
                .filter(x -> !x.attr("href").isEmpty())
                .filter(x -> !x.attr("abs:href").toLowerCase().endsWith(".mp4"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".wmv"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".avi"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".mpg"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".mpeg"))
                .filter(x -> !x.attr("href").startsWith("javascript"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".mp3"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".wav"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".zip"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".rar"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".tar.gz"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".jpg"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".png"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".jpg"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".jpeg"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".pdf"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".ppt"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".pptx"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".doc"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".docx"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".xls"))
                .filter(x -> !x.attr("href").toLowerCase().endsWith(".xlsx"))
                .forEach(x -> links.add(x.attr("href").split("\\?")[0]));

        Collection<String> linksFixed = new ArrayList<>();
        //TODO: do we need this?

        try {
           // System.out.println("HI");
           // System.out.println(baseURL);
          //  System.out.println("HI2");

            URL base = new URL(baseURL);

            for (int i = 0; i < links.size(); i++) {
                String l = ((ArrayList<String>) links).get(i);
                System.out.println("LINKS: " + l);
                if (l.indexOf("http") != 0 && l.indexOf('#') != 0) {
                    int secondSharp = 0;
                    if ((secondSharp = l.indexOf('#', 1)) != -1) {
                        l = l.substring(0, secondSharp);
                    }
                    URL fixed = new URL(base, l);
                    String fixedString = fixed.toString();
                   // System.out.println("FIXED: " + fixedString);
                    linksFixed.add(fixedString);
                   // System.out.println("ADDED");
                } else if (l.indexOf('#') == 0) {
                 //   System.out.println("SKIPPED");
                } else {
                  //  System.out.println("OTHER CASE: " + l);
                    int sharp = 0;
                    if ((sharp = l.indexOf('#', 0)) != -1) {
                        l = l.substring(0, sharp);
                    }
                  //  System.out.println("OTHER CASE PROCESSED: " + l);
                    linksFixed.add(l);
                  //  System.out.println("ADDED");
                }
            }

        } catch (Exception e) {
            //System.out.println("EXCEPTION");
        }

        log.debug("Found {} child links", links.size());
        return linksFixed;
    }

    private static String getMetaTag(Document document, String attr) {
        Elements elements = document.select("meta[name=" + attr + "]");
        for (Element element : elements) {
            final String s = element.attr("content");
            if (s != null) return s;
        }
        elements = document.select("meta[property=" + attr + "]");
        for (Element element : elements) {
            final String s = element.attr("content");
            if (s != null) return s;
        }
        return null;
    }

    private static String getMetaDescription(Document document) {
        String description = getMetaTag(document, "description");
        if (description == null) {
            description = getMetaTag(document, "og:description");
        }
        if (description == null)
            description = "No description is available for this site.";
        return description;
    }


}
