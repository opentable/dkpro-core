/**
 * Copyright 2007-2014
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package de.tudarmstadt.ukp.dkpro.core.matetools;

import static org.apache.uima.util.Level.INFO;
import is2.data.SentenceData09;
import is2.io.CONLLReader09;
import is2.parser.MFO;
import is2.parser.Options;
import is2.parser.Parser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.SingletonTagset;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProviderFactory;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ModelProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ResourceUtils;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;

/**
 * <p>
 * DKPro Annotator for the MateToolsParser
 * </p>
 *
 * Please cite the following paper, if you use the parser Bernd Bohnet. 2010. Top Accuracy and Fast
 * Dependency Parsing is not a Contradiction. The 23rd International Conference on Computational
 * Linguistics (COLING 2010), Beijing, China.
 *
 * Required annotations:
 * <ul>
 * <li>Sentence</li>
 * <li>Token</li>
 * <li>POS</li>
 * </ul>
 *
 * Generated annotations:
 * <ul>
 * <li>Dependency</li>
 * </ul>
 *
 *
 */
@TypeCapability(
        inputs = {
            "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
            "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
            "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS" },
        outputs = {
            "de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency" })
public class MateParser
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
     * Log the tag set(s) when a model is loaded.
     *
     * Default: {@code false}
     */
    public static final String PARAM_PRINT_TAGSET = ComponentParameters.PARAM_PRINT_TAGSET;
    @ConfigurationParameter(name = PARAM_PRINT_TAGSET, mandatory = true, defaultValue = "false")
    protected boolean printTagSet;

    /**
     * Load the dependency to UIMA type mapping from this location instead of locating
     * the mapping automatically.
     */
    public static final String PARAM_DEPENDENCY_MAPPING_LOCATION = ComponentParameters.PARAM_DEPENDENCY_MAPPING_LOCATION;
    @ConfigurationParameter(name = PARAM_DEPENDENCY_MAPPING_LOCATION, mandatory = false)
    protected String dependencyMappingLocation;


	private CasConfigurableProviderBase<Parser> modelProvider;
    private MappingProvider mappingProvider;

	@Override
	public void initialize(UimaContext aContext)
		throws ResourceInitializationException
	{
		super.initialize(aContext);

		modelProvider = new ModelProviderBase<Parser>(this, "matetools", "parser")
		{
			@Override
			protected Parser produceResource(URL aUrl)
				throws IOException
			{
				File modelFile = ResourceUtils.getUrlAsFile(aUrl, true);

				String[] args = { "-model", modelFile.getPath() };
				Options option = new Options(args);
				Parser parser = new Parser(option); // create a parser

				Properties metadata = getResourceMetaData();

				HashMap<String, HashMap<String, Integer>> featureSet = MFO.getFeatureSet();
                SingletonTagset posTags = new SingletonTagset(
                        POS.class, metadata.getProperty("pos.tagset"));
                HashMap<String, Integer> posTagFeatures = featureSet.get("POS");
                posTags.addAll(posTagFeatures.keySet());
                addTagset(posTags);

                SingletonTagset depTags = new SingletonTagset(
                        Dependency.class, metadata.getProperty("dependency.tagset"));
                HashMap<String, Integer> depTagFeatures = featureSet.get("REL");
                depTags.addAll(depTagFeatures.keySet());
                addTagset(depTags);

                if (printTagSet) {
                    getContext().getLogger().log(INFO, getTagset().toString());
                }

				return parser;
			}
		};
		
        mappingProvider = MappingProviderFactory.createDependencyMappingProvider(
                dependencyMappingLocation, language, modelProvider);
	}

	@Override
	public void process(JCas jcas)
		throws AnalysisEngineProcessException
	{
		CAS cas = jcas.getCas();

		modelProvider.configure(cas);
		mappingProvider.configure(cas);

		for (Sentence sentence : JCasUtil.select(jcas, Sentence.class)) {
			List<Token> tokens = JCasUtil.selectCovered(Token.class, sentence);

			List<String> forms = new LinkedList<String>();
			forms.add(CONLLReader09.ROOT);
			forms.addAll(JCasUtil.toText(tokens));

			List<String> lemmas = new LinkedList<String>();
			List<String> posTags = new LinkedList<String>();
			lemmas.add(CONLLReader09.ROOT_LEMMA);
			posTags.add(CONLLReader09.ROOT_POS);
			for (Token token : tokens) {
			    if (token.getLemma() != null) {
			        lemmas.add(token.getLemma().getValue());
			    }
			    else {
			        lemmas.add("_");
			    }
				posTags.add(token.getPos().getPosValue());
			}

			SentenceData09 sd = new SentenceData09();
			sd.init(forms.toArray(new String[forms.size()]));
			sd.setLemmas(lemmas.toArray(new String[lemmas.size()]));
			sd.setPPos(posTags.toArray(new String[posTags.size()]));
			SentenceData09 parsed = modelProvider.getResource().apply(sd);

			for (int i = 0; i < parsed.labels.length; i++) {
				if (parsed.pheads[i] != 0) {
					Token sourceToken = tokens.get(parsed.pheads[i] - 1);
					Token targetToken = tokens.get(i);

	                Type depRel = mappingProvider.getTagType(parsed.plabels[i]);
	                Dependency dep = (Dependency) cas.createFS(depRel);
					dep.setGovernor(sourceToken);
					dep.setDependent(targetToken);
					dep.setDependencyType(parsed.plabels[i]);
                    dep.setBegin(dep.getDependent().getBegin());
                    dep.setEnd(dep.getDependent().getEnd());
					dep.addToIndexes();
				}
			}
		}
	}
}
