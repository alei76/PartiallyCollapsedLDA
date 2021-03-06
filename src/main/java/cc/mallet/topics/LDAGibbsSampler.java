package cc.mallet.topics;

import java.io.IOException;
import java.util.ArrayList;

import cc.mallet.configuration.LDAConfiguration;
import cc.mallet.topics.TopicAssignment;
import cc.mallet.types.Alphabet;
import cc.mallet.types.InstanceList;

public interface LDAGibbsSampler {
	void setConfiguration(LDAConfiguration config);
	void addInstances (InstanceList training);
	void sample (int iterations) throws IOException;
	void setRandomSeed(int seed);
	String [][] getTopWords(int noWords);
	int getNoTopics();
	int getCurrentIteration();
	int [][] getZIndicators();
	double [][] getZbar();
	double[][] getThetaEstimate();
	void setZIndicators(int[][] zIndicators);
	ArrayList<TopicAssignment> getDataset();
	int[][] getDeltaStatistics();
	int[] getTopTypeFrequencyIndices();
	int[] getTypeFrequencies();
	int getCorpusSize();
	Alphabet getAlphabet();
	int getStartSeed();
	double[] getTypeMassCumSum();
	int [][] getDocumentTopicMatrix();
	int [][] getTypeTopicMatrix();
	void preIteration();
	void postIteration();
	void preSample();
	void postSample();
	void postZ();
	void preZ();
}
