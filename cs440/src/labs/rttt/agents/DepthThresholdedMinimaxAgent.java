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
import java.util.Stack;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;

// JAVA PROJECT IMPORTS
import src.labs.rttt.heuristics.Heuristics;

public class DepthThresholdedMinimaxAgent
        extends Agent {

    public static final int DEFAULT_MAX_DEPTH = 3;

    private int maxDepth;

    public DepthThresholdedMinimaxAgent(PlayerType myPlayerType) {
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

    public Node minimax(Node node) {
        if (node.isTerminal()) {
            node.setUtilityValue(node.getTerminalUtility());
            return null;
        }

        Stack<Node> S = new Stack<>();
        S.push(node);

        // We need to organize all nodes which can't be evaluated yet by layer into a
        // data structure for backtracking
        Map<Integer, Set<Node>> layers = new HashMap<>();
        layers.put(0, new HashSet<>());
        layers.get(0).add(node);

        // since getChildren regenerates children on the fly we must cache them to store
        // utility
        Map<Node, List<Node>> candidateCache = new HashMap<>();

        while (!S.isEmpty()) {
            Node currentNode = S.pop();
            List<Node> kids = currentNode.getChildren();
            candidateCache.put(currentNode, kids);

            for (Node candidateMove : kids) {

                if (candidateMove.isTerminal()) {
                    candidateMove.setUtilityValue(candidateMove.getTerminalUtility());
                    continue;
                }
                if (candidateMove.getDepth() == this.getMaxDepth()) {
                    candidateMove.setUtilityValue(
                            Heuristics.calculateHeuristicValue(candidateMove));
                    continue;
                }
                // If we couldn't evaluate it yet, we need to push it to stack and save it for
                // backtracking
                S.push(candidateMove);
                if (!layers.containsKey(candidateMove.getDepth())) {
                    layers.put(candidateMove.getDepth(), new HashSet<>());
                }
                layers.get(candidateMove.getDepth()).add(candidateMove);
            }
        }

        int processLayer = this.getMaxDepth() - 1;
        while (processLayer >= 0) {
            Set<Node> CurrentLayer = layers.get(processLayer);
            if (CurrentLayer == null) {
                processLayer -= 1;
                continue;
            }
            for (Node n : CurrentLayer) {
                boolean maxTurn = (n.getCurrentPlayerType() == this.getMyPlayerType());
                double best = maxTurn ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

                // Use cached children to avoid regenerating nodes
                List<Node> kids = candidateCache.get(n);
                for (Node child : kids) {
                    double v = child.getUtilityValue();
                    best = maxTurn ? Math.max(best, v) : Math.min(best, v);
                }
                n.setUtilityValue(best);
            }
            processLayer -= 1;
        }

        Node bestMove = null;
        double best = Double.NEGATIVE_INFINITY;
        List<Node> children = candidateCache.get(node);
        for (Node c : children) {
            double v = c.getUtilityValue();
            if (v > best) {
                best = v;
                bestMove = c;
            }
        }
        return bestMove;
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

            Node innerChoiceNode = this.minimax(new Node(gameToSetCurrentGame.getView(), this.getMyPlayerType(), 0));

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
        Node bestInnerChoiceNode = this.minimax(new Node(game, this.getMyPlayerType(), 0));
        return bestInnerChoiceNode.getLastMove();
    }

    @Override
    public void afterGameEnds(final RecursiveTicTacToeGameView game) {
    }
}
