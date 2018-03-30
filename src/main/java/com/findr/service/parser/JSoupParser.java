package com.findr.service.parser;

import com.findr.object.Webpage;
import com.findr.service.stemming.Vectorizer;
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

/**
 * The JSoup implementation of the Parser class
 */
public class JSoupParser implements Parser {

    private static final int TIMEOUT = 1000;
    private static final Logger log = LoggerFactory.getLogger(JSoupParser.class);

    @Override
    public Optional<Webpage> parse(String url, boolean handleRedirects) {
        log.debug("Parsing {}...", url);
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

            HashMap<String, Integer> keywords = Vectorizer.vectorize(doc.text(), true);
            HashMap<String, Integer> titleKeywords = Vectorizer.vectorize(doc.title(), true);
            
            String contentLength = httpCon.getHeaderField("Content-Length");
            long contentLengthLong;
            
            try {
            	contentLengthLong = Long.parseLong(contentLength);
            } catch (Exception e) {
            	contentLengthLong = rawBody.length();
            }
     
            String baseURL = httpCon.getURL().toString();
            
            Webpage result = Webpage.create()
                    .setLastModified(getLastModifiedDate(httpCon))
                    .setSize(contentLengthLong)
                    .setBody(rawBody)
                    .setLinks(getLinks(doc, baseURL))
                    .setTitle(doc.title())
                    .setMyUrl(url)
                    .setKeywordsAndFrequencies(keywords)
                    .setTitleKeywordsAndFrequencies(titleKeywords)
                    .setMetaDescription(getMetaDescription(doc));

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

        Collection<String> links = new ArrayList<>();

        linkElements.stream()
                .filter(x -> !x.attr("href").isEmpty())
                .filter(x -> !x.attr("abs:href").endsWith("mp4"))
                .forEach(x -> links.add(x.attr("href")));
        
        Collection<String> linksFixed = new ArrayList<String>();
        try {
        	System.out.println("HI");
        	System.out.println(baseURL);
        	System.out.println("HI2");
        	
        URL base = new URL(baseURL);
        
        for (int i = 0; i < links.size(); i++ ) {
        	String l = ((ArrayList<String>) links).get(i);
        	System.out.println("LINKS: " + l);
        	if (l.indexOf("http") != 0 && l.indexOf('#') != 0) {
        		int secondSharp = 0; 
        		if ((secondSharp = l.indexOf('#', 1)) != -1) {
        			l = l.substring(0, secondSharp);
        		}
        		URL fixed = new URL(base , l);
        		String fixedString  = fixed.toString();
        		System.out.println("FIXED: " + fixedString);
        		linksFixed.add(fixedString);
        		System.out.println("ADDED");
        	}
        	else if (l.indexOf('#') == 0) {
        		System.out.println("SKIPPED");
        	}
        	else {
        		System.out.println("OTHER CASE: " + l);
        		int sharp = 0;
        		if ((sharp = l.indexOf('#', 0)) != -1) {
        			l = l.substring(0, sharp);
        		}
        		System.out.println("OTHER CASE PROCESSED: " + l);
        		linksFixed.add(l);
        		System.out.println("ADDED");
        	}
        }
        
        }
        catch (Exception e) {
        	System.out.println("FUCKED");
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
