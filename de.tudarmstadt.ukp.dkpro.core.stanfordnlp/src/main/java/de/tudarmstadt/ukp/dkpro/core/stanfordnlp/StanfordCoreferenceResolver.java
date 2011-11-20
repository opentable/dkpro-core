/*******************************************************************************
 * Copyright 2011
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl-3.0.txt
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.stanfordnlp;

import static org.uimafit.util.JCasUtil.select;
import static org.uimafit.util.JCasUtil.selectCovered;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceChain;
import de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceLink;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.ROOT;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.util.TreeUtils;
import edu.stanford.nlp.dcoref.Constants;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.dcoref.MentionExtractor;
import edu.stanford.nlp.dcoref.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.dcoref.SieveCoreferenceSystem;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap.Key;

/**
 * @author Richard Eckart de Castilho
 */
public class StanfordCoreferenceResolver
	extends JCasAnnotator_ImplBase
{
	public static final String PARAM_SIEVES = "sieves";
	@ConfigurationParameter(name = PARAM_SIEVES, defaultValue = Constants.SIEVEPASSES, mandatory = true)
	private String sieves;

	public static final String PARAM_SCORE = "score";
	@ConfigurationParameter(name = PARAM_SCORE, defaultValue = "false", mandatory = true)
	private boolean score;

	public static final String PARAM_POSTPROCESSING = "postprocessing";
	@ConfigurationParameter(name = PARAM_POSTPROCESSING, defaultValue = "false", mandatory = true)
	private boolean postprocessing;

	public static final String PARAM_MAXDIST = "maxDist";
	@ConfigurationParameter(name = PARAM_MAXDIST, defaultValue = "-1", mandatory = true)
	private int maxdist;

	private MentionExtractor mentionExtractor;
	private SieveCoreferenceSystem corefSystem;

	@Override
	public void initialize(UimaContext aContext)
		throws ResourceInitializationException
	{
		super.initialize(aContext);

		Properties props = new Properties();
		props.setProperty(Constants.SIEVES_PROP, sieves);
		props.setProperty(Constants.SCORE_PROP, String.valueOf(score));
		props.setProperty(Constants.POSTPROCESSING_PROP, String.valueOf(postprocessing));
		props.setProperty(Constants.MAXDIST_PROP, String.valueOf(maxdist));
		props.setProperty(Constants.REPLICATECONLL_PROP, "false");
		props.setProperty(Constants.CONLL_SCORER, Constants.conllMentionEvalScript);

		try {
			corefSystem = new SieveCoreferenceSystem(props);
			mentionExtractor = new MentionExtractor(corefSystem.dictionaries(),
					corefSystem.semantics());
		}
		catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void process(JCas aJCas)
		throws AnalysisEngineProcessException
	{
		List<Tree> trees = new ArrayList<Tree>();
	    List<CoreMap> sentences = new ArrayList<CoreMap>();
	    List<List<CoreLabel>> sentenceTokens = new ArrayList<List<CoreLabel>>();
		for (ROOT root : select(aJCas, ROOT.class)) {
			// Copy all relevant information from the tokens
			List<CoreLabel> tokens = new ArrayList<CoreLabel>();
			for (Token token : selectCovered(Token.class, root)) {
				CoreLabel t = new CoreLabel();
				t.set(TokenKey.class, token);
				t.setOriginalText(token.getCoveredText());
				t.setWord(token.getCoveredText());
				t.setBeginPosition(token.getBegin());
				t.setEndPosition(token.getEnd());
				if (token.getLemma() != null) {
					t.setLemma(token.getLemma().getValue());
				}
				if (token.getPos() != null) {
					t.setTag(token.getPos().getPosValue());
				}
				List<NamedEntity> nes = selectCovered(NamedEntity.class, token);
				if (nes.size() > 0) {
					t.setNER(nes.get(0).getValue());
				}
				else {
					t.setNER("O");
				}
				tokens.add(t);
			}
			sentenceTokens.add(tokens);
			
			// deep copy of the tree. These are modified inside coref!
			Tree treeCopy = TreeUtils.createStanfordTree(root).treeSkeletonCopy();
			trees.add(treeCopy);

			// Build the sentence
			CoreMap sentence = new CoreLabel();			
			sentence.set(TreeAnnotation.class, treeCopy);
			sentence.set(TokensAnnotation.class, tokens);
			sentence.set(RootKey.class, root);
			sentences.add(sentence);
			
			// merge the new CoreLabels with the tree leaves
			MentionExtractor.mergeLabels(treeCopy, tokens);
			MentionExtractor.initializeUtterance(tokens);
		}
		
		Annotation document = new Annotation(aJCas.getDocumentText());
		document.set(SentencesAnnotation.class, sentences);

		// extract all possible mentions
		RuleBasedCorefMentionFinder finder = new RuleBasedCorefMentionFinder();
		List<List<Mention>> allUnprocessedMentions = finder.extractPredictedMentions(document, 0,
				corefSystem.dictionaries());

		// add the relevant info to mentions and order them for coref
		Document doc = mentionExtractor.arrange(document, sentenceTokens, trees, allUnprocessedMentions);
		Map<Integer, CorefChain> result = corefSystem.coref(doc);
		
		for (CorefChain chain : result.values()) {
			CoreferenceLink last = null;
			for (CorefMention mention : chain.getCorefMentions()) {
				CoreLabel beginLabel = sentences.get(mention.sentNum - 1)
						.get(TokensAnnotation.class).get(mention.startIndex - 1);
				CoreLabel endLabel = sentences.get(mention.sentNum - 1).get(TokensAnnotation.class)
						.get(mention.endIndex - 2);
				CoreferenceLink link = new CoreferenceLink(aJCas, 
						beginLabel.get(TokenKey.class).getBegin(),
						endLabel.get(TokenKey.class).getEnd());
				
				if (mention.mentionType != null) {
					link.setReferenceType(mention.mentionType.toString());
				}

				if (last == null) {
					// This is the first mention. Here we'll initialize the chain
					CoreferenceChain corefChain = new CoreferenceChain(aJCas);
					corefChain.setFirst(link);
					corefChain.addToIndexes();
				}
				else {
					// For the other mentions, we'll add them to the chain.
					last.setNext(link);
				}
				last = link;

				link.addToIndexes();
			}
		}
	}

	private static class RootKey
		implements Key<CoreMap, ROOT>
	{
	};

	private static class TokenKey
		implements Key<CoreMap, Token>
	{
	};
}
