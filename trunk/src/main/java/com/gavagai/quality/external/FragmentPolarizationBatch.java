package com.gavagai.quality.external;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.gavagai.jussiutil.ConfusionMatrix;
import com.gavagai.jussiutil.Tone;

public class FragmentPolarizationBatch {
	String tag = "text";
	private HashMap<String,String> texts  = new HashMap<String,String>();
	private HashMap<String,Tone> goldStandard = new HashMap<String,Tone>() ;
	private HashMap<String,Tone> prediction = new HashMap<String,Tone>();
	private HashMap<String,HashMap<Tone,Float>> scores = new HashMap<String,HashMap<Tone,Float>>();
	private HashMap<Tone,HashMap<Tone,Float>>  weights = new HashMap<Tone,HashMap<Tone,Float>>();
	private String language;

	public void setWeights(Tone s,HashMap<Tone,Float>  weights) {
		this.weights.put(s, weights);
	}
	public int setTexts(String[] textitems) {
		int n = texts.size();
		for (String t: textitems) {
			texts.put(tag+n, t);
			n++;
		}
		return n;
	}
	public int addText(String text) {
		int n = texts.size()+1;
		return addText(tag+n, text);	
	}	
	public int addText(String key, String text) {
		texts.put(key, text);
		return texts.size();	
	}	
	public int addText(String key, String text, Tone p) {
		goldStandard.put(key, p);
		return addText(key, text);	
	}	
	public void addScore(String key, Tone p, float f) {
		HashMap<Tone,Float> h;
		if (scores.containsKey(key)) {h = scores.get(key);} else {h = new HashMap<Tone,Float>();}
		h.put(p, f);
		scores.put(key,h);
	}
	public Collection<String> getTexts() {
		return texts.values();
	}
	
//	static public String byteToHex(byte b) {
//	      // Returns hex String representation of byte b
//	      char hexDigit[] = {
//	         '0', '1', '2', '3', '4', '5', '6', '7',
//	         '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
//	      };
//	      char[] array = { hexDigit[(b >> 4) & 0x0f], hexDigit[b & 0x0f] };
//	      return new String(array);
//	   }
	 
	public String getPostJson() throws UnsupportedEncodingException {
		//"&language=EN"
		//String json = "{\"documents\":[";
		String json = "{\"language\":\""+language.toLowerCase()+"\",\"texts\":[";
		int i = 0;
		for (Entry<String,String> e: texts.entrySet()) {
			String sb = e.getValue();

			sb = sb.replace("\\", "\\\\");
			sb = sb.replace("\"", "\\\"");
			sb = sb.replace("–", "-");
			sb = sb.replace("“","\\\"");
			sb = sb.replace("”","\\\"");
			sb = sb.replace("”","\\\"");
			sb = sb.replace("ó","o");
			sb = sb.replace("ñ","n");
			sb = sb.replace("á","a");
			sb = sb.replace("í","i");
			sb = sb.replace("ú","u");
			sb = sb.replace("é","e");
			sb = sb.replace("Ó","O");
			sb = sb.replace("Ñ","N");
			sb = sb.replace("Á","A");
			sb = sb.replace("Í","I");
			sb = sb.replace("Ú","U");
			sb = sb.replace("É","E");
			sb = sb.replace("ü","u");
			sb = sb.replace("Ü","u");
			sb = sb.replace("…","...");
			sb = sb.replace("¿","? ");
			sb = sb.replace("¡","! ");
			byte[] ptext = sb.getBytes("UTF-8");
			for (int ii=0; ii < ptext.length; ii++) {
				if (ptext[ii] < 0) {ptext[ii] = 32;}
			}	
			String value = new String(ptext,Charset.forName("UTF-8"));
			
//			System.out.println(sb);
//						for (byte b: ptext) {
////							System.out.println(b+"->"+byteToHex(b));
//						}
//						String value = new String(ptext, Charset.forName("UTF-16"));
//						System.out.println(sb +" -> " + value);
			if (i > 0) json += ",";
			json += "{\"body\":\""+value+"\",\"id\":\""+e.getKey()+"\"}";
			i++;
//			System.out.println(e.getValue());
//			System.out.println(sb);
//			System.out.println(value);
		}			
		json += "]}";
		return json;
	}
	public void setLanguage(String language) {
		this.language = language;
	}
	public int getN() {return texts.size();}

	public int getMisses(Tone target) {
		int n = 0;
		try {
			for (String key: texts.keySet()) {if (goldStandard.get(key).equals(target) && ! prediction.get(key).equals(target)) {n++;}}
		} catch (NullPointerException e) {

		}
		return n;
	}
	public int getMatches(Tone target) {
		int n = 0;
		try {
			for (String key: texts.keySet()) {if (goldStandard.get(key).equals(target) && prediction.get(key).equals(target)) {n++;}}
		} catch (NullPointerException e) {

		}
		return n;
	}
	public int getErrors(Tone target) {
		int n = 0;
		try {
			for (String key: texts.keySet()) {if (! goldStandard.get(key).equals(target) && prediction.get(key).equals(target)) {n++;}}
		} catch (NullPointerException e) {

		}	return n;
	}

