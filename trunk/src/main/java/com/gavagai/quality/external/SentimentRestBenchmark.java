package com.gavagai.quality.external;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.gavagai.jussiutil.StatsdLogger;
import com.gavagai.jussiutil.ConfusionMatrix;
import com.gavagai.jussiutil.PearsonsChiSquareTest;
import com.gavagai.jussiutil.TextWithTone;
import com.gavagai.jussiutil.Tone;

public class SentimentRestBenchmark {

	Log logger = LogFactory.getLog(SentimentRestBenchmark.class);

	ConfusionMatrix errors;
	Map<String, Integer> errorWords;
	Map<String, Integer> missedWords;
	Map<String, Integer> neverGuessedWords;
	Map<String, PearsonsChiSquareTest> chiSquare;

	String testIdentifier;
	String testFileName;

	int thisright;
	int thiswrong;
	int neverguessed;
	int totalGoldPos;
	int totalGoldNeg;
	int batchSize = 100;
	int antalprocessed;
	int antalbatches;
	int antaltriedbatches;

	protected static int BUCKET_SIZE = 200;
	protected static final String HOST = "https://api.gavagai.se";
	private static final String contentType = "application/json"; // ;charset=utf-8";

	protected String testdirectory;
	protected String username;
	protected String password;
	private String host = HOST;
	protected String apiKey = "faaa43ef8f44b05e822f63bbe6adfd25";
	protected int bucketsize = BUCKET_SIZE;
	protected String language;
	protected Vector<TextWithTone> bucket;

	private int thisunlabeled;

	private boolean debug;

	private String outfile;

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	protected void setPassword(String pw) {
		this.password = pw;
	}

	protected void setUsername(String uid) {
		this.username = uid;
	}
	protected void setBucketSize(int i) {
		this.bucketsize = i;
	}

	protected void setLanguage(String language) {
		this.language = language;
	}

	protected void setTestdirectory(String testdirectory) {
		this.testdirectory = testdirectory;
	}

	protected void setTestIdentifier(String testIdentifier) {
		this.testIdentifier = testIdentifier;
	}

	protected void setTestFilename(String filename) {
		this.testFileName = filename;
	}

	class Verifier implements HostnameVerifier {
		public boolean verify(String arg0, SSLSession arg1) {
			return true;   // mark everything as verified
		}
	}

	public void processBatch(FragmentPolarizationBatch request, boolean debug2) throws MalformedURLException, IOException {
		String url = "https://"+host+"/v3/tonality?apiKey="+apiKey;
//		if (debug) System.out.println(url);
		HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
		String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary((username+":"+password).getBytes());
		con.setRequestProperty ("Authorization", basicAuth);	
		con.setRequestMethod("POST");
		con.addRequestProperty("Content-type", contentType);
		con.setHostnameVerifier(new Verifier());
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		String textpackage = request.getPostJson();
		wr.writeBytes(textpackage);
		wr.flush();
		wr.close();
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		request.processResponse(response.toString(),debug2);
		//if (debug) System.out.println(response);
	}

	public void setHost(String host) {
		this.host = host;
	}

	public float getResultLenient() {
		if (thisright+thiswrong > 0) {
			return ((float) thisright / (float) (thisright+thiswrong));//bucket.size());
		} else { 
			return 0f;
		}
	}
	public float getResultStrict() {
		if (antalprocessed > 0) {
			return ((float) thisright / (float) antalprocessed);//bucket.size());
		} else { 
			return 0f;
		}
	}

	protected void setOutFilename(String filename) {
		this.outfile = filename;
	}

