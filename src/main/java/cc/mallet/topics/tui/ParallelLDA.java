package cc.mallet.topics.tui;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cc.mallet.configuration.ConfigFactory;
import cc.mallet.configuration.Configuration;
import cc.mallet.configuration.LDACommandLineParser;
import cc.mallet.configuration.LDAConfiguration;
import cc.mallet.configuration.ParsedLDAConfiguration;
import cc.mallet.topics.ADLDA;
import cc.mallet.topics.CollapsedLightLDA;
import cc.mallet.topics.EfficientUncollapsedParallelLDA;
import cc.mallet.topics.LDAGibbsSampler;
import cc.mallet.topics.LDASamplerWithPhi;
import cc.mallet.topics.LightPCLDA;
import cc.mallet.topics.LightPCLDAtypeTopicProposal;
import cc.mallet.topics.NZVSSpaliasUncollapsedParallelLDA;
import cc.mallet.topics.SerialCollapsedLDA;
import cc.mallet.topics.SpaliasUncollapsedParallelLDA;
import cc.mallet.topics.SpaliasUncollapsedParallelWithPriors;
import cc.mallet.topics.UncollapsedParallelLDA;
import cc.mallet.types.InstanceList;
import cc.mallet.util.LDAUtils;
import cc.mallet.util.LoggingUtils;
import cc.mallet.util.Timer;

public class ParallelLDA {
	public static String PROGRAM_NAME = "ParallelLDA";

