package src.pas.pacman.agents;

// SYSTEM IMPORTS
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Collections;

// JAVA PROJECT IMPORTS
import edu.bu.pas.pacman.agents.Agent;
import edu.bu.pas.pacman.agents.SearchAgent;
import edu.bu.pas.pacman.interfaces.ThriftyPelletEater;
import edu.bu.pas.pacman.game.Action;
import edu.bu.pas.pacman.game.Game.GameView;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.graph.PelletGraph.PelletVertex;
import edu.bu.pas.pacman.utils.Coordinate;
import edu.bu.pas.pacman.utils.Pair;

public class PacmanAgent
        extends SearchAgent
        implements ThriftyPelletEater {

    // Helper class for edge weights (for mst)
    static final class Edge implements Comparable<Edge> {
        Coordinate u, v;
        final float weight;

        Edge(Coordinate u, Coordinate v, float weight) {
            this.u = u;
            this.v = v;
            this.weight = weight;
        }

        @Override
        public int compareTo(Edge other) {
            return Float.compare(this.weight, other.weight);
        }
    }

    private final Random random;
    private static final int K_DEFAULT = 4;

    public PacmanAgent(int myUnitId,
            int pacmanId,
            int ghostChaseRadius) {
        super(myUnitId, pacmanId, ghostChaseRadius);
        this.random = new Random();
    }

    public final Random getRandom() {
        return this.random;
    }

    @Override
    public Set<PelletVertex> getOutoingNeighbors(final PelletVertex vertex,
            final GameView game) {

        Set<PelletVertex> Neighbors = new HashSet<>();
        for (Coordinate p : vertex.getRemainingPelletCoordinates()) {
            PelletVertex temp = vertex.removePellet(p);
            Neighbors.add(temp);
        }
        return Neighbors;
    }

    @Override
    public float getEdgeWeight(final PelletVertex src,
            final PelletVertex dst) {
        if (src.equals(dst)) {
            return 0f;
        }
        Set<Coordinate> srcCoords = src.getRemainingPelletCoordinates();
        Set<Coordinate> dstCoords = dst.getRemainingPelletCoordinates();

        Coordinate snackCandidate = null;
        for (Coordinate c : srcCoords) {
            if (!dstCoords.contains(c)) {
                snackCandidate = c;
                break;
            }
        }

        Coordinate pacmanCoord = src.getPacmanCoordinate();
        if (pacmanCoord.equals(snackCandidate)) {
            return 0f;
        }
        Map<MyPair<Coordinate, Coordinate>, Float> trueMazeDistance = getTrueMazeDistance();
        MyPair<Coordinate, Coordinate> key = new MyPair<>(pacmanCoord, snackCandidate);
        return trueMazeDistance.get(key);
    }

    @Override
    public float getHeuristic(final PelletVertex src,
            final GameView game) {
        float weightMST = 0f;
        List<Edge> edges = new ArrayList<>();
        Set<Coordinate> remaining = new HashSet<>(src.getRemainingPelletCoordinates());
        remaining.add(src.getPacmanCoordinate());
        List<Coordinate> nodes = new ArrayList<>(remaining); // to access by index

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) { // to avoid double counting
                Coordinate u = nodes.get(i);
                Coordinate v = nodes.get(j);
                // take manhattan distance between points as weight (this is an underestimate)
                float weight = (Math.abs(u.getXCoordinate() - v.getXCoordinate()) +
                        Math.abs(u.getYCoordinate() - v.getYCoordinate()));
                edges.add(new Edge(u, v, weight));
            }
        }
        java.util.Collections.sort(edges); // O(nlog(n))

        Set<Coordinate> inMST = new HashSet<>();
        // Kruskal's algorithm to find weight of MST
        for (Edge e : edges) {
            Coordinate u = e.u;
            Coordinate v = e.v;
            if (!inMST.contains(u) || !inMST.contains(v)) {
                weightMST += e.weight;
                inMST.add(u);
                inMST.add(v);
            }
        }
        return weightMST;
    }

    public class MyPair<F, S> {
        private F first;
        private S second;

        public MyPair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MyPair<?, ?> myPair = (MyPair<?, ?>) o;
            return java.util.Objects.equals(first, myPair.first) &&
                    java.util.Objects.equals(second, myPair.second);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(first, second);
        }
    }

    // Helper for A*
    public Path<PelletVertex> AstarK(final PelletVertex start, final PelletVertex goal, final GameView game,
            final int k) {

        Map<PelletVertex, Float> gScore = new HashMap<>(); // stores the (true) cost to reach a node
        gScore.put(start, 0f);

        Map<PelletVertex, PelletVertex> parents = new HashMap<>(); // stores the parent of each node for path
                                                                   // reconstruction
        parents.put(start, null);
        Set<PelletVertex> closedSet = new HashSet<>(); // stores the nodes with finalized best cost

        PriorityQueue<MyPair<Float, PelletVertex>> open = new PriorityQueue<>(
                Comparator.comparing((MyPair<Float, PelletVertex> p) -> p.getFirst()));

        open.add(new MyPair<>(getHeuristic(start, game), start));

        Map<PelletVertex, Integer> depth = new HashMap<>();
        depth.put(start, 0);

        while (!open.isEmpty()) {
            MyPair<Float, PelletVertex> currentPair = open.poll();
            PelletVertex current = currentPair.getSecond();

            // We need to manually simulate decrease-key operation which don't exist in Java
            // by default
            float bestCurrentF = gScore.getOrDefault(current, Float.MAX_VALUE)
                    + getHeuristic(current, game);
            if (currentPair.getFirst() > bestCurrentF) {
                continue;
            }

            if ((current.getRemainingPelletCoordinates()).equals(goal.getRemainingPelletCoordinates())) {
                return constructPath(parents, start, current);
            }
            if (depth.get(current) >= k) {
                return constructPath(parents, start, current);
            }

            if (closedSet.contains(current)) {
                continue;
            } else {
                closedSet.add(current);
            }

            Set<PelletVertex> children = getOutoingNeighbors(current, game);
            children.remove(current);
            for (PelletVertex child : children) {
                if (closedSet.contains(child)) {
                    continue;
                }
                float currentBestG = gScore.get(current) + getEdgeWeight(current, child); // true cost so far

                if (currentBestG < gScore.getOrDefault(child, Float.MAX_VALUE)) {
                    parents.put(child, current);
                    gScore.put(child, currentBestG);
                    depth.put(child, depth.get(current) + 1);
                    float f = currentBestG + getHeuristic(child, game);
                    open.add(new MyPair<>(f, child)); // add the child for analysis
                }
            }
        }
        return null;
    }

    public Path<PelletVertex> constructPath(Map<PelletVertex, PelletVertex> parents, PelletVertex start,
            PelletVertex goal) {
        List<PelletVertex> s = new ArrayList<>();
        s.add(goal);
        while (!goal.equals(start)) {
            goal = parents.get(goal);
            if (goal == null) {
                return null; // shouldn't happen but in case
            }
            s.add(goal);
        }
        Collections.reverse(s);
        Path<PelletVertex> path = new Path<>(s.get(0));
        for (int i = 1; i < s.size(); i++) {
            PelletVertex prev = s.get(i - 1);
            PelletVertex next = s.get(i);
            float weight = getEdgeWeight(prev, next);
            path = new Path<>(next, weight, path);
        }
        return path;
    }

    private Map<MyPair<Coordinate, Coordinate>, Float> trueMazeDistance = new HashMap<>();

    public Map<MyPair<Coordinate, Coordinate>, Float> getTrueMazeDistance() {
        return this.trueMazeDistance;
    }

    public void setTrueMazeDistance(Map<MyPair<Coordinate, Coordinate>, Float> m) {
        this.trueMazeDistance = m;
    }

    public Path<PelletVertex> appendPelletPaths(Path<PelletVertex> a,
            Path<PelletVertex> b) {
        List<PelletVertex> seq = new ArrayList<>();
        for (Path<PelletVertex> t = b; t != null; t = t.getParentPath()) {
            seq.add(t.getDestination());
        }
        Collections.reverse(seq);
        PelletVertex tailA = a.getDestination();
        int join = -1;
        for (int i = 0; i < seq.size(); i++) {
            if (seq.get(i).equals(tailA)) {
                join = i;
                break;
            }
        }
        if (join == -1) {
            return a;
        }
        Path<PelletVertex> out = a;
        PelletVertex prev = tailA;

        for (int i = join + 1; i < seq.size(); i++) {
            PelletVertex nxt = seq.get(i);
            float w = getEdgeWeight(prev, nxt);
            out = new Path<>(nxt, w, out);
            prev = nxt;
        }

        return out;
    }

    @Override
    public Path<PelletVertex> findPathToEatAllPelletsTheFastest(final GameView game) {
        // find all coordinates reachable from pacman
        Set<Coordinate> initCoordinates = new HashSet<>();
        Coordinate pacmanCoord = game.getEntity(this.getPacmanId()).getCurrentCoordinate();
        initCoordinates.add(pacmanCoord);
        Set<Coordinate> tooExplore = new HashSet<>();
        tooExplore.add(pacmanCoord);
        while (!tooExplore.isEmpty()) {
            Coordinate current = tooExplore.iterator().next();
            tooExplore.remove(current);
            Set<Coordinate> neighbors = getOutgoingNeighbors(current, game);
            for (Coordinate neighbor : neighbors) {
                if (!initCoordinates.contains(neighbor)) {
                    initCoordinates.add(neighbor);
                    tooExplore.add(neighbor);
                }
            }
        }
        // store the distance from every coordinate to every other coordinate in O(n^2)
        // time
        Map<MyPair<Coordinate, Coordinate>, Float> trueMazeDistance = new HashMap<>();

        for (Coordinate c : initCoordinates) {
            Set<Coordinate> Visited = new HashSet<>();
            Float cost = 1f;
            Set<Coordinate> toExplore = new HashSet<>();
            toExplore.add(c);
            Set<Coordinate> nextlayer = new HashSet<>();

            while (!toExplore.isEmpty()) {
                for (Coordinate u : toExplore) {
                    Visited.add(u);
                    Set<Coordinate> neighbors = getOutgoingNeighbors(u, game);
                    for (Coordinate v : neighbors) {
                        if (!Visited.contains(v)) {
                            trueMazeDistance.put(new MyPair<>(c, v), cost);
                            nextlayer.add(v);
                        }
                    }
                }
                cost += 1f;
                toExplore = nextlayer;
                nextlayer = new HashSet<>();
            }
        }
        setTrueMazeDistance(trueMazeDistance);

        PelletVertex start = new PelletVertex(game);
        // the goal is the state with no remaining pellets
        PelletVertex goal = new PelletVertex(game);
        for (Coordinate p : start.getRemainingPelletCoordinates()) {
            goal = goal.removePellet(p);
        }

        final int k = K_DEFAULT;
        Path<PelletVertex> fullPath = null;
        PelletVertex curr = start;
        int safety = 0;
        final int MAX_STEPS = 100000;
        // chain depth limited A* searches until all pellets are eaten
        while (!curr.equals(goal)) {
            Path<PelletVertex> prefix = AstarK(curr, goal, game, Integer.MAX_VALUE);
            if (prefix == null) {
                return fullPath;
            }
            if (fullPath == null) {
                fullPath = prefix;
            } else {
                fullPath = appendPelletPaths(fullPath, prefix);
            }
            PelletVertex last = prefix.getDestination();
            if (last.equals(curr))
                break;

            curr = last;
            if (++safety > MAX_STEPS)
                break;
        }
        List<Coordinate> removalOrder = extractPelletRemovalOrder(fullPath, true);
        PelletVertex total = new PelletVertex(game);
        return fullPath;
    }

    @Override
    public Set<Coordinate> getOutgoingNeighbors(final Coordinate src,
            final GameView game) {
        Set<Coordinate> neighbors = new HashSet<>();
        int x = src.getXCoordinate();
        int y = src.getYCoordinate();
        if (game.isLegalPacmanMove(src, Action.NORTH)) {
            Coordinate n = new Coordinate(x, y - 1);
            neighbors.add(n);
        }
        if (game.isLegalPacmanMove(src, Action.WEST)) {
            Coordinate w = new Coordinate(x - 1, y);
            neighbors.add(w);
        }
        if (game.isLegalPacmanMove(src, Action.EAST)) {
            Coordinate e = new Coordinate(x + 1, y);
            neighbors.add(e);
        }
        if (game.isLegalPacmanMove(src, Action.SOUTH)) {
            Coordinate s = new Coordinate(x, y + 1);
            neighbors.add(s);
        }
        return neighbors;
    }

    @Override
    public Path<Coordinate> graphSearch(final Coordinate src,
            final Coordinate tgt,
            final GameView game) {
        Set<Coordinate> Discovered = new HashSet<>();
        Discovered.add(src);
        Map<Integer, Set<Path<Coordinate>>> Layers = new HashMap<>();
        Map<Path, Integer> Distance = new HashMap<>();

        // Initialize layer 0
        Path<Coordinate> srcPath = new Path<Coordinate>(src);
        Set<Path<Coordinate>> layer0 = new HashSet<>();
        layer0.add(srcPath);
        Layers.put(0, layer0);
        Distance.put(srcPath, 0);
        int i = 0;

        while (Layers.containsKey(i) && !Layers.get(i).isEmpty()) {
            Set<Path<Coordinate>> newlayer = new HashSet<>();
            for (Path<Coordinate> u : Layers.get(i)) {
                if (u.getDestination() == null) {
                    continue;
                }
                Set<Coordinate> neighbors = getOutgoingNeighbors(u.getDestination(), game);
                for (Coordinate v : neighbors) {
                    if (!Discovered.contains(v)) {
                        Discovered.add(v);
                        Path<Coordinate> temp = new Path<Coordinate>(v, 1, u);
                        Distance.put(temp, Distance.get(u) + 1);
                        newlayer.add(temp);
                        if (v.equals(tgt)) {
                            Path<Coordinate> finPath = new Path<Coordinate>(v, 1, u);
                            return finPath;
                        }
                    }
                }
            }
            Layers.put(i + 1, newlayer);
            i++;
        }
        return null;
    }

    // Helper functions which collapse a PelletVertex path into a list of
    // coordinates based on set differences
    public List<Coordinate> extractPelletRemovalOrder(Path<PelletVertex> pelletPath, boolean earliestFirst) {
        if (pelletPath == null)
            return Collections.emptyList();
        List<Coordinate> eaten = new ArrayList<>();
        Path<PelletVertex> curPath = pelletPath;
        while (curPath.getParentPath() != null) {
            PelletVertex cur = curPath.getDestination();
            PelletVertex par = curPath.getParentPath().getDestination();
            Set<Coordinate> curSet = new HashSet<>(cur.getRemainingPelletCoordinates());
            Set<Coordinate> parSet = new HashSet<>(par.getRemainingPelletCoordinates());
            parSet.removeAll(curSet);

            if (parSet.size() != 1) {
                throw new IllegalStateException(
                        "Expected exactly one pellet difference, got " + parSet.size());
            }
            Coordinate snack = parSet.iterator().next();
            eaten.add(snack);

            curPath = curPath.getParentPath();

        }
        if (earliestFirst) {
            Collections.reverse(eaten);
        }
        return eaten;
    }

    public List<Coordinate> pelletPathToCoordinatePath(Path<PelletVertex> pelletPath, GameView game) {
        System.out.println("Converting pellet path to coordinate path");
        List<Coordinate> pelletRemovalOrder = extractPelletRemovalOrder(pelletPath, true);
        return pelletRemovalOrder;
    };

    // manage the pellet eating order as a list of coordinates
    List<Coordinate> pelletOrder = null;

    public void setPelletOrder(List<Coordinate> order) {
        this.pelletOrder = order;
    }

    public List<Coordinate> getPelletOrder() {
        return this.pelletOrder;
    }

    @Override
    public void makePlan(final GameView game) {
        Coordinate tempE = game.getEntity(this.getPacmanId()).getCurrentCoordinate();
        Path<Coordinate> finPath = graphSearch(tempE, this.getTargetCoordinate(), game);

        Stack<Coordinate> s = new Stack<Coordinate>();

        Path<Coordinate> tempNode = finPath;
        while (tempNode != null) {
            Coordinate currentCoord = tempNode.getDestination();
            s.add(currentCoord);
            tempNode = tempNode.getParentPath();
        }
        this.setPlanToGetToTarget(s);
    }

    @Override
    public Action makeMove(final GameView game) {
        // have makeMove work with the new class variable pelletOrder
        if (this.getPelletOrder() == null || this.getPelletOrder().isEmpty()) {
            // Create our pellet eating order
            Path<PelletVertex> pelletPath = findPathToEatAllPelletsTheFastest(game);
            List<Coordinate> finPath = pelletPathToCoordinatePath(pelletPath, game);
            this.setPelletOrder(finPath);
        }

        if (this.getPlanToGetToTarget() == null || this.getPlanToGetToTarget().isEmpty()) {
            List<Coordinate> pelletOrder = this.getPelletOrder();
            this.setTargetCoordinate(pelletOrder.get(0));
            pelletOrder.remove(0);
            this.setPelletOrder(pelletOrder);
            makePlan(game);
        }

        Stack<Coordinate> plan = this.getPlanToGetToTarget();
        if (plan == null || plan.isEmpty()) {
            // if the plan is still null or empty after the above processing then we are
            // actually done
            return null;
        }

        Coordinate curr = game.getEntity(this.getPacmanId()).getCurrentCoordinate();
        while (!plan.isEmpty() && plan.peek().equals(curr)) {
            plan.pop();
        }
        if (plan.isEmpty()) {
            return null;
        }

        Coordinate next = plan.peek();

        int dx = next.getXCoordinate() - curr.getXCoordinate();
        int dy = next.getYCoordinate() - curr.getYCoordinate();

        if (dx == 1 && dy == 0) {
            return Action.EAST;
        }
        if (dx == -1 && dy == 0) {
            return Action.WEST;
        }
        if (dx == 0 && dy == -1) {
            return Action.NORTH;
        }
        if (dx == 0 && dy == 1) {
            return Action.SOUTH;
        }
        return null;
    }

    @Override
    public void afterGameEnds(final GameView game) {

    }
}
