package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;

import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction; // Directions in Sepia

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue; // heap in java
import java.util.Set;
import static java.lang.Math.sqrt;

// JAVA PROJECT IMPORTS

public class DijkstraMazeAgent
        extends MazeAgent {

    public DijkstraMazeAgent(int playerNum) {
        super(playerNum);
    }

    public int[] getTownHallCoordinates(StateView state) {
        for (Integer unitId : state.getAllUnitIds()) {
            if (state.getUnit(unitId).getTemplateView().getName().equals("TownHall")) {
                int x = state.getUnit(unitId).getXPosition();
                int y = state.getUnit(unitId).getYPosition();
                return new int[] { x, y };
            }
        }
        return null;
    }

    // helper method to get neighbors of a vertex time complexity O(1)
    public Set<Path> getNeighborsF(Path v, int[] townHallCoords, StateView state) {
        Set<Path> neighbors = new HashSet<>();
        Vertex vVert = v.getDestination();
        int x = vVert.getXCoordinate();
        int y = vVert.getYCoordinate();
        // each potential neighbor has x, y, cost
        float[][] potentialNeighbors = {
                { x - 1, y - 1, (float) Math.sqrt(26) }, { x - 1, y, 5 }, { x - 1, y + 1, (float) Math.sqrt(125) },
                { x, y - 1, 1 }, { x, y + 1, 10 },
                { x + 1, y - 1, (float) Math.sqrt(26) }, { x + 1, y, 5 }, { x + 1, y + 1, (float) Math.sqrt(125) }
        };

        for (float[] coords : potentialNeighbors) {
            int newX = (int) coords[0];
            int newY = (int) coords[1];
            float cost = coords[2];
            if (state.inBounds(newX, newY)) {
                Vertex neighbor = new Vertex(newX, newY);
                if (state.isUnitAt(newX, newY) == false && state.resourceAt(newX, newY) == null) {
                    Path neighborPath = new Path(neighbor, cost, v);
                    neighbors.add(neighborPath);
                } else if (townHallCoords != null && (newX == townHallCoords[0] && newY == townHallCoords[1])) {
                    // The only 'unit we want to add is the town hall'
                    Path neighborPath = new Path(neighbor, cost, v);
                    neighbors.add(neighborPath);
                }
            }
        }
        return neighbors;
    }

    @Override
    public Path search(Vertex src,
            Vertex goal,
            StateView state) {
        int[] townHallCoords = getTownHallCoordinates(state);
        Map<Path, Float> CurrentD = new HashMap<>();
        Map<Path, Float> Distances = new HashMap<>();
        Set<Path> Visited = new HashSet<>();
        // PriorityQueue<Float, Path> pq = new PriorityQueue<>(Comparator.comparing(Float::floatValue));
        return null;
    }

}