	public static void main(String[] args) throws Exception {
		
		if(args.length == 0) {
			System.out.println("\n" + PROGRAM_NAME + ": No args given, you should typically call it along the lines of: \n" 
					+ "java -cp PCPLDA-X.X.X.jar cc.mallet.topics.tui.ParallelLDA --run_cfg=src/main/resources/configuration/PLDAConfig.cfg\n" 
					+ "or\n" 
					+ "java -jar PCPLDA-X.X.X.jar -run_cfg=src/main/resources/configuration/PLDAConfig.cfg\n");
			System.exit(-1);
		}

		Thread.setDefaultUncaughtExceptionHandler(new Thread.
				UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				System.out.println(t + " throws exception: " + e);
				e.printStackTrace();
				System.err.println("Main thread Exiting.");
				System.exit(-1);
			}
		});

		System.out.println("We have: " + Runtime.getRuntime().availableProcessors() 
				+ " processors avaiable");
		String buildVer = LoggingUtils.getManifestInfo("Implementation-Build","PCPLDA");
		String implVer  = LoggingUtils.getManifestInfo("Implementation-Version", "PCPLDA");
		if(buildVer==null||implVer==null) {
			System.out.println("GIT info:" + LoggingUtils.getLatestCommit());
		} else {
			System.out.println("Build info:" 
					+ "Implementation-Build = " + buildVer + ", " 
					+ "Implementation-Version = " + implVer);
		}
		
		LDACommandLineParser cp = new LDACommandLineParser(args);
		
		// We have to create this temporary config because at this stage if we want to create a new config for each run
		ParsedLDAConfiguration tmpconfig = (ParsedLDAConfiguration) ConfigFactory.getMainConfiguration(cp);			
		
		int numberOfRuns = tmpconfig.getInt("no_runs");
		System.out.println("Doing: " + numberOfRuns + " runs");
		// Reading in command line parameters		
		for (int i = 0; i < numberOfRuns; i++) {
			System.out.println("Starting run: " + i);
			
			LDAConfiguration config = (LDAConfiguration) ConfigFactory.getMainConfiguration(cp);
			LoggingUtils lu = new LoggingUtils();
			String expDir = config.getExperimentOutputDirectory("");
			if(!expDir.equals("")) {
				expDir += "/";
			}
			String logSuitePath = "Runs/" + expDir + "RunSuite" + LoggingUtils.getDateStamp();
			System.out.println("Logging to: " + logSuitePath);
			lu.checkAndCreateCurrentLogDir(logSuitePath);
			config.setLoggingUtil(lu);

			String [] configs = config.getSubConfigs();
			for(String conf : configs) {
				lu.checkCreateAndSetSubLogDir(conf);
				config.activateSubconfig(conf);
				int commonSeed = config.getSeed(LDAConfiguration.SEED_DEFAULT);

				System.out.println("Using Config: " + config.whereAmI());
				System.out.println("Runnin subconfig: " + conf);
				String dataset_fn = config.getDatasetFilename();
				System.out.println("Using dataset: " + dataset_fn);
				String whichModel = config.getScheme();
				System.out.println("Scheme: " + whichModel);

				InstanceList instances;
				if(config.getTfIdfVocabSize(LDAConfiguration.TF_IDF_VOCAB_SIZE_DEFAULT)>0) {
					instances = LDAUtils.loadInstancesKeep(
							dataset_fn, 
							config.getStoplistFilename("stoplist.txt"), 
							config.getTfIdfVocabSize(LDAConfiguration.TF_IDF_VOCAB_SIZE_DEFAULT), 
							config.keepNumbers(), 
							config.getMaxDocumentBufferSize(LDAConfiguration.MAX_DOC_BUFFFER_SIZE_DEFAULT), 
							config.getKeepConnectingPunctuation(LDAConfiguration.KEEP_CONNECTING_PUNCTUATION));					
				} else {					
					instances = LDAUtils.loadInstancesPrune(
							dataset_fn, 
							config.getStoplistFilename("stoplist.txt"), 
							config.getRareThreshold(LDAConfiguration.RARE_WORD_THRESHOLD), 
							config.keepNumbers(), 
							config.getMaxDocumentBufferSize(LDAConfiguration.MAX_DOC_BUFFFER_SIZE_DEFAULT), 
							config.getKeepConnectingPunctuation(LDAConfiguration.KEEP_CONNECTING_PUNCTUATION));
				}

				LDAGibbsSampler model = createModel(config, whichModel);
				
				model.setRandomSeed(commonSeed);
				if(config.getTfIdfVocabSize(LDAConfiguration.TF_IDF_VOCAB_SIZE_DEFAULT)>0) {
					System.out.println(String.format("Top TF-IDF threshold: %d", config.getTfIdfVocabSize(LDAConfiguration.TF_IDF_VOCAB_SIZE_DEFAULT)));
				} else {
					System.out.println(String.format("Rare word threshold: %d", config.getRareThreshold(LDAConfiguration.RARE_WORD_THRESHOLD)));
				}

				System.out.println("Vocabulary size: " + instances.getDataAlphabet().size() + "\n");
				System.out.println("Instance list is: " + instances.size());
				System.out.println("Loading data instances...");

				// Sets the frequent with which top words for each topic are printed
				//model.setShowTopicsInterval(config.getTopicInterval(LDAConfiguration.TOPIC_INTER_DEFAULT));
				System.out.println("Config seed:" + config.getSeed(LDAConfiguration.SEED_DEFAULT));
				System.out.println("Start seed: " + model.getStartSeed());
				// Imports the data into the model
				model.addInstances(instances);
			
				System.out.println("Loaded " + model.getDataset().size() + " documents, with " + model.getCorpusSize() + " words in total.");

				System.out.println("Starting iterations (" + config.getNoIterations(LDAConfiguration.NO_ITER_DEFAULT) + " total).");
				System.out.println("_____________________________\n");

				// Runs the model
				System.out.println("Starting:" + new Date());
				Timer t = new Timer();
				t.start();
				model.sample(config.getNoIterations(LDAConfiguration.NO_ITER_DEFAULT));
				t.stop();
				System.out.println("Finished:" + new Date());
				
				File lgDir = lu.getLogDir();
				if(config.saveDocumentTopicMeans()) {
					String docTopicMeanFn = config.getDocumentTopicMeansOutputFilename();
					double [][] means = model.getZbar();
					LDAUtils.writeASCIIDoubleMatrix(means, lgDir.getAbsolutePath() + "/" + docTopicMeanFn, ",");
				}

				if(model instanceof LDASamplerWithPhi) {
					LDASamplerWithPhi modelWithPhi = (LDASamplerWithPhi) model;
					if(config.savePhiMeans(LDAConfiguration.SAVE_PHI_MEAN_DEFAULT)) {
						String docTopicMeanFn = config.getPhiMeansOutputFilename();
						double [][] means = modelWithPhi.getPhiMeans();
						if(means!=null) {
						LDAUtils.writeASCIIDoubleMatrix(means, lgDir.getAbsolutePath() + "/" + docTopicMeanFn, ",");
						} else {
							System.err.println("WARNING: ParallelLDA: No Phi means where sampled, not saving Phi means! This is likely due to a combination of configuration settings of phi_mean_burnin, phi_mean_thin and save_phi_mean");
						}
					}
				}

				List<String> metadata = new ArrayList<String>();
				metadata.add("No. Topics: " + model.getNoTopics());
				metadata.add("Start Seed: " + model.getStartSeed());
				// Save stats for this run
				lu.dynamicLogRun("Runs", t, cp, (Configuration) config, null, 
						ParallelLDA.class.getName(), "Convergence", "HEADING", "PLDA", 1, metadata);
				PrintWriter out = new PrintWriter(lgDir.getAbsolutePath() + "/TopWords.txt");
				int requestedWords = config.getNrTopWords(LDAConfiguration.NO_TOP_WORDS_DEFAULT);
				if(requestedWords>instances.getDataAlphabet().size()) {
					requestedWords = instances.getDataAlphabet().size();
				}
				out.println(LDAUtils.formatTopWords(model.getTopWords(requestedWords)));
				out.flush();
				out.close();

				System.out.println("Top words are: \n" + LDAUtils.formatTopWords(model.getTopWords(20)));
				System.out.println("I am done!");
			}
			if(buildVer==null||implVer==null) {
				System.out.println("GIT info:" + LoggingUtils.getLatestCommit());
			} else {
			System.out.println("Build info:" 
					+ "Implementation-Build = " + buildVer + ", " 
					+ "Implementation-Version = " + implVer);
			}
		}
	}

	public static LDAGibbsSampler createModel(LDAConfiguration config, String whichModel) {
		LDAGibbsSampler model;
		switch(whichModel) {
		case "adlda": {
			model = new ADLDA(config);
			System.out.println(
					String.format("ADLDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "uncollapsed": {
			model = new UncollapsedParallelLDA(config);
			System.out.println(
					String.format("Uncollapsed Parallell LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "collapsed": {
			model = new SerialCollapsedLDA(config);
			System.out.println(
					String.format("Uncollapsed Parallell LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "lightcollapsed": {
			model = new CollapsedLightLDA(config);
			System.out.println(
					String.format("CollapsedLightLDA Parallell LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "efficient_uncollapsed": {
			model = new EfficientUncollapsedParallelLDA(config);
			System.out.println(
					String.format("EfficientUncollapsedParallelLDA Parallell LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "spalias": {
			model = new SpaliasUncollapsedParallelLDA(config);
			System.out.println(
					String.format("SpaliasUncollapsed Parallell LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "spalias_priors": {
			model = new SpaliasUncollapsedParallelWithPriors(config);
			System.out.println(
					String.format("SpaliasUncollapsed Parallell LDA with Priors(%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "lightpclda": {
			model = new LightPCLDA(config);
			System.out.println(
					String.format("Light PC LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "lightpcldaw2": {
			model = new LightPCLDAtypeTopicProposal(config);
			System.out.println(
					String.format("Light PC LDA with proposal 2 (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		case "nzvsspalias": {
			model = new NZVSSpaliasUncollapsedParallelLDA(config);
			System.out.println(
					String.format("NZVSSpaliasUncollapsedParallelLDA Parallell LDA (%d batches).", 
							config.getNoBatches(LDAConfiguration.NO_BATCHES_DEFAULT)));
			break;
		}
		default : {
			System.out.println("Invalid model type. Aborting");
			return null;
		}
		}
		return model;
	}
}	
