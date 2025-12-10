package src.pas.pokemon.rewards;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.utils.Pair;
import java.util.List;

public class CustomRewardFunction
        extends RewardFunction {

    public CustomRewardFunction() {
        super(RewardType.STATE_ACTION_STATE); // R(s,a) implementation
    }

    public double getLowerBound() {
        double lowerBound = -100;
        return lowerBound;
    }

    public double getUpperBound() {
        double upperBound = 100;
        return upperBound;
    }

    // -------------------BOUNDS: [-100, 100]-----------------------

    public double getStateReward(final BattleView state) {
        return 0d;
    }

    // ------------------------- Helpers for Rewards----------------------------
    private double teamHP(TeamView team) {
        double sum = 0.0;
        for (int i = 0; i < 6; i++) {
            PokemonView view = team.getPokemonView(i);
            int cur = view.getCurrentStat(Stat.HP);
            // int max = view.getInitialStat(Stat.HP);
            sum += (double) cur;
        }
        return sum; // in [0, teamSize]
    }

    private int countFainted(TeamView teamView) {
        int numFainted = 0;
        for (int i = 0; i < teamView.size(); i++) {
            if (teamView.getPokemonView(i).hasFainted()) {
                numFainted += 1;
            }
        }
        return numFainted;
    }

    private int statusScore(TeamView team) {
        int score = 0;
        for (int i = 0; i < 6; i++) {
            PokemonView view = team.getPokemonView(i);
            NonVolatileStatus status = view.getNonVolatileStatus();
            if (status != NonVolatileStatus.NONE) { // whatever the API gives you
                score++;
            }
        }
        return score;
    }

    private double[] pokemonsHP(TeamView team) {
        double[] pokeHP = new double[6];
        for (int i = 0; i < team.size(); i++) {
            pokeHP[i] = team.getPokemonView(i).getCurrentStat(Stat.HP);
        }
        return pokeHP;
    }

    // -----------------------------------------------------

    public double getStateActionReward(final BattleView state,
            final MoveView action) {

        return 0.0;

    }

    public double getStateActionStateReward(final BattleView state,
            final MoveView action,
            final BattleView nextState) {

        // ----------------- List of OG/Pre move stats------------------------
        // original HP of active Pokemon on each side
        int originalMyHP = state.getTeam1View()
                .getActivePokemonView()
                .getCurrentStat(Stat.HP);
        int originalOppHP = state.getTeam2View()
                .getActivePokemonView()
                .getCurrentStat(Stat.HP);

        double winnerReward = 0.0; // expected win/loss term
        double myHPDamageExp = 0.0; // expected damage *to me*
        double oppHPDamageExp = 0.0; // expected damage *to opponent*

        int faintedMyBefore = countFainted(state.getTeam1View()); // fainted before move *my pokemon*
        int faintedOppBefore = countFainted(state.getTeam2View()); // fainted before move *op pokemon*
        double koReward = 0.0;

        double myHPFracBefore = teamHP(state.getTeam1View());
        double oppHPFracBefore = teamHP(state.getTeam2View());
        double teamHPReward = 0.0;

        int myStatusBefore = statusScore(state.getTeam1View());
        int oppStatusBefore = statusScore(state.getTeam2View());
        double statusReward = 0.0;

        int myOriginalIndex = state.getTeam1View().getActivePokemonIdx();
        int oppOriginalIndex = state.getTeam2View().getActivePokemonIdx();

        double[] myPokemonHealth = pokemonsHP(state.getTeam1View());
        double[] oppPokemonHealth = pokemonsHP(state.getTeam2View());
        // ----------------- List of OG/Pre move stats------------------------

        // -----calculate how many (if any) pokemon fainted after the move applied------
        int faintedMyAfter = countFainted(nextState.getTeam1View());
        int faintedOppAfter = countFainted(nextState.getTeam2View());

        // ----------- Fainting stats -----------------------
        int myNewKOs = faintedMyAfter - faintedMyBefore; // mons we lost
        int oppNewKOs = faintedOppAfter - faintedOppBefore; // mons we KO'd
        koReward += (oppNewKOs - myNewKOs);
        // ----------------------------------------------------------

        // ------------ HP STATS overall team -----------------------------------
        double myHPFracAfter = teamHP(nextState.getTeam1View());
        double oppHPFracAfter = teamHP(nextState.getTeam2View());
        double myDelta = myHPFracBefore - myHPFracAfter;
        double oppDelta = oppHPFracBefore - oppHPFracAfter;
        double Delta = oppDelta - myDelta;
        // ------------------------------------------------------------------

        // ------------------ Status ---------------------------
        int myStatusAfter = statusScore(nextState.getTeam1View());
        int oppStatusAfter = statusScore(nextState.getTeam2View());
        int deltaStatus = (oppStatusAfter - myStatusAfter) - (oppStatusBefore - myStatusBefore);
        statusReward += deltaStatus;
        // -----------------------------------------------------
        // ----- terminal win/loss reward -----
        if (nextState.isOver()) {
            TeamView myTeamView = nextState.getTeam1View();
            TeamView opTeamView = nextState.getTeam2View();

            boolean iLost = myTeamView.getActivePokemonView().hasFainted();
            boolean iWon = opTeamView.getActivePokemonView().hasFainted();

            if (iLost) {
                winnerReward += -100.0;
            } else if (iWon) {
                winnerReward += 100.0;
            }
            // implicit 0 if no one died
        }
        // -------------------------------------------

        // ----- damage reward for active Pokemon -----
        int newMyHP = nextState.getTeam1View()
                .getActivePokemonView()
                .getCurrentStat(Stat.HP);
        int newOppHP = nextState.getTeam2View()
                .getActivePokemonView()
                .getCurrentStat(Stat.HP);

        double[] myPokemonHealthAfter = pokemonsHP(nextState.getTeam1View());
        double[] oppPokemonHealthAfter = pokemonsHP(nextState.getTeam2View());
        double switchPenalty = 0.0;

        if (myOriginalIndex != nextState.getTeam1View().getActivePokemonIdx()) {
            // switch
            int index = nextState.getTeam1View().getActivePokemonIdx();
            double diffMy = myPokemonHealth[index] - myPokemonHealthAfter[index];
            switchPenalty -0.3;

        } else {
            // no switch
            double diffMy = originalMyHP - newMyHP; // >0 means I took damage
        }

        if (oppOriginalIndex != nextState.getTeam2View().getActivePokemonIdx()) {
            // op switch !!
            int index = nextState.getTeam2View().getActivePokemonIdx();
            double diffMy = oppPokemonHealth[index] - oppPokemonHealthAfter[index];

        } else {
            // no switch
            int diffOpp = originalOppHP - newOppHP; // >0 means they took damage
        }

        // these start at zero dont worry
        // myHPDamageExp += diffMy;
        // oppHPDamageExp += diffOpp;

        // positive if we expect to deal more damage than we take
        double damageReward = Delta;

        // penalize battle steps;
        Battle battle = new Battle(state);
        int numTurns = battle.getTurnNumber();

        double penalty = -0.1 * numTurns;

        damageReward = Math.max(-200.0, Math.min(200.0, damageReward));
        koReward = Math.max(-1.0, Math.min(1.0, koReward));
        teamHPReward = Math.max(-2.0, Math.min(2.0, teamHPReward));
        statusReward = Math.max(-6.0, Math.min(6.0, statusReward));
        // then combine with weights
        double reward = 0.0;
        if (winnerReward != 0) {
            reward = winnerReward;
        } else {
            reward = 0.25 * damageReward + 30 * koReward + penalty + switchPenalty;
        }

        reward = Math.max(-100.0, Math.min(100.0, reward));

        return reward;
    }
}