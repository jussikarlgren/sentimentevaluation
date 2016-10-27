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
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.gavagai.jussiutil.Tone;

public class FragmentRestPolariser {

	Log logger = LogFactory.getLog(FragmentRestPolariser.class);

	private String fileName;
	private String outfile;

	int batchSize = 100;
	int antalprocessed;
	int antalbatches;
	int antaltriedbatches;

	protected static int BUCKET_SIZE = 200;
	protected static final String HOST = "https://api.gavagai.se";
	private static final String contentType = "application/json"; // ;charset=utf-8";

	protected String username;
	protected String password;
	private String host = HOST;
	protected String apiKey = "faaa43ef8f44b05e822f63bbe6adfd25";
	protected int bucketsize = BUCKET_SIZE;
	protected String language;
	protected Vector<String> bucket;

	private int thisunlabeled;

	private boolean debug;

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

	protected void setFilename(String filename) {
		this.fileName = filename;
	}

	protected void setOutFilename(String filename) {
		this.outfile = filename;
	}

	class Verifier implements HostnameVerifier {
		public boolean verify(String arg0, SSLSession arg1) {
			return true;   // mark everything as verified
		}
	}

	public void processBatch(FragmentPolarizationBatch request) throws MalformedURLException, IOException {
		String url = "https://"+host+"/v3/tonality?apiKey="+apiKey+"; //&language="+language;
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
		request.processResponse(response.toString(),false);
		//if (debug) System.out.println(response);
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void performBatchPolarisation() throws Exception {
		Writer out = new BufferedWriter(new FileWriter(new File(outfile)));
		Writer misses = new BufferedWriter(new FileWriter(new File(outfile+".misses")));
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
		ffn.put(Tone.PROFANITY, 0.5f);
		antalprocessed = 0;
		antalbatches = 0;
		antaltriedbatches = 0;
		FragmentPolarizationBatch request = new FragmentPolarizationBatch();
		request.setWeights(Tone.POSITIVITY,ff);
		request.setWeights(Tone.NEGATIVITY,ffn);
		request.setLanguage(language);
		for (String f : bucket) {
			antal++;
			String[] bits = f.split("\t");
			if (bits.length > 1) {
			request.addText(bits[0],bits[1]);
			j++;			
			if (j >= batchSize || antal >= bucket.size()) {
				try {
					antaltriedbatches++;
					processBatch(request);
					thisunlabeled += request.getUnlabeled();
					antalprocessed += request.getN();
					antalbatches++;
					request.classify();
				} catch (IOException e) {
					logger.error("no dice! - batch of "+j+" documents discarded: "+antal);
					logger.error(e.getMessage());
					logger.error("***************"+request.getPostJson());
					for (String m: request.getKeys()) {
						misses.write(m+"\n");
					}
					misses.flush();
				}
				request = new FragmentPolarizationBatch();
				request.setWeights(Tone.POSITIVITY,ff);
				request.setWeights(Tone.NEGATIVITY,ffn);
				request.setLanguage(language);
				j = 0;
			}
			}
		}
		logger.info("processed "+antalbatches+" batches out of "+antaltriedbatches);
		out.flush();
		out.close();
		misses.flush();
		misses.close();
	}
	
	private Vector<String> getTexts(File testFile) throws FileNotFoundException {
		Vector<String> vec = new Vector<String>();
		logger.debug("Assessment file scanner created "+testFile.getAbsolutePath());
		int i = 0;
		Scanner fileScanner = new Scanner(new BufferedInputStream(new FileInputStream(testFile)));
		while (fileScanner.hasNextLine()) {
			String fileLine = fileScanner.nextLine();
			vec.add(fileLine);
			i++;
		}
		fileScanner.close();
		logger.debug(i+" cases scanned.");
		bucket = vec;
		return vec;
	}


	public static void main(String[] args) throws IOException {
		String userid = "jussi";
		String password = "humle";
		String apiKey = "faaa43ef8f44b05e822f63bbe6adfd25";
		String host = "api.gavagai.se"; //rabbitconfig.getProperty("host", "api.gavagai.se");
		String language = "en";
		try {
			FragmentRestPolariser sbt = new FragmentRestPolariser();
			sbt.setUsername(userid);
			sbt.setPassword(password);
			sbt.setApiKey(apiKey);
			sbt.setHost(host);
			sbt.setBatchSize(50);
			sbt.setLanguage(language);
			sbt.setOutFilename("/Users/jussi/Desktop/gg.out");
			sbt.getTexts(new File("/bigdata/research/experiments/2014.10.gamergate/24.list"));
			sbt.performBatchPolarisation();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setBatchSize(int i) {
		this.batchSize = i;
	}		
}