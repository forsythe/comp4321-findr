package com.findr.service.searcher;

import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

public class WolframSearcher {
	static final String baseURL = "http://api.wolframalpha.com/v2/query";
	static final String appID = "Q5UT29-63J64XWLR4";

	LinkedHashMap<String, String> calculationResult = new LinkedHashMap<>();
	LinkedHashMap<String, String> knowledgeGraph = new LinkedHashMap<>();

	public LinkedHashMap<String, String> getCalcResult() {
		if (calculationResult.isEmpty() || (calculationResult.size() == 1))
			return null;
		return calculationResult;
	}

	public LinkedHashMap<String, String> getKnowledgeGraph() {
		if (knowledgeGraph.isEmpty() || (knowledgeGraph.size() == 1))
			return null;
		return knowledgeGraph;
	}

	public void search(String text) {
		@SuppressWarnings("deprecation")
		String query = URLEncoder.encode(text);

		try {
			StringBuffer buf = new StringBuffer();
			URL queryURL = new URL(baseURL + "?appid=" + appID + "&input=" + query + "&format=plaintext,image" + "&podtitle=Input*" + "&podtitle=Result" + "&podtitle=Exact+result" + "&podtitle=Decimal+approximation" + "&podtitle=Basic+information"); // + "&podtitle=Image");
			HttpURLConnection conn = (HttpURLConnection) queryURL.openConnection();

			int status = conn.getResponseCode();

			if (status == HttpURLConnection.HTTP_OK) {
				InputStream in = conn.getInputStream();
				InputStreamReader inreader = new InputStreamReader(in);
				BufferedReader bufreader = new BufferedReader(inreader);
				String line = null;
				while ((line = bufreader.readLine()) != null) {
					//System.out.println(line);
					if (line.contains("|"))
						buf.append('\n');
					else 
						buf.append(" | ");

					buf.append(line);
				}
				bufreader.close();
				inreader.close();
				in.close();

				//System.out.println("-------------------");

				if (buf.indexOf("<queryresult success='false'") != -1) {}
				else {
					int titleIdx = buf.indexOf("<pod title='Input");
					if (titleIdx != -1) {
						String input = buf.substring(titleIdx + buf.substring(titleIdx).indexOf("<plaintext>") + 11, titleIdx + buf.substring(titleIdx).indexOf("</plaintext>"));
						calculationResult.put("Input", input);
						knowledgeGraph.put("Input", input);
					}
					int resultIdx = buf.indexOf("<pod title='Result'");
					if (resultIdx != -1) {
						String result = buf.substring(resultIdx + buf.substring(resultIdx).indexOf("<plaintext>") + 11, resultIdx + buf.substring(resultIdx).indexOf("</plaintext>"));
						calculationResult.put("Result", result);
					}
					int extResultIdx = buf.indexOf("<pod title='Exact result'");
					if (extResultIdx != -1) {
						String exactResult = buf.substring(extResultIdx + buf.substring(extResultIdx).indexOf("<plaintext>") + 11, extResultIdx + buf.substring(extResultIdx).indexOf("</plaintext>"));
						calculationResult.put("Exact Result", exactResult);
					}
					int decapproxIdx = buf.indexOf("<pod title='Decimal approximation'");
					if (decapproxIdx != -1) {
						String decApprox = buf.substring(decapproxIdx + buf.substring(decapproxIdx).indexOf("<plaintext>") + 11, decapproxIdx + buf.substring(decapproxIdx).indexOf("</plaintext>"));
						decApprox = decApprox.substring(0, 37);
						decApprox += "...";
						calculationResult.put("Decimal Approximation", decApprox);
					}
					int basicInfoIdx = buf.indexOf("<pod title='Basic information'");
					if (basicInfoIdx != -1) {
						String content = buf.substring(basicInfoIdx + buf.substring(basicInfoIdx).indexOf("<plaintext>") + 11, basicInfoIdx + buf.substring(basicInfoIdx).indexOf("</plaintext>"));
						String elements[] = content.split("\n");
						for (String element : elements) {
							//System.out.println("ELEMENT: " + element);
							knowledgeGraph.put(element.substring(0, element.indexOf("|") - 1), element.substring(element.indexOf("|") + 1));
						}
					}
					//					int imgIdx = buf.indexOf("<pod title='Image'");
					//					if (imgIdx != -1) {
					//						String imgURL = buf.substring(imgIdx + buf.substring(imgIdx).indexOf("<imagesource>") + 13, imgIdx + buf.substring(imgIdx).indexOf("</imagesource>"));
					//						knowledgeGraph.put("Image", imgURL);
					//					}

				}
				//				System.out.println("Calculation Result");
				//				for (String k : calculationResult.keySet()) {
				//					System.out.println(k + ": " + calculationResult.get(k));
				//				}
				//				System.out.println("-------------------");
				//				
				//				System.out.println("Knowledge Graph");
				//				for (String k : knowledgeGraph.keySet()) {
				//					System.out.println(k + ": " + knowledgeGraph.get(k));
				//				}
				//				System.out.println("-------------------");

			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	public String outputHTML() {
		LinkedHashMap<String, String> out = new LinkedHashMap<>();
		if ((out = getKnowledgeGraph()) == null) {
			if ((out = getCalcResult()) == null)
				return null;
		}

		StringBuilder output = new StringBuilder();

		output.append("<h2>" + out.get("Input") + "</h2>");

		output.append("<table>");

		for (String s : out.keySet()) {
			output.append("<tr>");
			output.append("<th>" + s + "</th>");
			output.append("<td>" + out.get(s) + "</td>");
			output.append("</tr>");
		}

		output.append("</table>");

		return output.toString();
	}

	/**
	 * For testing.
	 * @param args
	 */
	public static void main(String[] args) {
		WolframSearcher searcher = new WolframSearcher();
		searcher.search("Bill Gates");
		LinkedHashMap<String, String> calc = searcher.getCalcResult();
		LinkedHashMap<String, String> kg = searcher.getKnowledgeGraph();

		if (calc != null) {
			for (String s : calc.keySet()) {
				System.out.println(s + ": " + calc.get(s));
			}
		}

		if (kg != null) {
			for (String s : kg.keySet()) {
				System.out.println(s + ": " + kg.get(s));
			}
		}
	}
}
