/*
 *  Copyright 2014 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package es.ehu.si.ixa.pipe.nerc;

import ixa.kaflib.KAFDocument;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import opennlp.tools.util.TrainingParameters;

import org.jdom2.JDOMException;

import com.google.common.io.Files;

import es.ehu.si.ixa.pipe.nerc.eval.CorpusEvaluate;
import es.ehu.si.ixa.pipe.nerc.eval.Evaluate;
import es.ehu.si.ixa.pipe.nerc.train.FixedTrainer;
import es.ehu.si.ixa.pipe.nerc.train.InputOutputUtils;
import es.ehu.si.ixa.pipe.nerc.train.NameModel;
import es.ehu.si.ixa.pipe.nerc.train.Trainer;

/**
 * Main class of ixa-pipe-nerc, the ixa pipes (ixa2.si.ehu.es/ixa-pipes) NERC
 * tagger.
 * 
 * @author ragerri
 * @version 2014-06-26
 * 
 */
public class CLI {

  /**
   * Get dynamically the version of ixa-pipe-nerc by looking at the MANIFEST
   * file.
   */
  private final String version = CLI.class.getPackage()
      .getImplementationVersion();
  /**
   * Name space of the arguments provided at the CLI.
   */
  private Namespace parsedArguments = null;
  /**
   * Argument parser instance.
   */
  private ArgumentParser argParser = ArgumentParsers.newArgumentParser(
      "ixa-pipe-nerc-" + version + ".jar").description(
      "ixa-pipe-nerc-" + version
          + " is a multilingual NERC module developed by IXA NLP Group.\n");
  /**
   * Sub parser instance.
   */
  private Subparsers subParsers = argParser.addSubparsers().help(
      "sub-command help");
  /**
   * The parser that manages the tagging sub-command.
   */
  private Subparser annotateParser;
  /**
   * The parser that manages the training sub-command.
   */
  private Subparser trainParser;
  /**
   * The parser that manages the evaluation sub-command.
   */
  private Subparser evalParser;

  /**
   * Default beam size for decoding.
   */
  public static final int DEFAULT_BEAM_SIZE = 3;
  public static final String DEFAULT_EVALUATE_MODEL = "off";
  public static final String DEFAULT_NE_TYPES = "off";
  public static final String DEFAULT_FEATURES = "baseline";
  public static final String DEFAULT_LEXER = "off";
  public static final String DEFAULT_DICT_OPTION = "off";
  public static final String DEFAULT_DICT_PATH = "off";
  public static final String DEFAULT_OUTPUT_FORMAT="naf";

  /**
   * Construct a CLI object with the three sub-parsers to manage the command
   * line parameters.
   */
  public CLI() {
    annotateParser = subParsers.addParser("tag").help("Tagging CLI");
    loadAnnotateParameters();
    trainParser = subParsers.addParser("train").help("Training CLI");
    loadTrainingParameters();
    evalParser = subParsers.addParser("eval").help("Evaluation CLI");
    loadEvalParameters();
  }

  /**
   * Main entry point of ixa-pipe-nerc.
   * 
   * @param args
   *          the arguments passed through the CLI
   * @throws IOException
   *           exception if input data not available
   * @throws JDOMException
   *           if problems with the xml formatting of NAF
   */
  public static void main(final String[] args) throws IOException,
      JDOMException {

    CLI cmdLine = new CLI();
    cmdLine.parseCLI(args);
  }

  /**
   * Parse the command interface parameters with the argParser.
   * 
   * @param args
   *          the arguments passed through the CLI
   * @throws IOException
   *           exception if problems with the incoming data
   * @throws JDOMException 
   */
  public final void parseCLI(final String[] args) throws IOException, JDOMException {
    try {
      parsedArguments = argParser.parseArgs(args);
      System.err.println("CLI options: " + parsedArguments);
      if (args[0].equals("tag")) {
        annotate(System.in, System.out);
      } else if (args[0].equals("eval")) {
        eval();
      } else if (args[0].equals("train")) {
        train();
      }
    } catch (ArgumentParserException e) {
      argParser.handleError(e);
      System.out.println("Run java -jar target/ixa-pipe-nerc-" + version
          + ".jar (tag|train|eval) -help for details");
      System.exit(1);
    }
  }

  /**
   * Main method to do Named Entity tagging.
   * 
   * @param inputStream
   *          the input stream containing the content to tag
   * @param outputStream
   *          the output stream providing the named entities
   * @throws IOException
   *           exception if problems in input or output streams
   */
  public final void annotate(final InputStream inputStream,
      final OutputStream outputStream) throws IOException, JDOMException {

    BufferedReader breader = new BufferedReader(new InputStreamReader(
        inputStream, "UTF-8"));
    BufferedWriter bwriter = new BufferedWriter(new OutputStreamWriter(
        outputStream, "UTF-8"));
    // read KAF document from inputstream
    KAFDocument kaf = KAFDocument.createFromStream(breader);
    // load properties parameters file
    String paramFile = parsedArguments.getString("params");
    TrainingParameters params = InputOutputUtils
        .loadTrainingParameters(paramFile);
    // language parameter
    String lang = null;
    if (params.getSettings().get("Language") != null) {
      lang = params.getSettings().get("Language");
      if (!kaf.getLang().equalsIgnoreCase(lang)) {
        System.err
            .println("Lang parameter in NAF and parameters file do not match!!");
        System.exit(1);
      }
    } else {
      params.getSettings().put("Language", kaf.getLang());
    }
    String lexer = parsedArguments.getString("lexer");
    KAFDocument.LinguisticProcessor newLp = kaf.addLinguisticProcessor(
        "entities", "ixa-pipe-nerc-" + lang + "-" + paramFile, version);
    newLp.setBeginTimestamp();
    Properties properties = setAnnotateProperties(lexer);
    Annotate annotator = new Annotate(properties, params);
    annotator.annotateNEs(kaf);
    String outputFormatOption = InputOutputUtils.getOutputFormat(params);
    String kafToString = null;
    if (outputFormatOption.equalsIgnoreCase("conll03")) {
      kafToString = annotator.annotateNEsToCoNLL2003(kaf);
    } else if (outputFormatOption.equalsIgnoreCase("conll02")) {
      kafToString = annotator.annotateNEsToCoNLL2002(kaf);
    } else {
      kafToString = annotator.annotateNEsToKAF(kaf);
    }
    newLp.setEndTimestamp();
    bwriter.write(kafToString);
    bwriter.close();
    breader.close();
  }

