package src.labs.cp;

// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// JAVA PROJECT IMPORTS
import edu.bu.cp.linalg.Matrix;
import edu.bu.cp.nn.Model;
import edu.bu.cp.utils.Pair;

public class ReplayBuffer
        extends Object {

    public static enum ReplacementType {
        RANDOM,
        OLDEST;
    }

    private ReplacementType type;
    private int size;
    private int newestSampleIdx;

    private Matrix prevStates;
    private Matrix rewards;
    private Matrix nextStates;
    private boolean isStateTerminalMask[];

    private Random rng;

    public ReplayBuffer(ReplacementType type,
            int numSamples,
            int dim,
            Random rng) {
        this.type = type;
        this.size = 0;
        this.newestSampleIdx = -1;

        this.prevStates = Matrix.zeros(numSamples, dim);
        this.rewards = Matrix.zeros(numSamples, 1);
        this.nextStates = Matrix.zeros(numSamples, dim);
        this.isStateTerminalMask = new boolean[numSamples];

        this.rng = rng;

    }

    public int size() {
        return this.size;
    }

    public final ReplacementType getReplacementType() {
        return this.type;
    }

    private int getNewestSampleIdx() {
        return this.newestSampleIdx;
    }

    private Matrix getPrevStates() {
        return this.prevStates;
    }

    private Matrix getNextStates() {
        return this.nextStates;
    }

    private Matrix getRewards() {
        return this.rewards;
    }

    private boolean[] getIsStateTerminalMask() {
        return this.isStateTerminalMask;
    }

    private Random getRandom() {
        return this.rng;
    }

    private void setSize(int i) {
        this.size = i;
    }

    private void setNewestSampleIdx(int i) {
        this.newestSampleIdx = i;
    }

    private int chooseSampleToEvict() {
        int idxToEvict = -1;

        switch (this.getReplacementType()) {
            case RANDOM:
                idxToEvict = this.getRandom().nextInt(this.getNextStates().getShape().getNumRows());
                break;
            case OLDEST:
                idxToEvict = (this.getNewestSampleIdx() + 1) % this.getNextStates().getShape().getNumRows();
                break;
            default:
                System.err.println("[ERROR] ReplayBuffer.chooseSampleToEvict: unknown replacement type "
                        + this.getReplacementType());
                System.exit(-1);
        }

        return idxToEvict;
    }

    public void addSample(Matrix prevState,
            double reward,
            Matrix nextState) {

        boolean bufferFull = (this.size() >= this.getPrevStates().getShape().getNumRows());

        int evicitionIndex = -1;
        ReplacementType rt = this.getReplacementType();
        if (bufferFull) {
            evicitionIndex = this.chooseSampleToEvict();
        } else {
            evicitionIndex = this.getNewestSampleIdx() + 1;
        }

        Matrix prevStatesMat = this.getPrevStates();
        try {
            prevStatesMat.copySlice(evicitionIndex, evicitionIndex + 1, 0, prevStatesMat.getShape().getNumCols(),
                    prevState);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Matrix rewards = this.getRewards();
        rewards.set(evicitionIndex, 0, reward);

        if (nextState != null) {
            Matrix nextStatesMat = this.getNextStates();
            try {
                nextStatesMat.copySlice(evicitionIndex, evicitionIndex + 1, 0, nextStatesMat.getShape().getNumCols(),
                        nextState);
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.getIsStateTerminalMask()[evicitionIndex] = false;
        } else {
            this.getIsStateTerminalMask()[evicitionIndex] = true;
        }

        if (bufferFull) {
            if (rt == ReplacementType.OLDEST) {
                int capacity = prevStatesMat.getShape().getNumRows();
                this.setNewestSampleIdx((this.getNewestSampleIdx() + 1) % capacity);
            } else {
                this.setNewestSampleIdx(this.getRandom().nextInt(this.size()));
            }
        } else {
            this.setSize(this.size() + 1);
        }

        // This method should add a new transition (prevState, reward, nextState) to the
        // replayBuffer
        // However, we cannot just add this transition right away, we first have to
        // check that there is space!
        //
        // A replay buffer can be configured to act like a circular buffer (i.e.
        // overwrite the OLDEST transitions
        // first when we run out of space) OR it can be configured to overwrite RANDOM
        // transitions.
        // This value is already provided for you when the ReplayBuffer object is
        // created,
        // and can be accessed with the this.getReplacementType() method.

        // your method should work for both types of replacement!

        // After we determine the row index to insert this new transition into
        // there are several fields that need to be updated.
        // - We want to put the prevState in the Matrix returned by this.getPrevStates()
        // - We want to put the reward in the Matrix returned by this.getRewards()
        // - We want to put nextState in the Matrix returned by this.getNextStates() but
        // ONLY if it isnt Null!
        // Since we need to store terminal transitions (i.e. transitions that end the
        // game)
        // its possible for nextState to be null. If it is, we don't want to add it
        // - We want to update the array returned by this.getIsStateTerminalMask() with
        // whether nextState
        // is null or not. Put a true value if nextState is null, and false otherwise
        // - We want to update any indexing information that we would need to keep the
        // replacementType going
        // - if there is space left, we need to increment this.getSize()
        // - if there isn't space left and we have OLDEST replacement, we need to
        // increment this.getNewestSampleIdx
    }

    public static double max(Matrix qValues) throws IndexOutOfBoundsException {
        double maxVal = 0;
        boolean initialized = false;

        for (int colIdx = 0; colIdx < qValues.getShape().getNumCols(); ++colIdx) {
            double qVal = qValues.get(0, colIdx);
            if (!initialized || qVal > maxVal) {
                maxVal = qVal;
            }
        }
        return maxVal;
    }

    public Matrix getGroundTruth(Model qFunction,
            double discountFactor) {

        int newestIndex = this.getNewestSampleIdx();
        Matrix rewards = this.getRewards();
        Matrix prevStates = this.getPrevStates();

        Matrix nextStates = this.getNextStates();

        Matrix col_vector = Matrix.zeros(this.size(), 1);

        for (int i = 0; i < prevStates.getShape().getNumRows(); i++) {
            double r = rewards.get(i, 0);
            if (this.getIsStateTerminalMask()[i]) {
                col_vector.set(i, 0, r);
            } else {
                try {
                    double bellman_update = r + discountFactor * max(qFunction.forward(nextStates.getRow(i)));
                    col_vector.set(i, 0, bellman_update);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return col_vector;

        // TODO: complete me!

        // This method should calculate the bellman update for temporal difference
        // learning so that
        // we can use it as ground truth for updating our neural network
        //
        // Remember, the bellman ground truth we want for a Q function looks like this:
        // R(s) + \gamma * max_{a'} Q(s', a')

        // Since the number of actions is fixed in the CartPole (cp) world, we don't
        // need to include
        // action information directly in the input vector to the q function. Instead,
        // we'll make the neural
        // network always produce (in this case since there are 2 actions) 2 q values:
        // one per action.
        // So whenever we need to max_{a'} Q(s', a'), we're literally going to feed s'
        // into our network,
        // which will produce two scores, one for a_1' and one for a_2'. We can choose
        // max_{a'} Q(s', a')
        // by choosing whichever value is largest!

        // Now note that this bellman update reduces to just R(s) whenever we're
        // processing a terminal transition
        // (so s' doesn't exist).

        // This method should calculate a column vector. The number of rows in this
        // column vector is equal to the
        // number of transitions currently stored in the ReplayBuffer. Each row
        // corresponds to a transition
        // which could either be (s, r, s') or (s, r, null), so when calculating the
        // bellman update for that row,
        // you need to check the mask to see which version you're calculating!
    }

    public Pair<Matrix, Matrix> getTrainingData(Model qFunction,
            double discountFactor) {
        Matrix X = Matrix.zeros(this.size(), this.getPrevStates().getShape().getNumCols());
        try {
            for (int rIdx = 0; rIdx < this.size(); ++rIdx) {
                X.copySlice(rIdx, rIdx + 1, 0, X.getShape().getNumCols(),
                        this.getPrevStates().getRow(rIdx));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
        Matrix YGt = this.getGroundTruth(qFunction, discountFactor);

        return new Pair<Matrix, Matrix>(X, YGt);
    }

}
