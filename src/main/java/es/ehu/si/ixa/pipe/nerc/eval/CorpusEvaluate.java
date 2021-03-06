package es.ehu.si.ixa.pipe.nerc.eval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.FMeasure;
import es.ehu.si.ixa.pipe.nerc.formats.CorpusSample;
import es.ehu.si.ixa.pipe.nerc.formats.CorpusSampleTypeFilter;
import es.ehu.si.ixa.pipe.nerc.train.AbstractTrainer;
import es.ehu.si.ixa.pipe.nerc.train.InputOutputUtils;

/**
 * Evaluation class mostly using {@link TokenNameFinderEvaluator}.
 *
 * @author ragerri
 * @version 2014-04-04
 */
public class CorpusEvaluate {

  /**
   * The reference corpus to evaluate against.
   */
  private ObjectStream<CorpusSample> referenceSamples;
  /**
   * The reference corpus to evaluate against.
   */
  private ObjectStream<CorpusSample> predictionSamples;
  /**
   * The FMeasure implementation.
   */
  private FMeasure fmeasure = new FMeasure();
  /**
   * Construct an evaluator.
   *
   * @param predictionData the reference data to evaluate against
   * @param model the model to be evaluated
   * @param features the features
   * @param lang the language
   * @param beamsize the beam size for decoding
   * @param corpusFormat the format of the testData corpus
   * @throws IOException if input data not available
   */
  public CorpusEvaluate(final String predictionData, final TrainingParameters params) throws IOException {

    String referenceData = InputOutputUtils.getDataSet("TestSet", params);
    String lang = InputOutputUtils.getLanguage(params);
    String corpusFormat = InputOutputUtils.getCorpusFormat(params);
    referenceSamples = AbstractTrainer.getNameStream(referenceData, lang, corpusFormat);
    predictionSamples = AbstractTrainer.getNameStream(predictionData, lang, corpusFormat);
    if (params.getSettings().get("Types") != null) {
      String neTypes = params.getSettings().get("Types");
      String[] neTypesArray = neTypes.split(",");
      referenceSamples = new CorpusSampleTypeFilter(neTypesArray, referenceSamples);
      predictionSamples = new CorpusSampleTypeFilter(neTypesArray, predictionSamples);
    }
  }
  
  public List<CorpusSample> readSamplesToList(ObjectStream<CorpusSample> samples) throws IOException {
    CorpusSample sample;
    List<CorpusSample> nameSampleList = new ArrayList<CorpusSample>();
    while ((sample = samples.read()) != null) {
      nameSampleList.add(sample);
    }
    return nameSampleList;
  }

  /**
   * Evaluate and print precision, recall and F measure.
   * @throws IOException if test corpus not loaded
   */
  public final void evaluate() throws IOException {
    List<CorpusSample> refList = readSamplesToList(referenceSamples);
    List<CorpusSample> predList = readSamplesToList(predictionSamples);
    for (int i = 0; i < refList.size(); ++i) {
      fmeasure.updateScores(refList.get(i).getNames(), predList.get(i).getNames());
    }
    System.out.println(fmeasure.toString());
  }
  

}
