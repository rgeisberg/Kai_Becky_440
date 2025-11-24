package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;

import edu.cwru.sepia.environment.model.state.State.StateView;

import java.util.HashSet; // will need for dfs
import java.util.Stack; // will need for dfs
import java.util.Set; // will need for dfs

// JAVA PROJECT IMPORTS

public class DFSMazeAgent
        extends MazeAgent {

    public DFSMazeAgent(int playerNum) {
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

        int[] townHallCoords = getTownHallCoordinates(state);
        Set<Path> Visited = new HashSet<>();
        Stack<Path> stack = new Stack<>();
        Path srcPath = new Path(src);
        stack.push(srcPath);

        while (!stack.isEmpty()) {
            Path u = stack.pop();
            if (!Visited.contains(u)) {
                Visited.add(u);
                if (u.getDestination().equals(goal)) {
                    return u;
                }
                for (Path v : getNeighbors(u, townHallCoords, state)) {
                    stack.push(v);
                }
            }
        }
        return null;
    }

}