	public Tone interpret (String tone) {
		//	if (tone.equals("NO_LABEL")) {return Sentiment.OTHER;} 
		Tone s;
		try {
			s = Tone.valueOf(tone.toUpperCase());
		} catch (IllegalArgumentException e) {
			Tone[] v = Tone.values();
			int i = (int) Math.floor(v.length*Math.random());
			s = v[i];
		}
		return s;
	}
	private int lopnummer;
	String jsonString = "";
	public String getJsonString() {return "{\"batchId\":\""+lopnummer+"\",\n"+"\"batch\":["+jsonString+"]}\n";}
	public ConfusionMatrix classify() throws IOException {
		ConfusionMatrix pp = new ConfusionMatrix(weights.size());
		for (String k: scores.keySet()) { // take every text we have scored
			Tone p = Tone.OTHER; // default
			float max = 0f;
			HashMap<Tone,Float> h = scores.get(k); // find the scores for all std tones for this text
			HashMap<Tone,Float> weightedScores = new HashMap<Tone,Float>();
			for (Tone target: weights.keySet()) { // take all the sentiments we are aiming for, in turn (POS & NEG, i guess)
				float thisscore = 0f;
				for (Tone s: h.keySet()) { 
					float w = 0f; // if no weight, discount the score for this tone 
					if (weights.get(target).containsKey(s)) w = weights.get(target).get(s); // the weight of this tone for this target sentiment
					thisscore += w*h.get(s);
					weightedScores.put(target, thisscore);
				}
				if (thisscore > max) {
					max = thisscore;
					p = target; // keep the largest score
				}
			}
			if (jsonString.length() > 0) {jsonString += ",\n";}
			boolean nailedIt = goldStandard.get(k).equals(p);
			String debugstring = "{";
			debugstring += "\"prediction\":\""+p+"\"";
			debugstring += ", ";
			debugstring += "\"goldstandard\":\""+goldStandard.get(k)+"\"";
			debugstring += ", ";
			debugstring += "\"nailedIt\":\""+nailedIt+"\"";
			debugstring += ", ";
			String kk = k.replace("\""," ");
			debugstring += "\"id\":\""+kk+"\"";
			debugstring += ", ";
			debugstring += "\"scores\":{";
			for (Tone s: h.keySet()) {
				debugstring += "\""+s+"\":\""+h.get(s)+"\", ";
			}	
			debugstring += "\"positiveAggregated\":\""+weightedScores.get(Tone.POSITIVITY)+"\""+", ";
			debugstring += "\"negativeAggregated\":\""+weightedScores.get(Tone.NEGATIVITY)+"\"";
			debugstring += "}";
			debugstring += ", ";
			String ssss = texts.get(k);
			ssss.replaceAll("\"", " ");
			debugstring += "\"text\":\""+ssss+"\"";				
			debugstring += "}";
			jsonString += debugstring+"\n";
			prediction.put(k, p); // put the best scoring sentiment in prediction
			pp.increment(goldStandard.get(k),p);
		}
		return pp;
	}
	public void processResponse(String response, boolean debug2) throws JsonParseException, JsonMappingException, IOException {
		String docid = "dummy";
		String tone = "notone";
		JsonFactory factory = new JsonFactory();
		JsonParser parser = factory.createParser(response);
		while (!parser.isClosed()) {
			// [{"uri":"text0","tonality":[{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":2.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0}]},{"uri":"text3","tonality":[{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0}]},{"uri":"text4","tonality":[{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0}]},{"uri":"text1","tonality":[{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0}]},{"uri":"text2","tonality":[{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":1.0},{"tone":"NO_LABEL","score":1.0},{"tone":"NO_LABEL","score":0.0},{"tone":"NO_LABEL","score":0.0}]}]
			JsonToken token;
			while (true) {
				token = parser.nextToken();
				if (token == null) break;
				if (JsonToken.FIELD_NAME.equals(token)) {
					String name = parser.getCurrentName();
					if (name.equals("documents")) continue;
					if (name.equals("tonality")) continue;
					token = parser.nextValue();
					String value = parser.getValueAsString();
					if (name.equals("id")) docid=value;
					if (name.equals("tone")) {
						tone=value;
					}
					if (name.equals("score")) {
						addScore(docid,interpret(tone),Float.parseFloat(value));
					}
				} 
			}
		}
	}
	public void addScore(String id, Tone p, Float f) {
		HashMap<Tone,Float> h = scores.get(id);
		h.put(p, f);
		scores.put(id, h);}

	public Map<Tone, Float> getScores(String id) {
		return scores.get(id);
	}
	public int getUnlabeled() {
		int n = 0;
		try {
			for (String key: texts.keySet()) {if (prediction.get(key).equals(Tone.OTHER)) {n++;}}
		} catch (NullPointerException e) {

		}
		return n;
	}
	public Set<String> getKeys() {
		return texts.keySet();
	}
	public void setN(int antaltriedbatches) {
		this.lopnummer = antaltriedbatches;
	}
}
