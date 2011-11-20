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

import static org.uimafit.factory.AnalysisEngineFactory.createAggregate;
import static org.uimafit.factory.AnalysisEngineFactory.createAggregateDescription;
import static org.uimafit.factory.AnalysisEngineFactory.createPrimitiveDescription;
import static org.uimafit.util.JCasUtil.select;
import static org.uimafit.util.JCasUtil.toText;

import org.apache.commons.lang.StringUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.jcas.JCas;
import org.junit.Assume;
import org.junit.Test;

import de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceChain;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;

/**
 * @author Richard Eckart de Castilho
 */
public class StanfordCoreferenceResolverTest
{
	@Test
	public void test()
		throws Exception
	{ 
		checkModel("/de/tudarmstadt/ukp/dkpro/core/stanfordnlp/lib/lexparser-en-pcfg.ser.gz");
		AnalysisEngine aggregate = createAggregate(createAggregateDescription(
				createPrimitiveDescription(StanfordSegmenter.class),
				createPrimitiveDescription(StanfordParser.class,
						StanfordParser.PARAM_MODEL,
						"classpath:/de/tudarmstadt/ukp/dkpro/core/stanfordnlp/lib/lexparser-en-pcfg.ser.gz",
						StanfordParser.PARAM_LANGUAGE_PACK, PennTreebankLanguagePack.class.getName(),
						StanfordParser.PARAM_CREATE_CONSTITUENT_TAGS, true,
						StanfordParser.PARAM_CREATE_DEPENDENCY_TAGS, true,
						StanfordParser.PARAM_CREATE_PENN_TREE_STRING, true,
						StanfordParser.PARAM_CREATE_POS_TAGS, true),
				createPrimitiveDescription(
						StanfordNamedEntityRecognizer.class,
						StanfordNamedEntityRecognizer.PARAM_MODEL, 
						"classpath:/de/tudarmstadt/ukp/dkpro/core/stanfordnlp/lib/ner-en-all.3class.distsim.crf.ser.gz"),
				createPrimitiveDescription(StanfordCoreferenceResolver.class)));

		JCas jcas = aggregate.newJCas();
		jcas.setDocumentLanguage("en");
		jcas.setDocumentText("John bought a car. He is very happy with it.");

		aggregate.process(jcas);

		for (CoreferenceChain chain : select(jcas, CoreferenceChain.class)) {
			System.out.println("Chain: [" + StringUtils.join(toText(chain.links()), "] , [") + "]");
		}
	}
	
    void checkModel(String aModel)
    {
		Assume.assumeTrue(getClass().getResource(aModel) != null);
    }
}
