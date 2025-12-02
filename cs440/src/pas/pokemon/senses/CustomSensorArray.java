package src.pas.pokemon.senses;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.DamageEquation;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import edu.bu.pas.pokemon.core.callbacks.Callback;
import edu.bu.pas.pokemon.core.enums.Target;

public class CustomSensorArray
        extends SensorArray {

    // TODO: make fields if you want!

    public CustomSensorArray() {
        // TODO: intialize those fields if you make any!
    }

    private static void logDebug(String message) {
        try (FileWriter fw = new FileWriter("debug.log", true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.println(message);
        } catch (IOException e) {
            // Silently fail if logging doesn't work
        }
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

    public double[] encodeMove(MoveView move, PokemonView myPokemon, PokemonView oppPokemon) {

        double[] encoded = new double[2];
        Move real_move = new Move(move);
        Pokemon real_myPokemon = viewToPokemon(myPokemon);
        Pokemon real_oppPokemon = viewToPokemon(oppPokemon);
        Type myMoveType = move.getType();
        Type opType1 = oppPokemon.getCurrentType1();
        Type opType2 = oppPokemon.getCurrentType2();

        // double typeMult = 1;

        // typeMult *= getTypeEffectiveness(myMoveType, opType1);
        // if (opType2 != null) {
        // typeMult *= getTypeEffectiveness(myMoveType, opType2);
        // }

        boolean STAB = false;

        if (myMoveType == myPokemon.getCurrentType1()) {
            STAB = true;
        } else if (myPokemon.getCurrentType2() != null && myMoveType == myPokemon.getCurrentType2()) {
            STAB = true;
        }

        int damage = DamageEquation.calculateDamage(
                real_move,
                1,
                real_myPokemon,
                real_oppPokemon,
                STAB,
                true,
                0,
                0.925);

        double accuracy = real_move.getAccuracy() / 100.0;
        double baseDamage = damage / 0.925;

        // variance of uniform(0.85, 1.0)
        double varR = (0.15 * 0.15) / 12.0;

        double varHit = baseDamage * baseDamage * varR;

        double damageExpectation = (double) damage * accuracy;
        double damageVariance = accuracy * varHit + accuracy * (1.0 - accuracy) * (damage * damage);
        encoded[0] = damageExpectation;
        encoded[1] = damageVariance;
        return encoded;
    }

    public Matrix normalizeSensorValues(final Matrix sensorValues) {
        int rows = sensorValues.getShape().getNumRows();
        int cols = sensorValues.getShape().getNumCols();

        Matrix normalized = Matrix.zeros(rows, cols);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double v = sensorValues.get(r, c);
                double nv = v;

                switch (c) {
                    // 0: myAttackRatio
                    // 1: mySAttackRatio
                    // 5: oppAttackRatio
                    // 6: oppSAttackRatio
                    // Ratios are usually not huge, scale to about [0,1]
                    case 0:
                    case 1:
                    case 5:
                    case 6:
                        nv = v / 4.0; // assume ratios rarely > 4
                        break;

                    // 2: mySpeed
                    // 7: oppSpeed
                    // Typical speeds 0200
                    case 2:
                    case 7:
                        nv = v / 200.0;
                        break;

                    // 3: myHP
                    // 8: oppHP
                    // HP often in 0400 range
                    case 3:
                    case 8:
                        nv = v / 400.0;
                        break;

                    // 4: myAliveCount
                    // 9: oppAliveCount
                    // In [0, 6]
                    case 4:
                    case 9:
                        nv = v / 6.0;
                        break;

                    // 10: move damage expectation
                    // Roughly similar to HP/damage, scale like HP
                    case 10:
                        nv = v / 400.0;
                        break;

                    // 11: move damage variance
                    // Can be big; scale more aggressively
                    case 11:
                        nv = v / 40000.0;
                        break;

                    default:
                        // Fallback: no scaling
                        nv = v;
                        break;
                }

                // Clamp to [-1, 1] to avoid exploding values
                if (nv > 1.0) {
                    nv = 1.0;
                } else if (nv < -1.0) {
                    nv = -1.0;
                }

                normalized.set(r, c, nv);
            }
        }

        return normalized;
    }

    private int countFainted(TeamView teamView) {
        int numFainted = 0;
        for (int i = 0; i < 6; i++) {
            if (teamView.getPokemonView(i).hasFainted()) {
                numFainted += 1;
            }
        }
        return numFainted;
    }

    public Matrix getSensorValues(final BattleView state, final MoveView action) {

        int numfeatures = 12;

        // matrix for features
        Matrix sensorValues = Matrix.zeros(1, numfeatures);

        // get the team views
        TeamView myTeam = state.getTeamView(0);
        TeamView oppTeam = state.getTeamView(1);

        // get active pokemon indices
        int myActivePokemonIndex = myTeam.getActivePokemonIdx();
        int oppActivePokemonIndex = oppTeam.getActivePokemonIdx();

        PokemonView myActivePokemon = myTeam.getActivePokemonView();
        PokemonView oppActivePokemon = oppTeam.getActivePokemonView();

        int myATK = myActivePokemon.getCurrentStat(Stat.ATK);
        int myDEF = myActivePokemon.getCurrentStat(Stat.DEF);
        int mySATK = myActivePokemon.getCurrentStat(Stat.SPATK);
        int mySPDEF = myActivePokemon.getCurrentStat(Stat.SPDEF);

        int oppATK = oppActivePokemon.getCurrentStat(Stat.ATK);
        int oppDEF = oppActivePokemon.getCurrentStat(Stat.DEF);
        int oppSATK = myActivePokemon.getCurrentStat(Stat.SPATK);
        int oppSPDEF = myActivePokemon.getCurrentStat(Stat.SPDEF);

        // attack and special attack ratios

        double myAttackRatio = myATK / oppDEF;
        double mySAttackRatio = mySATK / oppSPDEF;
        double oppAttackRatio = oppATK / myDEF;
        double oppSAttackRatio = oppSATK / mySPDEF;

        // speeds and current hp

        int mySpeed = myActivePokemon.getCurrentStat(Stat.SPD);
        int oppSpeed = oppActivePokemon.getCurrentStat(Stat.SPD);

        int myHP = myActivePokemon.getCurrentStat(Stat.HP);
        int oppHP = oppActivePokemon.getCurrentStat(Stat.HP);

        // num pokemon alive

        int myAliveCount = 6 - countFainted(myTeam);
        int oppAliveCount = 6 - countFainted(oppTeam);

        // encode the action move (2 features)

        double[] moveEncoding = encodeMove(action, myActivePokemon, oppActivePokemon);

        int sensorIdx = 0;

        sensorValues.set(0, sensorIdx++, myAttackRatio);
        sensorValues.set(0, sensorIdx++, mySAttackRatio);
        sensorValues.set(0, sensorIdx++, mySpeed);
        sensorValues.set(0, sensorIdx++, myHP);
        sensorValues.set(0, sensorIdx++, myAliveCount);

        sensorValues.set(0, sensorIdx++, oppAttackRatio);
        sensorValues.set(0, sensorIdx++, oppSAttackRatio);
        sensorValues.set(0, sensorIdx++, oppSpeed);
        sensorValues.set(0, sensorIdx++, oppHP);
        sensorValues.set(0, sensorIdx++, oppAliveCount);

        sensorValues.set(0, sensorIdx++, moveEncoding[0]);
        sensorValues.set(0, sensorIdx++, moveEncoding[0]);

        Matrix normalizedSensorValues = normalizeSensorValues(sensorValues);

        return normalizedSensorValues;
    }

}
