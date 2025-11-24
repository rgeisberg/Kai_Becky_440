package src.labs.rttt.agents;

// SYSTEM IMPORTS
import edu.bu.labs.rttt.agents.Agent;
import edu.bu.labs.rttt.game.CellType;
import edu.bu.labs.rttt.game.PlayerType;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame.RecursiveTicTacToeGameView;
import edu.bu.labs.rttt.traversal.Node;
import edu.bu.labs.rttt.utils.Coordinate;
import edu.bu.labs.rttt.utils.Pair;

import java.util.List;
import java.util.Map;

// JAVA PROJECT IMPORTS
import src.labs.rttt.heuristics.Heuristics;
import src.labs.rttt.ordering.MoveOrderer;

public class DepthThresholdedAlphaBetaAgent
        extends Agent {

    public static final int DEFAULT_MAX_DEPTH = 3;

    private int maxDepth;

    public DepthThresholdedAlphaBetaAgent(PlayerType myPlayerType) {
        super(myPlayerType);
        this.maxDepth = DEFAULT_MAX_DEPTH;
    }

    public final int getMaxDepth() {
        return this.maxDepth;
    }

    public void setMaxDepth(int i) {
        this.maxDepth = i;
    }

    public String getTabs(Node node) {
        StringBuilder b = new StringBuilder();
        for (int idx = 0; idx < node.getDepth(); ++idx) {
            b.append("\t");
        }
        return b.toString();
    }

    public Node alphaBeta(Node node,
            double alpha,
            double beta) {
        // uncomment if you want to see the tree being made
        // System.out.println(this.getTabs(node) + "Node(currentPlayer=" +
        // node.getCurrentPlayerType() +
        // " isTerminal=" + node.isTerminal() + " lastMove=" + node.getLastMove() +
        // ")");

        if (node.isTerminal()) {
            node.setUtilityValue(node.getTerminalUtility());
            return node;
        }
        if (node.getDepth() == this.getMaxDepth()) {
            double h = Heuristics.calculateHeuristicValue(node);
            node.setUtilityValue(h);
            return node;
        }

        boolean maxTurn = (node.getCurrentPlayerType() == this.getMyPlayerType());
        if (maxTurn) {
            Node BestMaxChild = null;
            double best = Double.NEGATIVE_INFINITY;
            for (Node child : MoveOrderer.orderChildren(node.getChildren())) {
                Node temp = alphaBeta(child, alpha, beta);
                double v = temp.getUtilityValue();
                if (v > best) {
                    BestMaxChild = temp;
                    best = v;
                }
                alpha = Math.max(alpha, best);
                if (beta <= alpha) {
                    break;
                }
            }
            return BestMaxChild;
        } else {
            double best = Double.POSITIVE_INFINITY;
            Node BestMinChild = null;
            for (Node child : MoveOrderer.orderChildren(node.getChildren())) {
                Node temp = alphaBeta(child, alpha, beta);
                double v = temp.getUtilityValue();
                if (v < best) {
                    BestMinChild = temp;
                    best = v;
                }
                beta = Math.min(beta, best);
                if (beta <= alpha) {
                    break;
                }
            }
            return BestMinChild;
        }
    }

    @Override
    public Pair<Coordinate, Coordinate> makeFirstMove(final RecursiveTicTacToeGameView game) {
        // the first move has two choices we need to make:
        // (1) which small board do we want to play on?
        // (2) what square in the small board to we want to mark?
        // we'll solve this by iterating over all options for decision (1) and using
        // minimax over all options for (2).
        // we'll pick the answer to (1) which leads to the best utility amongst all
        // options for (1)
        // and choose the move which optimizes the choice for (1) to decide (2)
        Coordinate bestOuterBoardChoice = null;
        Double bestOuterUtility = null;
        Coordinate bestInnerBoardChoice = null;
        for (Coordinate potentialOuterBoardChoice : game.getAvailableFirstMoves().keySet()) {

            // now that we have a choice for (1) we need to convey that to the game
            // so we'll make a RecursiveTicTacToeGame object which is mutable and set
            // the current game to the potentialOuterBoardChoice
            // then we can search like normal
            RecursiveTicTacToeGame gameToSetCurrentGame = new RecursiveTicTacToeGame(game);
            gameToSetCurrentGame.setCurrentGameCoord(potentialOuterBoardChoice);

            Node innerChoiceNode = this.alphaBeta(new Node(gameToSetCurrentGame.getView(), this.getMyPlayerType(), 0),
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

            if (bestOuterUtility == null || (innerChoiceNode.getUtilityValue() > bestOuterUtility)) {
                bestOuterBoardChoice = potentialOuterBoardChoice;
                bestOuterUtility = innerChoiceNode.getUtilityValue();
                bestInnerBoardChoice = innerChoiceNode.getLastMove(); // get the move that lead to this node
            }
        }

        return new Pair<Coordinate, Coordinate>(bestOuterBoardChoice, bestInnerBoardChoice);
    }

    @Override
    public Coordinate makeOtherMove(final RecursiveTicTacToeGameView game) {
        Node bestInnerChoiceNode = this.alphaBeta(new Node(game, this.getMyPlayerType(), 0),
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return bestInnerChoiceNode.getLastMove();
    }

    @Override
    public void afterGameEnds(final RecursiveTicTacToeGameView game) {
    }
}
