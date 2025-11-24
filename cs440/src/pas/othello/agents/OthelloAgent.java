package src.pas.othello.agents;

// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// JAVA PROJECT IMPORTS
import edu.bu.pas.othello.agents.Agent;
import edu.bu.pas.othello.agents.TimedTreeSearchAgent;
import edu.bu.pas.othello.game.Game;
import edu.bu.pas.othello.game.Game.GameView;
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;
import src.pas.othello.heuristics.Heuristics;
import src.pas.othello.ordering.MoveOrderer;

import java.util.Set;

public class OthelloAgent
        extends TimedTreeSearchAgent {

    public static class OthelloNode
            extends Node {

        public OthelloNode(final PlayerType maxPlayerType, // who is MAX (me)
                final GameView gameView, // current state of the game
                final int depth) // the depth of this node
        {
            super(maxPlayerType, gameView, depth);
        }

        @Override
        public double getTerminalUtility() {
            PlayerType[][] cells = getGameView().getCells();
            double myCount = 0d;
            double opCount = 0d;
            for (int r = 0; r < cells.length; r++) {
                for (int c = 0; c < cells[0].length; c++) {
                    if (cells[r][c] == getMaxPlayerType()) {
                        myCount += 1d;
                    } else if (cells[r][c] == getOtherPlayerType()) {
                        opCount += 1d;
                    }
                }
            }
            if (opCount == 0d) {
                return 64d;
            } else if (myCount == 0d) {
                return -64d;
            }
            return myCount - opCount;
        }

        @Override
        public List<Node> getChildren() {
            PlayerType currentPlayer = this.getGameView().getCurrentPlayerType();
            Set<Coordinate> myLegalMoves = this.getGameView().getFrontier(currentPlayer);
            List<Node> children = new ArrayList<Node>();

            if (myLegalMoves.size() == 0) {
                // edge case no legal moves but non terminal
                Game temp = new Game(this.getGameView());
                temp.applyMove(null);
                temp.setCurrentPlayerType(temp.getOtherPlayerType());
                if (currentPlayer == PlayerType.WHITE) {
                    temp.setTurnNumber(temp.getTurnNumber() + 1);
                }
                OthelloNode childNode = new OthelloNode(this.getMaxPlayerType(),
                        temp.getView(), this.getDepth() + 1);
                childNode.setLastMove(null);
                children.add(childNode);
            } else {
                for (Coordinate move : myLegalMoves) {
                    Game temp = new Game(this.getGameView());
                    temp.applyMove(move);
                    temp.setCurrentPlayerType(temp.getOtherPlayerType());
                    if (currentPlayer == PlayerType.WHITE) {
                        temp.setTurnNumber(temp.getTurnNumber() + 1);
                    }
                    OthelloNode childNode = new OthelloNode(this.getMaxPlayerType(),
                            temp.getView(), this.getDepth() + 1);
                    childNode.setLastMove(move);
                    children.add(childNode);
                }
            }

            return children;
        }
    }

    // Keep Track of time
    public class TimeBudget {
        private final long deadlineNanos;

        TimeBudget(long moveMs, long timeLimitMs) {
            long now = System.nanoTime();
            this.deadlineNanos = now + timeLimitMs * 1_000_000L;
        }

        boolean timeUp() {
            return System.nanoTime() >= deadlineNanos;
        }

        long remainingTimeHard() {
            return (deadlineNanos - System.nanoTime()) / 1_000_000L;
        }
    }

    static final class SearchStats {
        long nodesThisDepth = 0;
        double emaNps = 0.0;
        int heuristicUsed = 0;
        int terminalUsed = 0;

        void onNode() {
            nodesThisDepth++;
        }

        void finishDepth(long elapsedMs) {
            double inst = (elapsedMs > 0) ? (1000.0 * nodesThisDepth) / elapsedMs : 0.0;
            emaNps = (emaNps == 0.0) ? inst : 0.7 * emaNps + 0.3 * inst;
        }

        void onHeuristic() {
            heuristicUsed++;
        }

        void resetHeuristic() {
            heuristicUsed = 0;
        }

        void onTerminal() {
            terminalUsed++;
        }

        void resetTerminal() {
            terminalUsed = 0;
        }
    }

    private boolean timeExpired = false;

    // exception to break alphaBeta to ensure we don't exceed time limit
    static final class TimeUp extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static boolean canAffordNextDepth(int currentDepth, long remainingMs, long safetyBufferMs,
            int nodesAtCurrentDepth, int nodesAtPreviousDepth, double avgNodesPerSecond) {
        if (remainingMs <= safetyBufferMs) {
            return false;
        }
        double estimatedBranching = (double) nodesAtCurrentDepth / Math.max(1L, nodesAtPreviousDepth);

        // pessimistic estimates (assume that branching will increase)
        double conservativeBranching = Math.max(1.005, estimatedBranching);

        double predictedNodesNextDepth = nodesAtCurrentDepth * conservativeBranching;

        // assume that search speed will increase (based on observed behavior)
        double adjustedAvgNodesPerSecond = avgNodesPerSecond * 1.4;

        // convert to ms
        double predictedMillisNeeded = 1000.0 * (predictedNodesNextDepth / adjustedAvgNodesPerSecond);

        System.out.println("Predicted millis needed for depth " + (currentDepth + 1) + ": " + predictedMillisNeeded);

        return predictedMillisNeeded < (remainingMs - safetyBufferMs);
    }

    private final Random random;

    public OthelloAgent(final PlayerType myPlayerType,
            final long maxMoveThinkingTimeInMS) {
        super(myPlayerType, maxMoveThinkingTimeInMS);
        this.random = new Random();
    }

    public final Random getRandom() {
        return this.random;
    }

    @Override
    public OthelloNode makeRootNode(final GameView game) {
        // if you change OthelloNode's constructor, you will want to change this!
        // Note: I am starting the initial depth at 0 (because I like to count up)
        // change this if you want to count depth differently
        // note that at the root its the max players turn to move
        return new OthelloNode(this.getMyPlayerType(), game, 0);
    }

    public Node alphaBeta(Node n, double alpha, double beta, int maxDepth, TimeBudget t, SearchStats stats) {
        stats.onNode();
        if (t.timeUp()) {
            timeExpired = true;
            throw new TimeUp();
        }
        if (n.isTerminal()) {
            stats.onTerminal();
            n.setUtilityValue(n.getTerminalUtility());
            return n;
        }
        if (n.getDepth() == maxDepth) {
            stats.onHeuristic();
            n.setUtilityValue(Heuristics.calculateHeuristicValue(n));
            return n;
        }
        if (n.getGameView().getCurrentPlayerType() == n.getMaxPlayerType()) {
            double bestVal = Double.NEGATIVE_INFINITY;
            Node bestNode = null;
            Node bestChild = null;
            List<Node> orderedChildren = MoveOrderer.orderChildren(n.getChildren());

            for (Node child : orderedChildren) {
                if (t.timeUp()) {
                    timeExpired = true;
                    throw new TimeUp();
                }
                Node best = alphaBeta(child, alpha, beta, maxDepth, t, stats);
                double value = best.getUtilityValue();
                if (value > bestVal) {
                    bestVal = value;
                    bestChild = child;
                    bestNode = best;
                }
                alpha = Math.max(alpha, bestVal);
                if (beta <= alpha) {
                    break;
                }

            }
            bestChild.setUtilityValue(bestVal);
            return bestChild;

        } else {
            double bestVal = Double.POSITIVE_INFINITY;
            Node bestMinNode = null;
            Node bestChild = null;
            List<Node> orderedChildren = MoveOrderer.orderChildren(n.getChildren());
            for (Node child : orderedChildren) {
                if (t.timeUp()) {
                    timeExpired = true;
                    throw new TimeUp();
                }
                Node best = alphaBeta(child, alpha, beta, maxDepth, t, stats);
                double value = best.getUtilityValue();

                if (value < bestVal) {
                    bestVal = value;
                    bestChild = child;
                    bestMinNode = best;
                }
                beta = Math.min(beta, bestVal);
                if (beta <= alpha) {
                    break;
                }

            }
            bestChild.setUtilityValue(bestVal);
            n.setUtilityValue(bestVal);
            return bestChild;
        }
    }

    @Override
    public Node treeSearch(Node n) {
        final long totalMoveMs = this.getMaxThinkingTimeInMS();
        final long hardBufferMs = 25;

        TimeBudget t = new TimeBudget(totalMoveMs, totalMoveMs - hardBufferMs);

        Node bestMoveSoFar = null;
        int lastCompletedDepth = 0;

        timeExpired = false;

        long nodesAtPreviousDepth = 550;
        double avgNodesPerSecond = 0.0;

        int startDepth = 5;

        for (int depth = startDepth; depth < 60; depth++) {
            long t0 = System.nanoTime();
            SearchStats stats = new SearchStats();
            try {
                Node bestMoveThisIteration = alphaBeta(
                        n,
                        Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY,
                        depth,
                        t,
                        stats);

                bestMoveSoFar = bestMoveThisIteration;
                lastCompletedDepth = depth;
                if (stats.heuristicUsed == 0) {
                    break;
                }

            } catch (TimeUp e) {
                break;
            }

            long elapsedMs = Math.max(1L, (System.nanoTime() - t0) / 1_000_000L);
            if (timeExpired) {
                break;
            }
            stats.finishDepth(elapsedMs);

            long nodesThisDepth = stats.nodesThisDepth;
            avgNodesPerSecond = stats.emaNps;

            long remainingMs = t.remainingTimeHard();

            System.out.println("Completed depth " + depth +
                    " nodes: " + nodesThisDepth +
                    " time: " + elapsedMs + "ms" +
                    " rem: " + remainingMs + "ms" +
                    " nps: " + (int) avgNodesPerSecond +
                    " heuristics: " + stats.heuristicUsed +
                    " terminals: " + stats.terminalUsed);

            stats.resetHeuristic();
            stats.resetTerminal();
            boolean canContinue = canAffordNextDepth(
                    lastCompletedDepth,
                    remainingMs,
                    hardBufferMs,
                    (int) nodesThisDepth,
                    (int) nodesAtPreviousDepth,
                    avgNodesPerSecond);

            if (!canContinue) {
                break;
            }

            nodesAtPreviousDepth = nodesThisDepth;
        }
        if (bestMoveSoFar == null) {
            List<Node> kids = n.getChildren();
            if (kids == null || kids.isEmpty()) {
                // No valid moves available - this should be a terminal state
                // Return the root node, but this indicates a game-ending condition
                return n;
            }
            // Return the first child (which represents a move) after ordering
            return MoveOrderer.orderChildren(kids).get(0);
        }
        return bestMoveSoFar;
    }

    @Override
    public Coordinate chooseCoordinateToPlaceTile(final GameView game) {
        // TODO: this move will be called once per turn
        // you may want to use this method to add to data structures and whatnot
        // that your algorithm finds useful

        // make the root node
        Node node = this.makeRootNode(game);

        // call tree search
        Node moveNode = this.treeSearch(node);

        // return the move inside that node
        Coordinate move = moveNode.getLastMove();

        // Safety check: if we got a null move, try to get the first legal move
        if (move == null) {
            System.err.println("Warning: treeSearch returned null move, falling back to first legal move");
            List<Node> children = node.getChildren();
            if (children != null && !children.isEmpty()) {
                return children.get(0).getLastMove();
            }
            // If still null, there might be no legal moves (game over?)
            System.err.println("Error: No legal moves available!");
        }

        return move;
    }

    @Override
    public void afterGameEnds(final GameView game) {
    }

    private static void ensureTurn(Game g, PlayerType pt) {
        if (g.getCurrentPlayerType() != pt) {
            g.playTurn();
        }
    }
}
