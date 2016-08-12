package com.iai.uima.analysis_component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.iai.uima.jcas.tcas.KeyPhraseAnnotation;
import com.iai.uima.jcas.tcas.KeyPhraseAnnotationDeprecated;
import com.iai.uima.jcas.tcas.KeyPhraseEvaluation;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;;

public class KeyPhraseEvaluationAnnotator extends JCasAnnotator_ImplBase {

	public static final String PARAM_MANUAL_KEYPHRASE_LOCATION = "manualKeyPhraseLocation";
	@ConfigurationParameter(name = PARAM_MANUAL_KEYPHRASE_LOCATION, mandatory = false)
	private String manualKeyPhraseLocation;

	public static final String PARAM_CUT_KEYPHRAES = "cutKeyPhrases";
	@ConfigurationParameter(name = PARAM_CUT_KEYPHRAES, mandatory = false)
	private boolean cutKeyPhrases;

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {

		HashMap<Integer, List<String>> extractedKeyPhrases = new HashMap<Integer, List<String>>();

		HashSet<String> manualKeyPhrases = new HashSet<String>();

		for (KeyPhraseAnnotation kp : JCasUtil.select(aJCas, KeyPhraseAnnotation.class))
			if (!(kp instanceof KeyPhraseAnnotationDeprecated)) {
				if (extractedKeyPhrases.containsKey(kp.getRank())) {
					extractedKeyPhrases.get(kp.getRank()).add(kp.getKeyPhrase());
				} else {
					extractedKeyPhrases.put(kp.getRank(), new ArrayList<String>());
					extractedKeyPhrases.get(kp.getRank()).add(kp.getKeyPhrase());
				}
			}
		for (List<String> keyPhrases : extractedKeyPhrases.values())
			Collections.sort(keyPhrases, new StingLengthComparator());

		DocumentMetaData meta = DocumentMetaData.get(aJCas);
		String baseUri = meta.getDocumentBaseUri();
		String docID = meta.getDocumentId().substring(0, meta.getDocumentId().lastIndexOf('.'));
		URI uri = null;
		try {
			uri = new URI(baseUri + "manual_keyphrases/" + docID + ".key");
		} catch (URISyntaxException e1) {
			e1.printStackTrace();
		}
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(new File(uri)));
			String line;
			while ((line = br.readLine()) != null) {
				manualKeyPhrases.add(line.toLowerCase());
			}
			br.close();
		} catch (FileNotFoundException e) {
			System.err.println("Keyphrase file " + uri + " could not be found");
			return;
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}

		HashSet<String> realKeyPhrases = new HashSet<String>();
		ArrayList<Integer> ranks = new ArrayList<Integer>(extractedKeyPhrases.keySet());
		Collections.sort(ranks);

		for (int i = 0; i < ranks.size(); i++) {
			int rank = ranks.get(i);
			List<String> keyPhrase = extractedKeyPhrases.get(rank);
			for (int j = 0; j < keyPhrase.size(); j++)
				if (!cutKeyPhrases || realKeyPhrases.size() <= manualKeyPhrases.size())
					realKeyPhrases.add(keyPhrase.get(j));
		}

		int found = 0;

		for (String manualKeyPhrase : manualKeyPhrases)
			for (String extractedKeyPhrase : realKeyPhrases)
				if (extractedKeyPhrase.contains(manualKeyPhrase))
					found++;

		KeyPhraseEvaluation annotation = new KeyPhraseEvaluation(aJCas);

		double precision = (double) found / manualKeyPhrases.size();
		double recall = (double) manualKeyPhrases.size() / realKeyPhrases.size();

		annotation.setPrecision(precision);
		annotation.setRecall(recall);
		annotation.setF1Score(precision * recall / (precision + recall));
		annotation.addToIndexes();
	}

	private class StingLengthComparator implements java.util.Comparator<String> {

		public int compare(String s1, String s2) {
			return s1.length() - s2.length();
		}
	}
}
