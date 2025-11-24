package src.labs.zombayes.models;

// SYSTEM IMPORTS
import edu.bu.labs.zombayes.agents.SurvivalAgent;
import edu.bu.labs.zombayes.linalg.Matrix;
import edu.bu.labs.zombayes.linalg.Functions;
import edu.bu.labs.zombayes.utils.Pair;
import edu.bu.labs.zombayes.features.Features.FeatureType;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// JAVA PROJECT IMPORTS

public class NaiveBayes
        extends Object {

    private final List<Pair<FeatureType, Integer>> featureHeader; // array of (FEATURE_TYPE, NUM_FEATURE_VALUES)

    // parameters of the model
    private int numClasses = 2;
    private double[] priors; // probability of y in general
    private double[][] means;
    private double[][] variances;
    private double[][][] dprobs;

    public NaiveBayes(List<Pair<FeatureType, Integer>> featureHeader) {
        this.featureHeader = featureHeader;
        this.priors = null;
        this.means = null;
        this.variances = null;
        this.dprobs = null;
    }

    public List<Pair<FeatureType, Integer>> getFeatureHeader() {
        return this.featureHeader;
    }

    public int getNumClasses() {
        return this.numClasses;
    }

    public double[] getPriors() {
        return this.priors;
    }

    public double[][] getMeans() {
        return this.means;
    }

    public double[][] getVariances() {
        return this.variances;
    }

    public double[][][] getDprobs() {
        return this.dprobs;
    }

    public void setNumClasses(int numClasses) {
        this.numClasses = numClasses;
    }

    public void setPriors(double[] priors) {
        this.priors = priors;
    }

    public void setMeans(double[][] means) {
        this.means = means;
    }

    public void setVariances(double[][] variances) {
        this.variances = variances;
    }

    public void setDprobs(double[][][] dprobs) {
        this.dprobs = dprobs;
    }

    // TODO: complete me!
    public void fit(Matrix X, Matrix y_gt) {
        int n = X.getShape().getNumRows();
        int dX = X.getShape().getNumCols();

        this.setPriors(new double[this.getNumClasses()]);
        this.setMeans(new double[this.getNumClasses()][dX]);
        this.setVariances(new double[this.getNumClasses()][dX]);
        this.setDprobs(new double[this.getNumClasses()][dX][]);

        // priors
        int count0 = 0, count1 = 0;
        for (int i = 0; i < n; i++) {
            if (y_gt.get(i, 0) == 0) {
                count0++;
            } else {
                count1++;
            }
        }
        this.priors[0] = (double) count0 / Math.max(1, n);
        this.priors[1] = (double) count1 / Math.max(1, n);

        // data structs to hold sums and counts
        double[][] sum = new double[this.getNumClasses()][dX];
        double[][] sumsq = new double[this.getNumClasses()][dX];
        int[] classCounts = new int[this.getNumClasses()];

        int[][][] dcounts = new int[this.getNumClasses()][dX][];

        for (int j = 0; j < dX; j++) {
            if (this.featureHeader.get(j).getFirst() == FeatureType.DISCRETE) {
                int numValues = this.featureHeader.get(j).getSecond();
                for (int c = 0; c < this.getNumClasses(); c++) {
                    dcounts[c][j] = new int[numValues];
                }
            }
        }

        // per class stats
        for (int i = 0; i < n; i++) {
            int label = (int) y_gt.get(i, 0);
            classCounts[label]++;
            for (int j = 0; j < dX; j++) {
                double xij = X.get(i, j);
                FeatureType ftype = this.featureHeader.get(j).getFirst();
                if (ftype == FeatureType.CONTINUOUS) {
                    sum[label][j] += xij;
                    sumsq[label][j] += xij * xij;
                } else {
                    int v = (int) xij;
                    int[] countsArray = dcounts[label][j];
                    if (v >= 0 && v < countsArray.length) {
                        countsArray[v]++;
                    }
                }
            }
        }

        // finalize means and variances / discrete probs
        for (int c = 0; c < this.getNumClasses(); c++) {
            int count = classCounts[c];

            for (int j = 0; j < dX; j++) {
                FeatureType type = this.featureHeader.get(j).getFirst();

                if (type == FeatureType.CONTINUOUS) {
                    if (count > 0) {
                        double mean = sum[c][j] / count;
                        double var = (sumsq[c][j] / count) - mean * mean; // E[x^2] - (E[x])^2
                        if (var <= 1e-6) {
                            var = 1e-6;
                        }
                        this.means[c][j] = mean;
                        this.variances[c][j] = var;
                    } else {
                        this.means[c][j] = 0.0;
                        this.variances[c][j] = 1.0;
                    }
                } else {
                    int[] counts = dcounts[c][j];
                    int numVals = counts.length;
                    double[] probs = new double[numVals];

                    int total = 0;
                    for (int v = 0; v < numVals; v++) {
                        total += counts[v];
                    }
                    int denom = total + numVals;
                    for (int v = 0; v < numVals; v++) {
                        probs[v] = (counts[v] + 1.0) / (double) denom;
                    }
                    this.dprobs[c][j] = probs;
                }
            }
        }

    }

    // helper to do the Gaussian log pdf calculation
    private double gaussianLogPdf(double x, double mean, double var) {
        return -0.5 * (Math.log(2.0 * Math.PI * var) + (x - mean) * (x - mean) / var);
    }

    public int predict(Matrix x) {
        int dX = x.getShape().getNumCols();
        int rows = x.getShape().getNumRows();
        int cols = x.getShape().getNumCols();
        boolean rowVector = (cols == dX);
        double bestLogProb = Double.NEGATIVE_INFINITY;
        int bestLabel = 0;

        for (int c = 0; c < this.numClasses; c++) {
            double logProb = Math.log(this.priors[c]);

            for (int j = 0; j < dX; j++) {
                double xj = rowVector ? x.get(0, j) : x.get(j, 0);
                FeatureType type = this.featureHeader.get(j).getFirst();

                if (type == FeatureType.CONTINUOUS) {
                    double mean = this.means[c][j];
                    double var = this.variances[c][j];
                    logProb += gaussianLogPdf(xj, mean, var);
                } else {
                    int v = (int) xj;
                    double[] probs = this.dprobs[c][j];

                    double p;
                    if (probs == null || v < 0 || v >= probs.length) {
                        p = 1.0 / (probs != null ? probs.length : 1.0);
                    } else {
                        p = probs[v];
                    }
                    if (p <= 0.0) {
                        p = 1e-12;
                    }
                    logProb += Math.log(p);
                }
            }

            if (logProb > bestLogProb) {
                bestLogProb = logProb;
                bestLabel = c;
            }
        }

        return bestLabel;
    }
}
