package src.labs.rttt.ordering;


// SYSTEM IMPORTS
import edu.bu.labs.rttt.game.CellType;
import edu.bu.labs.rttt.game.PlayerType;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame.RecursiveTicTacToeGameView;
import edu.bu.labs.rttt.traversal.Node;
import edu.bu.labs.rttt.utils.Coordinate;
import edu.bu.labs.rttt.utils.Pair;

import java.util.List;


// JAVA PROJECT IMPORTS



public class MoveOrderer
    extends Object
{

    public static List<Node> orderChildren(List<Node> children)
    {
        // this default ordering does no ordering at all and just returns the children in whatever order they
        // were generated in
        return children;
    }

}
