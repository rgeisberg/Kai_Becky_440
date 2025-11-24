package src.labs.rttt.heuristics;

// SYSTEM IMPORTS
import edu.bu.labs.rttt.game.CellType;
import edu.bu.labs.rttt.game.PlayerType;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame.RecursiveTicTacToeGameView;
import edu.bu.labs.rttt.traversal.Node;
import edu.bu.labs.rttt.utils.Coordinate;
import edu.bu.labs.rttt.utils.Pair;

// JAVA PROJECT IMPORTS

public class Heuristics
        extends Object {

    public static int countNumberOfTicTacToeGamesWon(RecursiveTicTacToeGameView view,
            PlayerType playerType) {
        int numGamesWon = 0;
        for (int rIdx = 0; rIdx < view.getNumRows(); ++rIdx) {
            for (int cIdx = 0; cIdx < view.getNumCols(); ++cIdx) {
                if (view.getOutcome(rIdx, cIdx) == playerType) {
                    numGamesWon += 1;
                }
            }
        }
        return numGamesWon;
    }

    public static double calculateHeuristicValue(Node node) {
        // our (poor) heuristic is just going to count how many individual tic-tac-toe
        // games we've won so far
        // +10 for each won game
        // -10 for each lost game
        // +0 for each tie/undetermined game
        // note that the extrema of our utility values are -100 (if we lose) and +100
        // (if we win). The heuristic
        // value should NEVER match these extrema NOR should it fall out of the bound
        // (-100, +100) exclusive.
        return 10.0 * countNumberOfTicTacToeGamesWon(node.getView(), node.getMyPlayerType()) +
                -10.0 * countNumberOfTicTacToeGamesWon(node.getView(), node.getOppositePlayerType());
    }

}
