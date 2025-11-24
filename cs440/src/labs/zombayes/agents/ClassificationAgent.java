package src.labs.zombayes.agents;

// SYSTEM IMPORTS
import edu.bu.labs.zombayes.agents.SurvivalAgent;
import edu.bu.labs.zombayes.features.Features.FeatureType;
import edu.bu.labs.zombayes.linalg.Matrix;
import edu.bu.labs.zombayes.utils.Pair;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// JAVA PROJECT IMPORTS
import src.labs.zombayes.models.NaiveBayes;

public class ClassificationAgent
        extends SurvivalAgent {

    private NaiveBayes model;

    public ClassificationAgent(int playerNum, String[] args) {
        super(playerNum, args);

        List<Pair<FeatureType, Integer>> featureHeader = new ArrayList<>(4);
        featureHeader.add(new Pair<>(FeatureType.CONTINUOUS, -1));
        featureHeader.add(new Pair<>(FeatureType.CONTINUOUS, -1));
        featureHeader.add(new Pair<>(FeatureType.DISCRETE, 3));
        featureHeader.add(new Pair<>(FeatureType.DISCRETE, 4));

        this.model = new NaiveBayes(featureHeader);
    }

    public NaiveBayes getModel() {
        return this.model;
    }

    public void writeToCSV(Matrix X, Matrix y_gt, String filename) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File(filename))) {
            StringBuilder sb = new StringBuilder();

            for (int j = 0; j < X.getShape().getNumCols(); j++) {
                sb.append("feature_").append(j);
                sb.append(",");
            }
            sb.append("label");
            sb.append("\n");

            for (int i = 0; i < X.getShape().getNumRows(); i++) {
                for (int j = 0; j < X.getShape().getNumCols(); j++) {
                    sb.append(X.get(i, j));
                    sb.append(",");
                }
                sb.append((int) y_gt.get(i, 0));
                sb.append("\n");
            }

            writer.write(sb.toString());
        } catch (java.io.FileNotFoundException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }

    @Override
    public void train(Matrix X, Matrix y_gt) {
        System.out.println(X.getShape() + " " + y_gt.getShape());
        this.writeToCSV(X, y_gt,
                "C:\\Users\\kaiso\\OneDrive\\Desktop\\cs440\\src\\labs\\zombayes\\models\\TrainingData\\training_data.csv");
        this.getModel().fit(X, y_gt);
    }

    @Override
    public int predict(Matrix featureRowVector) {
        return this.getModel().predict(featureRowVector);
    }

}