  /**
   * Main access to the train functionalities.
   * 
   * @throws IOException
   *           input output exception if problems with corpora
   */
  public final void train() throws IOException {

    // load training parameters file
    String paramFile = parsedArguments.getString("params");
    TrainingParameters params = InputOutputUtils
        .loadTrainingParameters(paramFile);
    String outModel = null;
    if (params.getSettings().get("OutputModel") == null || params.getSettings().get("OutputModel").length() == 0) {
      outModel = Files.getNameWithoutExtension(paramFile) + ".bin";
      params.put("OutputModel", outModel);
    }
    else {
      outModel = InputOutputUtils.getModel(params);
    }
    String trainSet = InputOutputUtils.getDataSet("TrainSet", params);
    String testSet = InputOutputUtils.getDataSet("TestSet", params);
    Trainer nercTrainer = new FixedTrainer(trainSet, testSet, params);
    NameModel trainedModel = null;
    // check if CrossEval
    if (params.getSettings().get("CrossEval") != null) {
      String evalParam = params.getSettings().get("CrossEval");
      String[] evalRange = evalParam.split("[ :-]");
      if (evalRange.length != 2) {
        InputOutputUtils.devSetException();
      } else {
        if (params.getSettings().get("DevSet") != null) {
          String devSet = params.getSettings().get("DevSet");
          trainedModel = nercTrainer.trainCrossEval(devSet, params, evalRange);
        } else {
          InputOutputUtils.devSetException();
        }
      }
    } else {
      trainedModel = nercTrainer.train(params);
    }
    InputOutputUtils.saveModel(trainedModel, outModel);
    System.out.println();
    System.out.println("Wrote trained NERC model to " + outModel);
  }

  /**
   * Main evaluation entry point.
   * 
   * @throws IOException
   *           throws exception if test set not available
   */
  public final void eval() throws IOException {

    String predFile = parsedArguments.getString("prediction");
    // load training parameters file
    String paramFile = parsedArguments.getString("params");
    TrainingParameters params = InputOutputUtils
        .loadTrainingParameters(paramFile);
    if (parsedArguments.getString("prediction") == null) {
      Evaluate evaluator = new Evaluate(params);
      if (parsedArguments.getString("evalReport") != null) {
        if (parsedArguments.getString("evalReport").equalsIgnoreCase("brief")) {
          evaluator.evaluate();
        } else if (parsedArguments.getString("evalReport").equalsIgnoreCase(
            "error")) {
          evaluator.evalError();
        } else if (parsedArguments.getString("evalReport").equalsIgnoreCase(
            "detailed")) {
          evaluator.detailEvaluate();
        }
      } else {
        evaluator.detailEvaluate();
      }
    } else if (parsedArguments.getString("prediction") != null) {
      CorpusEvaluate corpusEvaluator = new CorpusEvaluate(predFile, params);
      corpusEvaluator.evaluate();
    } else {
      System.err
          .println("Provide either a model or a predictionFile to perform evaluation!");
    }
  }

  /**
   * Create the available parameters for NER tagging.
   */
  private void loadAnnotateParameters() {
    annotateParser.addArgument("-p", "--params").required(true)
        .help("Load the parameters file\n");
    annotateParser.addArgument("--lexer").choices("numeric")
        .setDefault(DEFAULT_LEXER).required(false)
        .help("Use lexer rules for NERC tagging\n");
  }

  /**
   * Create the main parameters available for training NERC models.
   */
  private void loadTrainingParameters() {
    trainParser.addArgument("-p", "--params").required(true)
        .help("Load the training parameters file\n");
  }

  /**
   * Create the parameters available for evaluation.
   */
  private void loadEvalParameters() {
    evalParser.addArgument("-p", "--params").required(true)
        .help("Load the parameters file\n");
    evalParser
        .addArgument("--prediction")
        .required(false)
        .help(
            "Use this parameter to evaluate one prediction corpus against a reference corpus\n");
    evalParser.addArgument("--evalReport").required(false)
        .choices("brief", "detailed", "error");
  }

  private Properties setAnnotateProperties(String ruleBasedOption) {
    Properties annotateProperties = new Properties();
    annotateProperties.setProperty("ruleBasedOption", ruleBasedOption);
    return annotateProperties;
  }

}
