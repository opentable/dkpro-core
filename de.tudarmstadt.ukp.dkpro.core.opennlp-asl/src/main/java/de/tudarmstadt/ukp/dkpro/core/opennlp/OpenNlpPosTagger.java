/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.opennlp;

import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;
import static org.apache.uima.fit.util.JCasUtil.toText;
import static org.apache.uima.util.Level.INFO;

import java.io.InputStream;
import java.util.List;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProviderFactory;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ModelProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.opennlp.internal.OpenNlpTagsetDescriptionProvider;

/**
 * Part-of-Speech annotator using OpenNLP. Requires {@link Sentence}s to be annotated before.
 *
 */
@TypeCapability(
	    inputs = {
	        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
	        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence" },
		outputs = {
		    "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS" })
public class OpenNlpPosTagger
	extends JCasAnnotator_ImplBase
{
	/**
	 * Use this language instead of the document language to resolve the model.
	 */
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
	protected String language;

	/**
	 * Override the default variant used to locate the model.
	 */
	public static final String PARAM_VARIANT = ComponentParameters.PARAM_VARIANT;
	@ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
	protected String variant;

	/**
	 * Load the model from this location instead of locating the model automatically.
	 */
	public static final String PARAM_MODEL_LOCATION = ComponentParameters.PARAM_MODEL_LOCATION;
	@ConfigurationParameter(name = PARAM_MODEL_LOCATION, mandatory = false)
	protected String modelLocation;

	/**
	 * Load the part-of-speech tag to UIMA type mapping from this location instead of locating
	 * the mapping automatically.
	 */
	public static final String PARAM_POS_MAPPING_LOCATION = ComponentParameters.PARAM_POS_MAPPING_LOCATION;
	@ConfigurationParameter(name = PARAM_POS_MAPPING_LOCATION, mandatory = false)
	protected String posMappingLocation;

	/**
	 * Use the {@link String#intern()} method on tags. This is usually a good idea to avoid
	 * spaming the heap with thousands of strings representing only a few different tags.
	 *
	 * Default: {@code true}
	 */
	public static final String PARAM_INTERN_TAGS = ComponentParameters.PARAM_INTERN_TAGS;
	@ConfigurationParameter(name = PARAM_INTERN_TAGS, mandatory = false, defaultValue = "true")
	private boolean internTags;

	/**
	 * Log the tag set(s) when a model is loaded.
	 *
	 * Default: {@code false}
	 */
	public static final String PARAM_PRINT_TAGSET = ComponentParameters.PARAM_PRINT_TAGSET;
	@ConfigurationParameter(name = PARAM_PRINT_TAGSET, mandatory = true, defaultValue="false")
	protected boolean printTagSet;

	private CasConfigurableProviderBase<POSTagger> modelProvider;
	private MappingProvider mappingProvider;

	@Override
	public void initialize(UimaContext aContext)
		throws ResourceInitializationException
	{
		super.initialize(aContext);

		modelProvider = new ModelProviderBase<POSTagger>(this, "opennlp", "tagger") {
		    {
		        setDefault(VARIANT, "maxent");
		    }
		    
			@Override
			protected POSTagger produceResource(InputStream aStream)
			    throws Exception
			{
				POSModel model = new POSModel(aStream);

				OpenNlpTagsetDescriptionProvider tsdp = new OpenNlpTagsetDescriptionProvider(
				        getResourceMetaData().getProperty("pos.tagset"), POS.class, 
				        model.getPosModel());
                if (getResourceMetaData().containsKey("pos.tagset.tagSplitPattern")) {
                    tsdp.setTagSplitPattern(getResourceMetaData().getProperty("pos.tagset.tagSplitPattern"));
                }
                addTagset(tsdp);

				if (printTagSet) {
					getContext().getLogger().log(INFO, tsdp.toString());
				}

				return new POSTaggerME(model);
			}
		};

        mappingProvider = MappingProviderFactory.createPosMappingProvider(posMappingLocation,
                language, modelProvider);
	}

	@Override
	public void process(JCas aJCas)
		throws AnalysisEngineProcessException
	{
		CAS cas = aJCas.getCas();

		modelProvider.configure(cas);
		mappingProvider.configure(cas);

        String tagSplitPattern = modelProvider.getResourceMetaData().getProperty(
                "pos.tagset.tagSplitPattern");
		
		for (Sentence sentence : select(aJCas, Sentence.class)) {
			List<Token> tokens = selectCovered(aJCas, Token.class, sentence);
			String[] tokenTexts = toText(tokens).toArray(new String[tokens.size()]);

			String[] tags = modelProvider.getResource().tag(tokenTexts);

			int i = 0;
			for (Token t : tokens) {
			    String tag = tags[i];
			    
	            if (tagSplitPattern != null) {
	                tag = tag.split(tagSplitPattern)[0];
	            }
			    
				Type posTag = mappingProvider.getTagType(tag);
				POS posAnno = (POS) cas.createAnnotation(posTag, t.getBegin(), t.getEnd());
				posAnno.setPosValue(internTags ? tag.intern() : tag);
				posAnno.addToIndexes();
				t.setPos(posAnno);
				i++;
			}
		}
	}
}
