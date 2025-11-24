package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;

import edu.cwru.sepia.environment.model.state.State.StateView;

import java.util.HashMap;
import java.util.HashSet; // will need for bfs
import java.util.Queue; // will need for bfs
import java.util.LinkedList; // will need for bfs
import java.util.Map;
import java.util.Set; // will need for bfs

// JAVA PROJECT IMPORTS

public class BFSMazeAgent
        extends MazeAgent {

    public BFSMazeAgent(int playerNum) {
        super(playerNum);
    }

    // helper method to get coordinates of the town hall
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
    public Set<Path> getNeighbors(Path v, int[] townHallCoords, StateView state) {
        Set<Path> neighbors = new HashSet<>();
        Vertex vVert = v.getDestination();
        int x = vVert.getXCoordinate();
        int y = vVert.getYCoordinate();
        int[][] potentialNeighbors = {
                { x - 1, y - 1 }, { x - 1, y }, { x - 1, y + 1 },
                { x, y - 1 }, { x, y + 1 },
                { x + 1, y - 1 }, { x + 1, y }, { x + 1, y + 1 }
        };

        for (int[] coords : potentialNeighbors) {
            int newX = coords[0];
            int newY = coords[1];
            if (state.inBounds(newX, newY)) {
                Vertex neighbor = new Vertex(newX, newY);
                if (state.isUnitAt(newX, newY) == false && state.resourceAt(newX, newY) == null) {
                    Path neighborPath = new Path(neighbor, 1, v);
                    neighbors.add(neighborPath);
                } else if (townHallCoords != null && (newX == townHallCoords[0] && newY == townHallCoords[1])) {
                    // The only 'unit we want to add is the town hall'
                    Path neighborPath = new Path(neighbor, 1, v);
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
        // Initialize Data Structs

        // For parents we can use path obj

        int[] townHallCoords = getTownHallCoordinates(state);

        Set<Vertex> Discovered = new HashSet<>();
        Discovered.add(src);
        Map<Integer, Set<Path>> Layers = new HashMap<>();
        Map<Path, Integer> Distance = new HashMap<>();

        // Initialize layer 0 (just the source vertex)
        Path srcPath = new Path(src);
        Set<Path> layer0 = new HashSet<>();
        layer0.add(srcPath);
        Layers.put(0, layer0);
        Distance.put(srcPath, 0);
        int i = 0;

        while (Layers.containsKey(i) && !Layers.get(i).isEmpty()) {
            // Declare the next layer
            Set<Path> newlayer = new HashSet<>();

            for (Path u : Layers.get(i)) {
                Set<Path> neighbors = getNeighbors(u, townHallCoords, state);
                for (Path v : neighbors) {
                    if (!Discovered.contains(v.getDestination())) {
                        Discovered.add(v.getDestination());
                        Distance.put(v, Distance.get(u) + 1);
                        newlayer.add(v);

                        int destX = v.getDestination().getXCoordinate();
                        int destY = v.getDestination().getYCoordinate();
                        int goalX = goal.getXCoordinate();
                        int goalY = goal.getYCoordinate();
                        if (destX == goalX && destY == goalY) {
                            Path FinPath = new Path(v.getDestination(), 1, u);
                            return FinPath;
                        }
                    }
                }
            }
            Layers.put(i + 1, newlayer);
            i++;
        }
        return null;
    }
}