	public void performSentimentPolarizationTest() throws Exception {
		logger.info("About to run "+testIdentifier+" sentiment polarization tests over Rest API with "+bucketsize+" items.");
//		Writer out = null;
		// out = new BufferedWriter(new FileWriter(new File("/home/jussi/Desktop/sbm."+testIdentifier+"."+algorithm+".misses")));
		Writer out = new BufferedWriter(new FileWriter(new File(outfile)));
		int antal = 0;
		int j = 0;
		HashMap<Tone,Float> ff = new HashMap<Tone,Float>();
		ff.put(Tone.POSITIVITY,1f);
		ff.put(Tone.NEGATIVITY,-1f);
		ff.put(Tone.LOVE,1f);
		ff.put(Tone.HATE,-1f);
		ff.put(Tone.FEAR,-1f);
		ff.put(Tone.DESIRE,1f);
		ff.put(Tone.BORING, -0.5f);
		ff.put(Tone.VIOLENCE,-1f);
		ff.put(Tone.SKEPTICISM, -0.5f);
		HashMap<Tone,Float> ffn = new HashMap<Tone,Float>();
		ffn.put(Tone.POSITIVITY,-1f);
		ffn.put(Tone.NEGATIVITY,1f);
		ffn.put(Tone.LOVE,-1f);
		ffn.put(Tone.HATE,1f);
		ffn.put(Tone.FEAR,1f);
		ffn.put(Tone.BORING, 0.5f);
		ffn.put(Tone.VIOLENCE,1f);
		ffn.put(Tone.SKEPTICISM, 0.5f);
		antalprocessed = 0;
		antalbatches = 0;
		antaltriedbatches = 0;
		FragmentPolarizationBatch request = new FragmentPolarizationBatch();
		request.setWeights(Tone.POSITIVITY,ff);
		request.setWeights(Tone.NEGATIVITY,ffn);
		request.setLanguage(language);
		request.setN(antaltriedbatches);
		errors = new ConfusionMatrix(3);
		out.write("[\n");
		String comma = "";
		for (TextWithTone f : bucket) {
			antal++;
			request.addText("text"+antal,  f.getText(), f.getSentiment());
			j++;			
			if (j >= batchSize || antal >= bucket.size()) {
				try {
					antaltriedbatches++;
					processBatch(request,debug);
					errors.addIn(request.classify());
					out.write(comma); //fult men vafan orka buffra dessa stora jsoner
					out.write(request.getJsonString());
					thisright += request.getMatches(Tone.NEGATIVITY)+request.getMatches(Tone.POSITIVITY);
					//					thiswrong += request.getMisses(Tone.NEGATIVE)+request.getMisses(Tone.POSITIVE);
					thiswrong += request.getErrors(Tone.NEGATIVITY)+request.getErrors(Tone.POSITIVITY);
					thisunlabeled += request.getUnlabeled();
					antalprocessed += request.getN();
					antalbatches++;
					comma = ",\n";
				} catch (IOException e) {
					logger.info("no dice - batch of "+j+" documents discarded");
					logger.info(e.getMessage());
					logger.info("***************"+request.getPostJson());
				}
				request = new FragmentPolarizationBatch();
				request.setWeights(Tone.POSITIVITY,ff);
				request.setWeights(Tone.NEGATIVITY,ffn);
				request.setLanguage(language);
				request.setN(antaltriedbatches);
				j = 0;
			}
		}
		out.write("]\n");
		logger.info("processed "+antalbatches+" batches out of "+antaltriedbatches);
		logger.info(thisright + " correct answers " + thiswrong + " wrong and "+thisunlabeled+" unlabeled answers out of " + antalprocessed + " processed documents from " + bucket.size());
		logger.info(errors);
		//		fixChiSquareExpectedValues();
		//		out.println("Top words leading to false classification: " + getErrorWords(30)+"\n");
		//		out.println("Top missing words: " + getMissedWords(30)+"\n");
		//		out.println("Top missing words from non-classified utterances: " + getMissedWordsFromNeverGuessed(30)+"\n");
		//		out.println("Top chi square values: " + getTopChiSquareValues(30)+"\n");
				out.close();
	}
	static public String byteToHex(byte b) {
	      // Returns hex String representation of byte b
	      char hexDigit[] = {
	         '0', '1', '2', '3', '4', '5', '6', '7',
	         '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	      };
	      char[] array = { hexDigit[(b >> 4) & 0x0f], hexDigit[b & 0x0f] };
	      return new String(array);
	   }
	protected void fixChiSquareExpectedValues() {
		//Set expected chi square counts
		double posRatio = (totalGoldPos) / ((double) totalGoldNeg + totalGoldPos);
		double negRatio = 1 - posRatio;

		for (String word : chiSquare.keySet()) {
			int totalObserved = 0;
			for (String sentiment : chiSquare.get(word).getColumns()) {
				totalObserved += chiSquare.get(word).getObserved(sentiment);
			}

			for (String sentiment : chiSquare.get(word).getColumns()) {
				chiSquare.get(word).setExpected(sentiment, totalObserved * posRatio);
				chiSquare.get(word).setExpected(sentiment, totalObserved * negRatio);
			}
		}
	}

	//72      0 13 0 0 0 Negativ       @kjje misstänkte det, galet dåligt @3sverige men inte superviktigt för mig.
	private Vector<TextWithTone> getHomeGrownAssessments(File testFile) throws FileNotFoundException {
		Vector<TextWithTone> vec = new Vector<TextWithTone>();
		logger.debug("Assessment file scanner created "+testFile.getAbsolutePath());
		int i = 0;
		Scanner fileScanner = new Scanner(new BufferedInputStream(new FileInputStream(testFile)));
		while (fileScanner.hasNextLine()) {
			String fileLine = fileScanner.nextLine();
			String bits[] = fileLine.split("\t");
			if (bits.length > 3) {
				TextWithTone t = new TextWithTone(Tone.constrain(bits[2]), bits[3]);
				vec.add(t);
				i++;
			}
		}
		fileScanner.close();
		logger.debug(i+" cases scanned.");
		return vec;
	}

	//170438318453030912	EN	scrapindustry	RL2012E04	Scrapmonster news: Deficits to develop in lead, zinc by 2014: Barclays: Barclays looks for the lead and zinc mar... bit.ly/wMyIoI	yes	positive	zinc market	no
	private Vector<TextWithTone> getRepLabFragments(File testFile) throws FileNotFoundException {
		Vector<TextWithTone> vec = new Vector<TextWithTone>();
		logger.info("Replab fragment file scanner created "+testFile.getAbsolutePath());
		FileInputStream fis = new FileInputStream(testFile);
		Scanner fileScanner = new Scanner(new BufferedInputStream(fis));
		while (fileScanner.hasNextLine()) {
			String fileLine = fileScanner.nextLine();
			String bits[] = fileLine.split("\t");
			if (bits.length > 5) {
//				String normt = Normalizer.normalize(bits[4], Normalizer.Form.NFKD);
//				if (! Normalizer.isNormalized(bits[4], Normalizer.Form.NFKD)) {System.err.println("************"+bits[4]);
//				System.err.println("************"+normt);
//				}
//				System.err.println(bits[4]+" "+normt);
				TextWithTone t = new TextWithTone(bits[6], bits[4]);
				vec.add(t);
			}
		}
		return vec;
	}
// I'm tired of having to suspend my other lines on @VerizonWireless just to keep my unlimited data on my only line! @TMobile help! @JohnLegere ; negative
	private Vector<TextWithTone> getMonkeyLearnFragments(File testFile) throws FileNotFoundException {
		Vector<TextWithTone> vec = new Vector<TextWithTone>();
		logger.info("Monkeylearn fragment file scanner created "+testFile.getAbsolutePath());
		FileInputStream fis = new FileInputStream(testFile);
		Scanner fileScanner = new Scanner(new BufferedInputStream(fis));
		while (fileScanner.hasNextLine()) {
			String fileLine = fileScanner.nextLine();
			String bits[] = new String[2];
			bits = fileLine.split(";");
			if (bits.length > 1) {
				vec.add(new TextWithTone(bits[1].replace(" ", ""), bits[0]));
			}
		}
		fileScanner.close();
		return vec;
	}

	private Vector<TextWithTone> getFragments(File testFile) throws FileNotFoundException {
		Vector<TextWithTone> vec = new Vector<TextWithTone>();
		Scanner fileScanner = new Scanner(new BufferedInputStream(
				new FileInputStream(testFile)));
		logger.debug("File scanner created");
		while (fileScanner.hasNextLine()) {
			String fileLine = fileScanner.nextLine();
			String bits[] = new String[2];
			bits = fileLine.split("\t");
			if (bits.length > 1
					&& Tone.translate(bits[0]) != Tone.NEUTRAL) {
				vec.add(new TextWithTone(bits[0], bits[1]));
			}
		}
		fileScanner.close();
		return vec;
	}

	private Vector<TextWithTone> getStanfordSentences(File testFile) throws FileNotFoundException {
		Vector<TextWithTone> vec = new Vector<TextWithTone>();
		Scanner fileScanner = new Scanner(new BufferedInputStream(
				new FileInputStream(testFile)));
		logger.debug("File scanner created");
		String fileLine = "";
		while (fileScanner.hasNextLine()) {
			fileLine = fileScanner.nextLine();
			String bits[] = new String[2];
			bits = fileLine.split("\t");
			if (bits.length > 1) {
				float score = Float.parseFloat(bits[1]);
				if (score > 0.6) {vec.add(new TextWithTone(Tone.POSITIVITY,bits[0]));}
				if (score < 0.4) {vec.add(new TextWithTone(Tone.NEGATIVITY,bits[0]));}
			}
		}
		logger.info(fileLine);
		fileScanner.close();
		return vec;
	}

	private Vector<TextWithTone> getTxts(Tone s, File directory,int targetSize)
			throws FileNotFoundException {
		Vector<File> vec = new Vector<File>();
		logger.debug("Reading files from "+directory);
		Collections.addAll(vec, directory.listFiles());
		Vector<File> lilvec = prune(vec,bucketsize);
		Vector<TextWithTone> resvec = new Vector<TextWithTone>(bucketsize);
		for (File testFile : lilvec) {
			if (testFile.isFile()) {
				Scanner fileScanner = new Scanner(new BufferedInputStream(
						new FileInputStream(testFile)));
				String txt = "";
				logger.debug("File scanner created: " + testFile);
				while (fileScanner.hasNextLine()) {
					txt += fileScanner.nextLine();
				}
				resvec.add(new TextWithTone(s, txt));
				fileScanner.close();
			}
		}
		return resvec;
	}

	private List<Entry<String, Double>> getTopChiSquareValues(int top) {
		Map<String, Double> wordsChiPairing = new HashMap<String, Double>();
		for (String word : chiSquare.keySet()) {
			wordsChiPairing.put(word, chiSquare.get(word).getTestStatistic());
		}

		SortedSet<Map.Entry<String, Double>> chiSquareList = entriesSortedByDoubles(wordsChiPairing);
		int lastIndex = top >= chiSquareList.size() ? chiSquareList.size()-1 : top-1;
		if (lastIndex < 0) lastIndex = 0;

		return new ArrayList<Entry<String, Double>>(chiSquareList).subList(0, lastIndex);
	}

	public List<Entry<String, Integer>> getMissedWords(int top) {
		SortedSet<Map.Entry<String, Integer>> missedList = entriesSortedByIntegers(missedWords);
		int lastIndex = top >= missedList.size() ? missedList.size()-1 : top-1;
		if (lastIndex < 0) lastIndex = 0;

		return new ArrayList<Entry<String, Integer>>(missedList).subList(0, lastIndex);
	}

	public List<Entry<String, Integer>> getMissedWordsFromNeverGuessed(int top) {
		SortedSet<Map.Entry<String, Integer>> neverGuessedList = entriesSortedByIntegers(neverGuessedWords);
		int lastIndex = top >= neverGuessedList.size() ? neverGuessedList.size()-1 : top-1;
		if (lastIndex < 0) lastIndex = 0;

		return new ArrayList<Entry<String, Integer>>(neverGuessedList).subList(0, lastIndex);
	}

	public List<Entry<String, Integer>> getErrorWords(int top) {
		SortedSet<Map.Entry<String, Integer>> errorList = entriesSortedByIntegers(errorWords);
		if (errorList.size() < 1) {return null;}
		return new ArrayList<Entry<String, Integer>>(errorList).subList(0, 
				top >= errorList.size() ? errorList.size()-1 : top-1);
	}

	private SortedSet<Map.Entry<String,Integer>> entriesSortedByIntegers(Map<String,Integer> map) {
		SortedSet<Map.Entry<String,Integer>> sortedEntries = new TreeSet<Map.Entry<String,Integer>>(
				new Comparator<Map.Entry<String,Integer>>() {
					@Override public int compare(Map.Entry<String,Integer> e1, Map.Entry<String,Integer> e2) {
						int res = -e1.getValue().compareTo(e2.getValue());
						return res != 0 ? res : -1;
					}
				}
				);
		sortedEntries.addAll(map.entrySet());
		return sortedEntries;
	}

	private SortedSet<Map.Entry<String,Double>> entriesSortedByDoubles(Map<String,Double> map) {
		SortedSet<Map.Entry<String,Double>> sortedEntries = new TreeSet<Map.Entry<String,Double>>(
				new Comparator<Map.Entry<String,Double>>() {
					@Override public int compare(Map.Entry<String,Double> e1, Map.Entry<String,Double> e2) {
						int res = -e1.getValue().compareTo(e2.getValue());
						return res != 0 ? res : -1;
					}
				}
				);
		sortedEntries.addAll(map.entrySet());
		return sortedEntries;
	}

	//	public Vector<TextWithSentiment> getTexts(String testIdentifier, String testdirectory, String testFileName, int bucketsize) throws IOException {
	public void getTexts() throws IOException {
		bucket = new Vector<TextWithTone>();
		if (testIdentifier.equals("oscar")) {
			File sentimentPolarizationTestDataDirFile = new File(testdirectory+testFileName);
			bucket = getFragments(sentimentPolarizationTestDataDirFile);
		} else if (testIdentifier.equals("monkeylearn")) {
				File sentimentPolarizationTestDataDirFile = new File(testdirectory+testFileName);
				bucket = getMonkeyLearnFragments(sentimentPolarizationTestDataDirFile);
		} else if (testIdentifier.equals("panglee")) {
			bucket = new Vector<TextWithTone>();
			bucket.addAll(getTxts(Tone.POSITIVITY, new File(testdirectory + "pos"),bucketsize));
			bucket.addAll(getTxts(Tone.NEGATIVITY, new File(testdirectory + "neg"),bucketsize));
		} else if (testIdentifier.equals("tre")) {
			File treFile = new File(testFileName);
			bucket = getHomeGrownAssessments(treFile);
		} else if (testIdentifier.equals("stanford")) {
			File file = new File(testdirectory+testFileName);
			bucket = getStanfordSentences(file);
		} else {
			bucket = getRepLabFragments(new File(testdirectory+testFileName));
		}
		bucket = prune(bucket, bucketsize);
	}

	protected static Vector prune(Vector bigbucket,
			int targetsize) {
		if (bigbucket.size() < targetsize) {
			return bigbucket;
		} else {
			Vector smallbucket = new Vector(
					targetsize);
			while (smallbucket.size() < targetsize && bigbucket.size() > targetsize - smallbucket.size()) {
				Object d = bigbucket.remove(((int) Math.round(Math
						.random() * (bigbucket.size() - 1))));
				smallbucket.add(d);
			}
			return smallbucket;
		}
	}

	public static void runTestClient() throws IOException {
		SentimentRestBenchmark rr = new SentimentRestBenchmark();
		FragmentPolarizationBatch request = new FragmentPolarizationBatch();
		request.setLanguage("en");
		String apiKey = "faaa43ef8f44b05e822f63bbe6adfd25";
		String url = "https://api.gavagai.se/v3/tonality?apiKey=faaa43ef8f44b05e822f63bbe6adfd25&language=EN";
		//		String url = "https://ethersource.gavagai.se/ethersource/rest/v2/tonality?apiKey="+apiKey+"&language=EN"; //+language;
		URL obj = new URL(url);
		HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
		String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary("jussi:humle".getBytes());
		con.setRequestProperty ("Authorization", basicAuth);	
		con.setRequestMethod("POST");
		con.addRequestProperty("Content-type", contentType);
		con.setHostnameVerifier(rr.new Verifier());

		request.addText("text0", "oh, happy day joy happiness and love for all");
		request.addText("text1","terrible and awful this is a sad and horrible thing");
		request.addText("text2","i am afraid for what might happen");
		request.addText("text3","so hot so sexy");
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		String textpackage = request.getPostJson();
		//		textpackage = "[{\"body\":\"oh, happy day joy happiness and love for all\",\"uri\":\"1\"},{\"body\":\"terrible and awful this is a sad and horrible thing\",\"uri\":\"2\"},{\"body\":\"i am afraid for what might happen\",\"uri\":\"3\"},{\"body\":\"so hot so sexy\",\"uri\":\"4\"}]";
		wr.writeBytes(textpackage);
		wr.flush();
		wr.close();
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'POST' request to URL : " + url);
		System.out.println("Post package : " + textpackage);
		System.out.println("Response Code : " + responseCode);
		for (Entry<String, List<String>> header : con.getHeaderFields().entrySet()) {
			System.out.println(header.getKey() + "=" + header.getValue());
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		System.out.println(response.toString());
	}

	public void resetScores() {
		errorWords = new HashMap<String, Integer>();
		missedWords = new HashMap<String, Integer>();
		neverGuessedWords = new HashMap<String, Integer>();		
		chiSquare = new HashMap<String, PearsonsChiSquareTest>();
		thisright = 0;
		thiswrong = 0;
		thisunlabeled = 0;
		neverguessed = 0;
		totalGoldPos = 0;
		totalGoldNeg = 0;	
		errors = new ConfusionMatrix(Tone.values());
	}

	public static void logToStatsd(StatsdLogger logger, String tag,float r) {
		try {
			logger.logGauge(tag, (int) (r*1000));
			logger.logCounter(tag, (int) (r*1000));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
//		SentimentRestBenchmark.runTestClient();
//	}
//	private void ingetalls(){
		Log logger = LogFactory.getLog(SentimentRestBenchmark.class);

		System.setProperty("com.gavagai.rabbit.utils.StatsdLogger.enablelogger","true");
		StatsdLogger statsdLogger = new StatsdLogger(System.getProperty("com.gavagai.rabbit.utils.StatsdLogger.serverName","localhost"),Integer.parseInt(System.getProperty("com.gavagai.rabbit.utils.StatsdLogger.serverPort","33444")),true);
		logger.info("Statsd logger created: "+statsdLogger.toString());

		Properties rabbitconfig = new Properties();
		String sconfigfilename = "restsentimentbenchmark.config";
		String urtima = "";
		try {	
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			InputStream propertiesStream = classLoader.getResourceAsStream(sconfigfilename);
			rabbitconfig.load(propertiesStream);
			propertiesStream.close();
		} catch (FileNotFoundException e1) {
			System.err.println(e1+": "+sconfigfilename);
		} catch (IOException e1) {
			System.err.println(e1+": "+sconfigfilename);
		} catch (NullPointerException e1) {
			System.err.println(e1+": "+sconfigfilename);
			urtima="experimental";
		}

		String testDirectory = rabbitconfig.getProperty("testdirectory","/bigdata/evaluation/sentiment.polarization/");
		int replabbucketsize = Integer.parseInt(rabbitconfig.getProperty("replabbucketsize","1000"));
		int esreplabbucketsize = Integer.parseInt(rabbitconfig.getProperty("esreplabbucketsize","1000"));
		int oscarbucketsize = Integer.parseInt(rabbitconfig.getProperty("oscarbucketsize","1000"));
		int stanfordbucketsize = Integer.parseInt(rabbitconfig.getProperty("stanfordbucketsize","1000"));
		int monkeybucketsize = Integer.parseInt(rabbitconfig.getProperty("monkeybucketsize","1800"));
//		int pangleebucketsize = Integer.parseInt(rabbitconfig.getProperty("pangleebucketsize","1000"));
		String userid = rabbitconfig.getProperty("userid", "jussi");
		String password = rabbitconfig.getProperty("password", "humle");
		String apiKey = rabbitconfig.getProperty("apikey","faaa43ef8f44b05e822f63bbe6adfd25");
		String host = "api.gavagai.se"; //rabbitconfig.getProperty("host", "api.gavagai.se");
		String language = "en";
		boolean debug = false;
		if (debug) {
		urtima = "experimental";
		replabbucketsize = 0;
		oscarbucketsize = 0;
		stanfordbucketsize = 100;
		monkeybucketsize = 0;
		esreplabbucketsize = 0;
		}
		float o1 = 0, r1 = 0, s1 = 0, e1 = 0, m1 = 0;
		float o2 = 0, r2 = 0, s2 = 0, e2 = 0, m2 = 0;
		String testIdentifier = "";
		if (oscarbucketsize > 0) {
			testIdentifier = "oscar";
			try {
				language = "en";
				SentimentRestBenchmark sbt = new SentimentRestBenchmark();
				sbt.setUsername(userid);
				sbt.setPassword(password);
				sbt.setApiKey(apiKey);
				sbt.setHost(host);
				sbt.setBatchSize(100);
				sbt.setDebug(debug);
				sbt.setLanguage(language.toUpperCase());
				sbt.setTestIdentifier(testIdentifier);
				sbt.setTestdirectory(testDirectory+language+"/"+testIdentifier+"/");
				sbt.setTestFilename(rabbitconfig.getProperty("oscartestfilename","rvw-en-finegrained.txt"));
				sbt.setBucketSize(oscarbucketsize);
				sbt.getTexts();
				sbt.setOutFilename("/home/jussi/Desktop/"+testIdentifier+".output");
				sbt.performSentimentPolarizationTest();
				o1 = sbt.getResultStrict();
				o2 = sbt.getResultLenient();
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+urtima,o2);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+"strict"+urtima,o1);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+urtima+"baseline",0.6f); // oscar's dissertation approximate figure
				logger.info("Logging "+o2+" to "+"quality.sentiment.polarization."+testIdentifier+urtima);
				sbt.resetScores();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (monkeybucketsize > 0) {
			testIdentifier = "monkeylearn";
			try {
				language = "en";
				SentimentRestBenchmark sbt = new SentimentRestBenchmark();
				sbt.setUsername(userid);
				sbt.setPassword(password);
				sbt.setApiKey(apiKey);
				sbt.setHost(host);
				sbt.setBatchSize(100);
				sbt.setDebug(debug);
				sbt.setLanguage(language.toUpperCase());
				sbt.setTestIdentifier(testIdentifier);
				sbt.setTestdirectory(testDirectory+language+"/"+testIdentifier+"/");
				sbt.setTestFilename(rabbitconfig.getProperty("monkeylearntestfilename","monkeylearn.tsv"));
				sbt.setBucketSize(monkeybucketsize);
				sbt.getTexts();
				sbt.setOutFilename("/home/jussi/Desktop/"+testIdentifier+".output");
				sbt.performSentimentPolarizationTest();
				m1 = sbt.getResultStrict();
				m2 = sbt.getResultLenient();
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+urtima,m2);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+"strict"+urtima,m1);
				logger.info("Logging "+m2+" to "+"quality.sentiment.polarization."+testIdentifier+urtima);
				sbt.resetScores();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (replabbucketsize > 0) {
			testIdentifier = "replab";
			try {
				language = "en";
				SentimentRestBenchmark sbt = new SentimentRestBenchmark();
				sbt.setUsername(userid);
				sbt.setPassword(password);
				sbt.setApiKey(apiKey);
				sbt.setDebug(debug);
				sbt.setHost(host);
				sbt.setBatchSize(100);
				sbt.setLanguage(language.toUpperCase());
				sbt.setTestIdentifier(testIdentifier);
				sbt.setTestdirectory(testDirectory+language+"/"+testIdentifier+"/");
				sbt.setTestFilename(rabbitconfig.getProperty("replabtestfilename","replab2013-noquotes.txt"));
				sbt.setOutFilename("/home/jussi/Desktop/"+language+testIdentifier+".output");
				sbt.setBucketSize(replabbucketsize);
				sbt.getTexts();
				sbt.performSentimentPolarizationTest();
				r1 = sbt.getResultStrict();
				r2 = sbt.getResultLenient();
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+"strict"+urtima,r1);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+urtima,r2);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+urtima+"baseline",0.686f); // human agreement baseline from Replab 2013 ovw paper 
				logger.info("Logging "+r2+" to "+"quality.sentiment.polarization."+testIdentifier+urtima);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (esreplabbucketsize > 0) {
			testIdentifier = "replab";
			try {
				language = "es";
				SentimentRestBenchmark sbt = new SentimentRestBenchmark();
				sbt.setUsername(userid);
				sbt.setPassword(password);
				sbt.setApiKey(apiKey);
				sbt.setDebug(debug);
				sbt.setHost(host);
				sbt.setBatchSize(100);
				sbt.setLanguage(language.toUpperCase());
				sbt.setTestIdentifier(testIdentifier);
				sbt.setTestdirectory(testDirectory+language+"/"+testIdentifier+"/");
				sbt.setTestFilename(rabbitconfig.getProperty("esreplabtestfilename","replab2013-es.txt"));
//				sbt.setTestFilename(rabbitconfig.getProperty("esreplabtestfilename","b"));
				sbt.setOutFilename("/home/jussi/Desktop/"+language+testIdentifier+".output");
				sbt.setBucketSize(esreplabbucketsize);
				sbt.getTexts();
				sbt.performSentimentPolarizationTest();
				e1 = sbt.getResultStrict();
				e2 = sbt.getResultLenient();
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+language+testIdentifier+"strict"+urtima,e1);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+language+testIdentifier+urtima,e2);
				logger.info("Logging "+e2+" to "+"quality.sentiment.polarization."+language+testIdentifier+urtima);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (stanfordbucketsize > 0) {
			testIdentifier = "stanford";
			try {
				language = "en";
				SentimentRestBenchmark sbt = new SentimentRestBenchmark();
				sbt.setUsername(userid);
				sbt.setPassword(password);
				sbt.setApiKey(apiKey);
				sbt.setDebug(debug);
				sbt.setHost(host);
				sbt.setBatchSize(100);
				sbt.setLanguage(language.toUpperCase());
				sbt.setTestIdentifier(testIdentifier);
				sbt.setTestdirectory(testDirectory+language+"/"+testIdentifier+"/");
				sbt.setTestFilename(rabbitconfig.getProperty("stanfordtestfilename","sentences_clean.txt"));
				sbt.setOutFilename("/home/jussi/Desktop/"+testIdentifier+".output");
				sbt.setBucketSize(stanfordbucketsize);
				sbt.getTexts();
				sbt.performSentimentPolarizationTest();
				s1 = sbt.getResultStrict();
				s2 = sbt.getResultLenient();
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+"strict"+urtima,s1);
				logToStatsd(statsdLogger,"quality.sentiment.polarization."+testIdentifier+urtima,s2);
				logger.info("Logging "+s2+" to "+"quality.sentiment.polarization."+testIdentifier+urtima);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println(" o:"+o1+" r:"+r1+" s:"+s1+" e:"+e1+" m:"+m1);
		System.out.println(" o:"+o2+" r:"+r2+" s:"+s2+" e:"+e2+" m:"+m2);
	}

	private void setDebug(boolean debug) {
		this.debug = debug;
	}

	private void setBatchSize(int i) {
		this.batchSize = i;
	}		
}
