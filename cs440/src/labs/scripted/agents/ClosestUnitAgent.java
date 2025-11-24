package src.labs.scripted.agents;

// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action; // how we tell sepia what each unit will do
import edu.cwru.sepia.agent.Agent; // base class for an Agent in sepia
import edu.cwru.sepia.environment.model.history.History.HistoryView; // history of the game so far
import edu.cwru.sepia.environment.model.state.ResourceNode; // tree or gold
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView; // the "state" of that resource
import edu.cwru.sepia.environment.model.state.ResourceType; // what kind of resource units are carrying
import edu.cwru.sepia.environment.model.state.State.StateView; // current state of the game
import edu.cwru.sepia.environment.model.state.Unit.UnitView; // current state of a unit
import edu.cwru.sepia.util.Direction; // directions for moving in the map

import java.io.InputStream;
import java.io.OutputStream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// JAVA PROJECT IMPORTS

public class ClosestUnitAgent
        extends Agent {

    private Integer closestUnitId; // id of the unit closest to the enemy (used to lookup UnitView from state)
    private Integer enemyUnitId; // id of the unit our opponent controls (used to lookup UnitView from state)
    private Integer goldResourceNodeId; // id of one gold deposit in game (used to lookup ResourceView from state)

    // put your fields here! You will probably want to remember the following
    // information:
    // - the closest friendly unit id
    // - the enemy unit id
    // - the id of the gold

    /**
     * The constructor for this type. The arguments (including the player number: id
     * of the team we are controlling)
     * are contained within the game's xml file that we are running. We can also add
     * extra arguments to the game's xml
     * config for this agent and those will be included in args.
     */
    public ClosestUnitAgent(int playerNum, String[] args) {
        super(playerNum); // make sure to call parent type (Agent)'s constructor!

        // initialize your fields here!
        this.closestUnitId = null;
        this.enemyUnitId = null;
        this.goldResourceNodeId = null;

        // helpful printout just to help debug
        System.out.println("Constructed ClosestUnitAgent");
    }

    /////////////////////////////// GETTERS AND SETTERS (this is Java after all)
    /////////////////////////////// ///////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public final Integer getClosestUnitId() {
        return this.closestUnitId;
    }

    public final Integer getEnemyUnitId() {
        return this.enemyUnitId;
    }

    public final Integer getGoldResourceNodeId() {
        return this.goldResourceNodeId;
    }

    private void setClosestUnitId(Integer id) {
        this.closestUnitId = id;
    }

    private void setEnemyUnitId(Integer i) {
        this.enemyUnitId = i;
    }

    private void setGoldResourceNodeId(Integer i) {
        this.goldResourceNodeId = i;
    }

    @Override
    public Map<Integer, Action> initialStep(StateView state,
            HistoryView history) {

        // discover friendly units
        Set<Integer> myUnitIds = new HashSet<Integer>();
        for (Integer unitID : state.getUnitIds(this.getPlayerNumber())) // for each unit on my team
        {
            myUnitIds.add(unitID);
        }

        // check that all units are of the correct type...in this game there is only
        // "melee" or "footman" units
        for (Integer unitID : myUnitIds) {
            if (!state.getUnit(unitID).getTemplateView().getName().toLowerCase().equals("footman")) {
                System.err.println("[ERROR] ScriptedAgent.initialStep: DummyAgent should control only footman units");
                System.exit(-1);
            }
        }

        // check that there is another player and get their player number (i.e. the id
        // of the enemy team)
        Integer[] playerNumbers = state.getPlayerNumbers();
        if (playerNumbers.length != 2) {
            System.err.println("ERROR: Should only be two players in the game");
            System.exit(1);
        }
        Integer enemyPlayerNumber = null;
        if (playerNumbers[0] != this.getPlayerNumber()) {
            enemyPlayerNumber = playerNumbers[0];
        } else {
            enemyPlayerNumber = playerNumbers[1];
        }

        // get the units controlled by the other player...similar strategy to how we
        // discovered our (friendly) units
        Set<Integer> enemyUnitIds = new HashSet<Integer>();
        for (Integer unitID : state.getUnitIds(enemyPlayerNumber)) {
            enemyUnitIds.add(unitID);
        }

        // in this game the enemy team should have 1 unit
        if (enemyUnitIds.size() != 1) {
            System.err.println("[ERROR] ScriptedAgent.initialStep: Enemy should control only 1 unit");
            System.exit(-1);
        }

        // check that the enemy only controlls "melee" or "footman" units
        for (Integer unitID : enemyUnitIds) {
            if (!state.getUnit(unitID).getTemplateView().getName().toLowerCase().equals("footman")) {
                System.err.println("[ERROR] ScriptedAgent.initialStep: Enemy should only control footman units");
                System.exit(-1);
            }
        }

        List<Integer> goldResourceNodeIds = state.getResourceNodeIds(ResourceNode.Type.GOLD_MINE);
        Integer goldResourceNodeId = goldResourceNodeIds.get(0);

        // Find the closest unit to the enemy
        Integer enemyUnitId = enemyUnitIds.iterator().next();
        UnitView enemyUnit = state.getUnit(enemyUnitId);
        int enemyX = enemyUnit.getXPosition();
        int enemyY = enemyUnit.getYPosition();

        Integer closestUnitId = null;
        double closestDistance = Double.MAX_VALUE;

        for (Integer unitId : myUnitIds) {
            UnitView myUnit = state.getUnit(unitId);
            int myX = myUnit.getXPosition();
            int myY = myUnit.getYPosition();

            double distance = Math.sqrt((myX - enemyX) * (myX - enemyX) + (myY - enemyY) * (myY - enemyY));

            if (distance < closestDistance) {
                closestDistance = distance;
                closestUnitId = unitId;
            }
        }

        // set our fields
        this.setClosestUnitId(closestUnitId);
        this.setEnemyUnitId(enemyUnitId);
        this.setGoldResourceNodeId(goldResourceNodeId);

        // ask middlestep what actions each unit should do. Unless we need the units to
        // do something very specific
        // on the first turn of the game, we typically put all "action logic" in
        // middlestep and call it from here.
        return this.middleStep(state, history);
    }

    @Override
    public Map<Integer, Action> middleStep(StateView state,
            HistoryView history) {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        // Get the closest unit and enemy unit
        Integer closestUnitId = this.getClosestUnitId();
        Integer enemyUnitId = this.getEnemyUnitId();

        UnitView closestUnit = state.getUnit(closestUnitId);
        UnitView enemyUnit = state.getUnit(enemyUnitId);

        int closestX = closestUnit.getXPosition();
        int closestY = closestUnit.getYPosition();
        int enemyX = enemyUnit.getXPosition();
        int enemyY = enemyUnit.getYPosition();

        // Move the closest unit towards the enemy or attack if adjacent
        if (closestX != enemyX) {
            actions.put(closestUnitId, Action.createPrimitiveMove(closestUnitId,
                    closestX < enemyX ? Direction.EAST : Direction.WEST));
        } else if (closestY != enemyY) {
            actions.put(closestUnitId, Action.createPrimitiveMove(closestUnitId,
                    closestY < enemyY ? Direction.SOUTH : Direction.NORTH));
        }

        if (closestX == enemyX && Math.abs(enemyY - closestY) <= 1) {
            actions.put(closestUnitId, Action.createPrimitiveAttack(closestUnitId, enemyUnitId));
        }

        return actions;
    }

    @Override
    public void terminalStep(StateView state,
            HistoryView history) {
        // don't need to do anything
    }

    /**
     * The following two methods aren't really used by us much in this class. These
     * methods are used to load/save
     * the Agent (for instance if our Agent "learned" during the game we might want
     * to save the model, etc.). Until the
     * very end of this class we will ignore these two methods.
     */
    @Override
    public void loadPlayerData(InputStream is) {
    }

    @Override
    public void savePlayerData(OutputStream os) {
    }

}
