package src.pas.othello.ordering;

// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;
import src.pas.othello.heuristics.Heuristics;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

// JAVA PROJECT IMPORTS

public class MoveOrderer
        extends Object {

    public static List<Node> orderChildren(List<Node> children) {
        // Null check for the children list
        if (children == null) {
            return new ArrayList<>();
        }

        for (Node child : children) {
            if (child == null) {
                continue;
            }

            double heuristicValue = Heuristics.calculateHeuristicValue(child);
            child.setUtilityValue(heuristicValue);
        }
        children.sort((a, b) -> Double.compare(b.getUtilityValue(), a.getUtilityValue()));
        if (children.get(0).getGameView().getCurrentPlayerType() != children.get(0).getMaxPlayerType()) {
            Collections.reverse(children);
        }
        return children;
    }

}
