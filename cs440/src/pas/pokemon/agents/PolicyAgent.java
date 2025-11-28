package src.pas.pokemon.agents;

// SYSTEM IMPORTS
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;
import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.DamageEquation;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense; // fully connected layer
import edu.bu.pas.pokemon.nn.layers.ReLU; // some activations (below too)
import edu.bu.pas.pokemon.nn.layers.Tanh;
import edu.bu.pas.pokemon.nn.layers.Sigmoid;

// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;

public class PolicyAgent
        extends NeuralQAgent {

    private static void logDebugDefault(String message) {
        try (FileWriter fw = new FileWriter("debug.log", true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.println(message);
        } catch (IOException e) {
            // Silently fail if logging doesn't work
        }
    }

    private static void logDebug(String message, String filePath) {
        try (FileWriter fw = new FileWriter(filePath, true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.println(message);
            pw.flush();
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PolicyAgent() {
        super();
    }

    public void initializeSenses(Namespace args) {
        SensorArray modelSenses = new CustomSensorArray();

        this.setSensorArray(modelSenses);
    }

    @Override
    public void initialize(Namespace args) {
        // make sure you call this, this will call your initModel() and set a field
        // AND if the command line argument "inFile" is present will attempt to set
        // your model with the contents of that file.
        super.initialize(args);

        // what senses will your neural network have?
        this.initializeSenses(args);

        // do what you want just don't expect custom command line options to be
        // available
        // when I'm testing your code
    }

    @Override
    public Model initModel() {

        // HELPFUL TIPS TO REMEMBER
        // The number of layers corresponds to how complicated the function is that you
        // need to learn
        //
        // The number of neurons determines how much information the layer can represent
        // A larger width means the layer can represent more complex functions
        //
        // perhaps use RELU as different activation function,

        // currently this creates a one-hidden-layer network
        Sequential qFunction = new Sequential();
        // -------------------layer 1----------------------
        qFunction.add(new Dense(128, 512)); // number of input features , number of neurons in this hidden layer
        qFunction.add(new Tanh()); // non-linear activation function
        // ------------------end of layer 1----------------
        // -------------------layer 2----------------------
        qFunction.add(new Dense(512, 512)); // number of input features , number of neurons in this hidden layer
        qFunction.add(new Tanh()); // non-linear activation function
        // ------------------end of layer 2----------------
        // -------------------layer 3----------------------
        qFunction.add(new Dense(512, 256)); // number of input features , number of neurons in this hidden layer
        qFunction.add(new Tanh()); // non-linear activation function
        // ------------------end of layer 3----------------
        // ------------------Final Layer-------------------
        qFunction.add(new Dense(256, 1)); // final dense layer, maps to 1
        return qFunction;
    }

    private double getTypeEffectiveness(Type attackType, Type defendType) {
        return Type.getEffectivenessModifier(attackType, defendType);
    }

    public Pokemon viewToPokemon(PokemonView pv) {
        Type[] types = new Type[2];
        types[0] = pv.getCurrentType1();
        types[1] = pv.getCurrentType2();

        int[] ivs = new int[6];
        Stat[] stats = Stat.values();
        for (Stat st : stats) {
            if (st == Stat.ACC || st == Stat.EVASIVE) {
                continue;
            }
            ivs[st.ordinal()] = pv.getIV(st);
        }

        int[] evs = new int[6];
        for (Stat st : stats) {
            if (st == Stat.ACC || st == Stat.EVASIVE) {
                continue;
            }
            evs[st.ordinal()] = pv.getEV(st);
        }

        int[] basestats = new int[8];
        for (Stat st : stats) {
            basestats[st.ordinal()] = pv.getBaseStat(st);
        }

        Pokemon mon = Pokemon.makeNewPokemon(
                pv.getDexIdx(),
                pv.getName(),
                types,
                pv.getLevel(),
                ivs,
                evs,
                basestats);
        return mon;
    }

    public int outSpeedKill(PokemonView oppPokemon, TeamView myTeam) {
        // if I have time in the future consider priority moves
        int numPokemon = myTeam.size();
        int opSpeed = oppPokemon.getCurrentStat(Stat.SPD);
        for (int i = 0; i < numPokemon; i++) {
            PokemonView myPokemon = myTeam.getPokemonView(i);
            int mySpeed = myPokemon.getCurrentStat(Stat.SPD);
            // find a pokemon that can out speed the opponent
            if (mySpeed > opSpeed && !myPokemon.hasFainted()) {
                List<MoveView> myMoves = myPokemon.getAvailableMoves();
                for (MoveView moveView : myMoves) {
                    if (moveView.getPower() == null) {
                        continue;
                    }
                    // determine if the damage will be sufficient to knock out the opponent assuming
                    // no crit and min roll
                    boolean STAB = moveView.getType().equals(myPokemon.getCurrentType1())
                            || moveView.getType().equals(myPokemon.getCurrentType2());
                    // ask about what 'type damage means' I'm assuming true means take it into
                    // account, not that it definenitly does super effective

                    Move real_move = new Move(moveView);
                    Pokemon real_myPokemon = viewToPokemon(myPokemon);
                    Pokemon real_oppPokemon = viewToPokemon(oppPokemon);

                    int damage = DamageEquation.calculateDamage(
                            real_move,
                            1,
                            real_myPokemon,
                            real_oppPokemon,
                            STAB,
                            true,
                            0,
                            0.85);

                    if (damage >= oppPokemon.getCurrentStat(Stat.HP)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int slowKill(PokemonView oppPokemon, TeamView myTeam) {
        int numPokemon = myTeam.size();
        for (int i = 0; i < numPokemon; i++) {
            PokemonView myPokemon = myTeam.getPokemonView(i);
            // find a pokemon that can kill the opponent (regardless of speed)
            if (!myPokemon.hasFainted()) {
                // Need to check we don't get oneshot ourselves first (assume no crit but high
                // roll)

                // only if we don't outspeed but since we call outSpeedKill first we know we are
                // slower (bc otherwise we would just switch to that mon)
                Pokemon real_myPokemon = viewToPokemon(myPokemon);
                Pokemon real_oppPokemon = viewToPokemon(oppPokemon);
                boolean wouldGetOneShot = false;
                List<MoveView> oppMoves = oppPokemon.getAvailableMoves();
                for (MoveView oppMove : oppMoves) {
                    if (oppMove.getPower() == null) {
                        continue;
                    }
                    boolean STAB = oppMove.getType().equals(oppPokemon.getCurrentType1())
                            || oppMove.getType().equals(oppPokemon.getCurrentType2());

                    Move real_oppMove = new Move(oppMove);

                    int damage = DamageEquation.calculateDamage(
                            real_oppMove,
                            1,
                            real_myPokemon,
                            real_oppPokemon,
                            STAB,
                            true,
                            0,
                            1.0);

                    if (damage >= myPokemon.getCurrentStat(Stat.HP)) {
                        // we would get oneshot, try next pokemon
                        wouldGetOneShot = true;
                        break;
                    }
                }

                if (wouldGetOneShot) {
                    continue; // now this correctly goes to next pokemon
                }

                List<MoveView> myMoves = myPokemon.getAvailableMoves();
                for (MoveView moveView : myMoves) {
                    if (moveView.getPower() == null) {
                        continue;
                    }
                    // determine if the damage will be sufficient to knock out the opponent assuming
                    // no crit and min roll
                    boolean STAB = moveView.getType().equals(myPokemon.getCurrentType1())
                            || moveView.getType().equals(myPokemon.getCurrentType2());

                    Move real_move = new Move(moveView);
                    int damage = DamageEquation.calculateDamage(
                            real_move,
                            1,
                            real_myPokemon,
                            real_oppPokemon,
                            STAB,
                            true,
                            0,
                            0.85);

                    if (damage >= oppPokemon.getCurrentStat(Stat.HP)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int typeMatchupSwitch(PokemonView oppPokemon, TeamView myTeam) {
        int numPokemon = myTeam.size();
        Type oppType1 = oppPokemon.getCurrentType1();
        Type oppType2 = oppPokemon.getCurrentType2();

        double bestEffectiveness = 1.0;
        int bestIdx = -1;

        for (int i = 0; i < numPokemon; i++) {
            PokemonView myPokemon = myTeam.getPokemonView(i);
            if (!myPokemon.hasFainted()) {
                Type myType1 = myPokemon.getCurrentType1();
                Type myType2 = myPokemon.getCurrentType2();

                double effectiveness = 1.0;
                effectiveness *= getTypeEffectiveness(oppType1, myType1);
                if (myType2 != null) {
                    effectiveness *= getTypeEffectiveness(oppType1, myType2);
                }
                if (oppType2 != null) {
                    effectiveness *= getTypeEffectiveness(oppType2, myType1);
                    if (myType2 != null) {
                        effectiveness *= getTypeEffectiveness(oppType2, myType2);
                    }
                }

                if (effectiveness > bestEffectiveness) {
                    bestEffectiveness = effectiveness;
                    bestIdx = i;
                }
            }
        }

        return bestIdx;

    }

    @Override
    public Integer chooseNextPokemon(BattleView view) {
        // TODO: change this to something more intelligent!
        // oh boy did i ever

        PokemonView oppPokemon = view.getTeam2View().getActivePokemonView();
        TeamView myTeam = view.getTeam1View();

        // first check if we have a pokemon that can out speed and kill the opponent in
        // one shot
        int switchIdx = outSpeedKill(oppPokemon, myTeam);
        if (switchIdx != -1) {
            return switchIdx;
        }

        switchIdx = slowKill(oppPokemon, myTeam);
        if (switchIdx != -1) {
            return switchIdx;
        }

        // otherwise just pick a good type match up (that is not fainted)
        switchIdx = typeMatchupSwitch(oppPokemon, myTeam);

        if (switchIdx != -1) {
            return switchIdx;
        }

        // if we have no good options at all just pick the first non-fainted pokemon
        for (int i = 0; i < myTeam.size(); i++) {
            PokemonView myPokemon = myTeam.getPokemonView(i);
            if (!myPokemon.hasFainted()) {
                switchIdx = i;
                break;
            }

        }
        return switchIdx;
    }

    // exploration schedule
    private int maxEpisodes = 10000;
    private double epsilonStart = 1.0; // explore a lot at the beginning
    private double epsilonEnd = 0.05; // small amount of exploration later
    private int episodesDone = 0;
    private Random rng = new Random();
    private boolean lastGameWasCounted = false;

    private double currentEpsilon() {
        // fraction goes from 0 to 1 as we move through 70% of training
        double fraction = Math.min(1.0, (double) episodesDone / (0.7 * maxEpisodes));
        return epsilonStart + fraction * (epsilonEnd - epsilonStart);
    }

    @Override
    public MoveView getMove(BattleView view) {
        // TODO: change this to include random exploration during training and maybe use
        // the transition model to make
        // good predictions?
        // if you choose to use the transition model you might want to also override the
        // makeGroundTruth(...) method
        // to not use temporal difference learning

        // currently always tries to argmax the learned model
        // this is not a good idea to always do when training. When playing evaluation
        // games you *do* want to always
        // argmax your model, but when training our model may not know anything yet! So,
        // its a good idea to sometime
        // during training choose *not* to argmax the model and instead choose something
        // new at random.

        // HOW that randomness works and how often you do it are up to you, but it
        // *will* affect the quality of your
        // learned model whether you do it or not!

        double epsilon = currentEpsilon();
        if (rng.nextDouble() < epsilon) {
            List<MoveView> legalMoves = view.getTeam1View().getActivePokemonView().getAvailableMoves();
            return legalMoves.get(rng.nextInt(legalMoves.size()));
        }
        return this.argmax(view);
    }

    private int gamesPlayed = 0;
    private int gamesWon = 0;
    private int gamesLost = 0;
    private int gamesTied = 0;
    private int totalTurns = 0;

    @Override
    public void afterGameEnds(BattleView view) {
        gamesPlayed++;

        TeamView myTeam = view.getTeam1View();
        TeamView oppTeam = view.getTeam2View();

        boolean myAllFainted = true;
        boolean oppAllFainted = true;

        for (int i = 0; i < myTeam.size(); i++) {
            if (!myTeam.getPokemonView(i).hasFainted()) {
                myAllFainted = false;
                break;
            }
        }
        for (int i = 0; i < oppTeam.size(); i++) {
            if (!oppTeam.getPokemonView(i).hasFainted()) {
                oppAllFainted = false;
                break;
            }
        }

        boolean iWon = oppAllFainted && !myAllFainted;
        boolean iLost = myAllFainted && !oppAllFainted;
        boolean tie = myAllFainted && oppAllFainted;

        if (iWon)
            gamesWon++;
        if (iLost)
            gamesLost++;
        if (tie)
            gamesTied++;

        episodesDone++;

        double winRate = (gamesWon + 0.5 * gamesTied) / (double) gamesPlayed;

        // epsilon at end of this episode:
        double eps = currentEpsilon();

        // Log game count every game
        logDebugDefault("Game " + gamesPlayed + " completed. Episodes done: " + episodesDone);
        logDebug("Game " + gamesPlayed + " completed. Episodes done: " + episodesDone, "gameCount.txt");

        if (gamesPlayed % 1000 == 0) {
            String logMsg = String.format(
                    "Games Played: %d, Wins: %d, Losses: %d, Ties: %d, Win Rate: %.3f, Epsilon: %.3f",
                    gamesPlayed,
                    gamesWon,
                    gamesLost,
                    gamesTied,
                    winRate,
                    eps);
            logDebug(logMsg, "trainingData.txt");
        }
    }
}
