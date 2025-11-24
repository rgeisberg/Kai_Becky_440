package src.pas.othello.heuristics;

// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;
import edu.bu.pas.othello.game.PlayerType;

// JAVA PROJECT IMPORTS
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Heuristics
        extends Object {
    private static double d(String key, double def) {
        return Double.parseDouble(System.getProperty(key, Double.toString(def)));
    }

    // Class-level constants, read once per JVM at class-load time
    private static final double BASE_PST = d("heur.basePST", 0.35);
    private static final double BASE_MOB = d("heur.baseMob", 0.35);
    private static final double BASE_POTMOB = d("heur.basePotMob", 0.25);
    private static final double BASE_CAP = d("heur.baseCap", 0.3);
    private static final double BASE_CORNER = d("heur.baseCorner", 0.35);
    private static final double BASE_EDGE = d("heur.baseEdge", 0.35);
    private static final double BASE_PARITY = d("heur.baseParity", 0.45);
    private static final double BASE_DIAG = d("heur.baseDiag", 0.10);

    public static double calculateHeuristicValue(Node node) {
        int numOpenTiles = numOpenTiles(node);
        float percentageOpen = numOpenTiles / 60f;
        float progress = 1f - percentageOpen;

        double score = currentScore(node);
        double parity = score / 64.0;

        int MynumCorners = cornerControl(node, node.getMaxPlayerType());
        int OPnumCorners = cornerControl(node, node.getOtherPlayerType());
        int cornerDiff = MynumCorners - OPnumCorners;
        double numCorners = cornerDiff / 4.0;

        int Myflexibility = flexibility(node, node.getMaxPlayerType());
        int OPflexibility = flexibility(node, node.getOtherPlayerType());

        double mobility;
        if (Myflexibility + OPflexibility == 0) {
            mobility = 0;
        } else {
            mobility = (double) (Myflexibility - OPflexibility) / (Myflexibility + OPflexibility);
        }

        // New terms (all ≤ Θ(n²))
        int pst = pstScore(node);
        int cap = cornerAdjacencyScore(node);
        int edgeStable = stableEdgeApprox(node);
        int potMe = potentialMobility(node, node.getMaxPlayerType());
        int potOp = potentialMobility(node, node.getOtherPlayerType());
        double potMob = (potMe + potOp == 0) ? 0.0 : (double) (potMe - potOp) / (potMe + potOp);

        // double wMob = 4*progress * (1-percentageOpen);
        // double wCorner = progress * progress;
        // double wParity = progress;

        // double hNorm = 1 * numCorners + 0 * mobility + 0 * parity;

        // double h = 64.0*hNorm;
        // if(h>64){
        // h=64;
        // }
        // if(h< -64){
        // h=-64;
        // }
        // return h;

        // Normalize to [-1,1]

        final double PST_MAX = 376.0; // sum of your PST positives on an 8x8
        final double CAP_MAX = 180.0; // 4 * (25 + 10 + 10) worst case swing
        final double EDGE_MAX = 192.0; // 4 corners * 2 edges * 8 cells * weight 3

        double pstNorm = clamp(pst / PST_MAX, -1.0, 1.0);
        double capNorm = clamp(cap / CAP_MAX, -1.0, 1.0);
        double edgeNorm = clamp(edgeStable / EDGE_MAX, -1.0, 1.0);

        // Define the phase-adaptive formulas (still using progress)
        double wPST = BASE_PST;// * (1.0 - progress); // strong early
        double wMob = BASE_MOB;
        double wPotMob = BASE_POTMOB;
        double wCap = BASE_CAP;
        double wCorner = BASE_CORNER;
        double wEdge = BASE_EDGE;
        double wParity = BASE_PARITY * progress; // strongest very late

        double wSum = wPST + wMob + wPotMob + wCap + wCorner + wEdge + wParity;
        // --- Combine to hNorm ∈ [-1,1]
        double hNorm = (wPST * pstNorm +
                wMob * mobility + // already in [-1,1]
                wPotMob * potMob + // already in [-1,1]
                wCap * capNorm +
                wCorner * numCorners + // in [-1,1]
                wEdge * edgeNorm +
                wParity * parity) // in [-1,1]
                / Math.max(1e-9, wSum);

        // --- Scale to utility range and clamp
        double h = 64.0 * hNorm;
        if (h > 64)
            h = 64;
        if (h < -64)
            h = -64;
        return h;
    }

    public static int numOpenTiles(Node node) {
        // helper function to return number of unplayed tiles
        PlayerType cells[][] = node.getGameView().getCells();
        int numEmpty = 0;
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < cells[0].length; c++) {
                if (cells[r][c] == null) {
                    numEmpty += 1;
                }

            }
        }
        return numEmpty;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    public static double currentScore(Node node) {
        // current score
        PlayerType[][] cells = node.getGameView().getCells();
        double myCount = 0;
        double opCount = 0;
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < cells[0].length; c++) {
                if (cells[r][c] == node.getMaxPlayerType()) {
                    myCount += 1;
                } else if (cells[r][c] == node.getOtherPlayerType()) {
                    opCount += 1;
                }
            }
        }
        return myCount - opCount;

    }

    public static int cornerControl(Node node, PlayerType playerType) {
        // how many corners we control
        Coordinate topLeft = new Coordinate(0, 0);
        Coordinate topRight = new Coordinate(0, 7);
        Coordinate bottomLeft = new Coordinate(7, 0);
        Coordinate bottomRight = new Coordinate(7, 7);
        int numCorners = 0;
        if (node.getGameView().getCell(topLeft) == playerType) {
            numCorners += 1;
        }
        if (node.getGameView().getCell(topRight) == playerType) {
            numCorners += 1;
        }
        if (node.getGameView().getCell(bottomLeft) == playerType) {
            numCorners += 1;
        }
        if (node.getGameView().getCell(bottomRight) == playerType) {
            numCorners += 1;
        }
        return numCorners;
    }

    public static int flexibility(Node n, PlayerType playerType) {
        // difference in frontier size between us and our enemy
        int myFronteirSize = n.getGameView().getFrontier(playerType).size();
        return myFronteirSize;
    }
    // public static HashMap<Integer,Double> security(Node node){
    // HashMap<Integer, Double> securityMap = new HashMap<>();

    // }

    // Standard PST for Othello (symmetric), tweak as you like
    private static final int[][] PST = {
            { 120, -20, 20, 5, 5, 20, -20, 120 },
            { -20, -40, -5, -5, -5, -5, -40, -20 },
            { 20, -5, 15, 3, 3, 15, -5, 20 },
            { 5, -5, 3, 3, 3, 3, -5, 5 },
            { 5, -5, 3, 3, 3, 3, -5, 5 },
            { 20, -5, 15, 3, 3, 15, -5, 20 },
            { -20, -40, -5, -5, -5, -5, -40, -20 },
            { 120, -20, 20, 5, 5, 20, -20, 120 }
    };

    private static int pstScore(Node node) {
        PlayerType[][] cells = node.getGameView().getCells();
        PlayerType me = node.getMaxPlayerType(), opp = node.getOtherPlayerType();
        int s = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (cells[r][c] == me)
                    s += PST[r][c];
                else if (cells[r][c] == opp)
                    s -= PST[r][c];
            }
        }
        return s;
    }

    private static int stableEdgeApprox(Node n) {
        PlayerType[][] a = n.getGameView().getCells();
        PlayerType me = n.getMaxPlayerType();
        PlayerType opp = n.getOtherPlayerType();
        int s = 0;

        s += scanEdge(a, me, opp, 0, 0, 0, +1); // top row, → right
        s += scanEdge(a, me, opp, 0, 0, +1, 0); // left col, ↓ down
        s += scanEdge(a, me, opp, 0, 7, 0, -1); // top row, left
        s += scanEdge(a, me, opp, 0, 7, +1, 0); // right col, ↓ down
        s += scanEdge(a, me, opp, 7, 0, 0, +1); // bottom row, → right
        s += scanEdge(a, me, opp, 7, 0, -1, 0); // left col, ↑ up
        s += scanEdge(a, me, opp, 7, 7, 0, -1); // bottom row, left
        s += scanEdge(a, me, opp, 7, 7, -1, 0); // right col, ↑ up

        return s;
    }

    private static int scanEdge(PlayerType[][] a, PlayerType me, PlayerType opp,
            int sr, int sc, int dr, int dc) {
        PlayerType corner = a[sr][sc];
        int score = 0;
        if (corner == null)
            return 0;

        int r = sr, c = sc;
        if (corner == me) {
            while (r >= 0 && r < 8 && c >= 0 && c < 8 && a[r][c] == me) {
                score += 3;
                r += dr;
                c += dc;
            }
        } else if (corner == opp) {
            while (r >= 0 && r < 8 && c >= 0 && c < 8 && a[r][c] == opp) {
                score -= 3;
                r += dr;
                c += dc;
            }
        }
        return score;
    }

    private static int cornerAdjacencyScore(Node n) {
        PlayerType[][] a = n.getGameView().getCells();
        PlayerType me = n.getMaxPlayerType(), opp = n.getOtherPlayerType();
        int s = 0;

        // For each corner, define its X and C squares
        int[][] corners = { { 0, 0 }, { 0, 7 }, { 7, 0 }, { 7, 7 } };
        int[][][] xs = {
                { { 1, 1 } }, { { 1, 6 } }, { { 6, 1 } }, { { 6, 6 } }
        };
        int[][][] cs = {
                { { 0, 1 }, { 1, 0 } }, { { 0, 6 }, { 1, 7 } }, { { 6, 0 }, { 7, 1 } }, { { 6, 7 }, { 7, 6 } }
        };

        for (int k = 0; k < 4; k++) {
            int cr = corners[k][0], cc = corners[k][1];
            PlayerType corner = a[cr][cc];

            // If corner empty, owning adjacent is risky → penalty
            if (corner == null) {
                for (int[] p : xs[k]) {
                    if (a[p[0]][p[1]] == me)
                        s -= 25;
                    if (a[p[0]][p[1]] == opp)
                        s += 25;
                }
                for (int[] p : cs[k]) {
                    if (a[p[0]][p[1]] == me)
                        s -= 10;
                    if (a[p[0]][p[1]] == opp)
                        s += 10;
                }
            } else if (corner == n.getMaxPlayerType()) {
                // If we own the corner, these neighbors become safe-ish → small bonus
                for (int[] p : xs[k])
                    if (a[p[0]][p[1]] == me)
                        s += 5;
                for (int[] p : cs[k])
                    if (a[p[0]][p[1]] == me)
                        s += 5;
            }
        }
        return s;
    }

    private static final int[][] DIRS = {
            { -1, -1 }, { -1, 0 }, { -1, 1 }, { 0, -1 }, { 0, 1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }
    };

    private static int potentialMobility(Node n, PlayerType me) {
        PlayerType[][] a = n.getGameView().getCells();
        PlayerType opp = (me == n.getMaxPlayerType()) ? n.getOtherPlayerType() : n.getMaxPlayerType();
        boolean[][] seen = new boolean[8][8];
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (a[r][c] != null)
                    continue;
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8 && a[nr][nc] == opp) {
                        if (!seen[r][c]) {
                            seen[r][c] = true;
                            count++;
                        }
                        break;
                    }
                }
            }
        }
        return count; // 0..60
    }

    private static int stableDiagApprox(Node n) {
        PlayerType[][] a = n.getGameView().getCells();
        PlayerType me = n.getMaxPlayerType(), opp = n.getOtherPlayerType();
        int s = 0;
        // A1 ↘
        s += scanDiag(a, me, opp, 0, 0, +1, +1);
        // A8 ↗
        s += scanDiag(a, me, opp, 7, 0, -1, +1);
        // H1 ↖
        s += scanDiag(a, me, opp, 0, 7, +1, -1);
        // H8 ↙
        s += scanDiag(a, me, opp, 7, 7, -1, -1);
        return s;
    }

    private static int scanDiag(PlayerType[][] a, PlayerType me, PlayerType opp,
            int sr, int sc, int dr, int dc) {
        PlayerType corner = a[sr][sc];
        if (corner == null)
            return 0;
        int score = 0, r = sr, c = sc;
        if (corner == me) {
            while (r >= 0 && r < 8 && c >= 0 && c < 8 && a[r][c] == me) {
                score += 3;
                r += dr;
                c += dc;
            }
        } else {
            while (r >= 0 && r < 8 && c >= 0 && c < 8 && a[r][c] == opp) {
                score -= 3;
                r += dr;
                c += dc;
            }
        }
        return score;
    }

}
