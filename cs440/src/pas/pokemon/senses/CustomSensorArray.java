package src.pas.pokemon.senses;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.SwitchMove;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.DamageEquation;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Move.Category;
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
        try (FileWriter fw = new FileWriter("sensorNorm3.log", true);
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

    public MoveView canIKillOpponent(BattleView state) {
        PokemonView myPokemon = state.getTeam1View().getActivePokemonView();
        PokemonView oppPokemon = state.getTeam2View().getActivePokemonView();
        List<MoveView> myMoves = myPokemon.getAvailableMoves();
        for (MoveView moveView : myMoves) {
            if (moveView.getPower() == null) {
                continue;
            }

            // assume min roll no crit
            int damage = computeDamage(moveView, myPokemon, oppPokemon, false, 0.85);

            if (damage >= oppPokemon.getCurrentStat(Stat.HP)) {
                return moveView;
            }
        }
        return null;
    }

    public int computeDamage(
            MoveView move,
            PokemonView attacker,
            PokemonView defender,
            boolean isCrit,
            double r) {

        double L = attacker.getLevel();
        double P = move.getPower();

        Type moveType = move.getType();
        Category cat = move.getCategory();

        int attackerStat = -1;
        int defenderStat = -1;

        if (cat == Category.PHYSICAL) {
            attackerStat = attacker.getCurrentStat(Stat.ATK);
            defenderStat = defender.getCurrentStat(Stat.DEF);
        } else if (cat == Category.SPECIAL) {
            attackerStat = attacker.getCurrentStat(Stat.SPATK);
            defenderStat = defender.getCurrentStat(Stat.SPDEF);
        } else {
            throw new IllegalArgumentException("Move category must be PHYSICAL or SPECIAL for damage calculation.");
        }

        double C = isCrit ? 2.0 : 1.0;

        // STAB = 1.5 if at least one type of attacker matches move type, else 1.0
        double STAB = 1.0;
        if (moveType == attacker.getCurrentType1()) {
            STAB = 1.5;
        } else if (attacker.getCurrentType2() != null && moveType == attacker.getCurrentType2()) {
            STAB = 1.5;
        }

        // Type effectiveness
        double T = getTypeEffectiveness(moveType, defender.getCurrentType1());
        if (defender.getCurrentType2() != null) {
            T *= getTypeEffectiveness(moveType, defender.getCurrentType2());
        }

        // ---- formula ----
        double numerator = (((2 * L * C) / 5.0) + 2) * P * ((double) attackerStat / (double) defenderStat);
        double denominator = 50.0;

        double baseTerm = (numerator / denominator) + 2;

        double damageDouble = baseTerm * STAB * T * r;

        return (int) Math.floor(damageDouble);
    }

    public double[] encodeMove(MoveView move, PokemonView myPokemon, PokemonView oppPokemon) {

        double[] encoded = new double[2];
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

        // double check that the move has power just in case
        if (move.getPower() == null) {
            // look into these 1 hit ko moves but for now just assume 100 damage
            encoded[0] = 100.0;
            encoded[1] = 0.0;
            return encoded;
        }

        int damage = computeDamage(
                move,
                myPokemon,
                oppPokemon,
                false,
                0.925);

        double accuracy = 1.0;
        if (move.getAccuracy() != null) {
            accuracy = move.getAccuracy() / 100.0;
        }
        double baseDamage = damage / 0.925;

        // variance of uniform(0.85, 1.0)
        double varR = (0.15 * 0.15) / 12.0;

        double varHit = baseDamage * baseDamage * varR;

        double damageExpectation = (double) damage * accuracy;
        double damageVariance = accuracy * varHit + accuracy * (1.0 - accuracy) * (damage * damage);
        encoded[0] = damageExpectation;

        double damageFrac = damageExpectation / oppPokemon.getBaseStat(Stat.HP);

        // encoded[1] = damageVariance; ignore variance for now
        encoded[1] = damageFrac; // instead try this damage fraction
        return encoded;
    }

    private Matrix normalizeSensorValues(Matrix raw) {

        Matrix norm = Matrix.zeros(1, 19);

        int i = 0;

        // --- Attack ratios ---
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 5.0) / 5.0); // myAttackRatio
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 5.0) / 5.0); // mySAttackRatio

        // --- HP Fraction (myHPFrac) ---
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 1.0)); // already 0-1

        // --- Speed features ---
        norm.set(0, i, raw.get(0, i++)); // myFaster (0/1)
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 5.0) / 5.0); // speedRatio

        // --- Opponent ratios ---
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 5.0) / 5.0); // oppAttackRatio
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 5.0) / 5.0); // oppSAttackRatio

        // --- HP Fraction (oppHPFrac) ---
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 1.0)); // already 0-1

        // --- Alive counts ---
        norm.set(0, i, raw.get(0, i++) / 6.0); // myAliveCount
        norm.set(0, i, raw.get(0, i++) / 6.0); // oppAliveCount

        // --- One-hot move category ---
        norm.set(0, i, raw.get(0, i++)); // isSwitch
        norm.set(0, i, raw.get(0, i++)); // isPhysical
        norm.set(0, i, raw.get(0, i++)); // isSpecial
        norm.set(0, i, raw.get(0, i++)); // isStatus

        // --- Move encoding ---
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 300.0) / 300.0); // expected damage
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 300.0) / 300.0); // variance

        // --- Strategic flags ---
        norm.set(0, i, raw.get(0, i++)); // oneShot (0/1)
        norm.set(0, i, raw.get(0, i++)); // stabFlag (0/1)

        // --- Type effectiveness ---
        norm.set(0, i, clamp(raw.get(0, i++), 0.0, 4.0) / 4.0);

        return norm;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
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

    public Matrix getSensorValues(final BattleView state, final MoveView action) {

        int numfeatures = 19;

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
        int oppSATK = oppActivePokemon.getCurrentStat(Stat.SPATK);
        int oppSPDEF = oppActivePokemon.getCurrentStat(Stat.SPDEF);

        // attack and special attack ratios

        double myAttackRatio = (double) myATK / oppDEF;
        double mySAttackRatio = (double) mySATK / oppSPDEF;
        double oppAttackRatio = (double) oppATK / myDEF;
        double oppSAttackRatio = (double) oppSATK / mySPDEF;

        // speeds and current hp
        int mySpeed = myActivePokemon.getCurrentStat(Stat.SPD);
        int oppSpeed = oppActivePokemon.getCurrentStat(Stat.SPD);

        int myHP = myActivePokemon.getCurrentStat(Stat.HP);
        int oppHP = oppActivePokemon.getCurrentStat(Stat.HP);

        // speed and hp features
        int myFaster = mySpeed > oppSpeed ? 1 : 0;
        double speedRatio = (double) mySpeed / oppSpeed;
        double myHPFrac = (double) myHP / myActivePokemon.getBaseStat(Stat.HP);
        double oppHPFrac = (double) oppHP / oppActivePokemon.getBaseStat(Stat.HP);

        // num pokemon alive

        int myAliveCount = 6 - countFainted(myTeam);
        int oppAliveCount = 6 - countFainted(oppTeam);

        double[] moveEncoding = new double[2];

        // one hot encoding of move category
        int isSwitch = 0;
        int isPhysical = 0;
        int isSpecial = 0;
        int isStatus = 0;

        // encode the action move (2 features)
        if (action instanceof SwitchMove.SwitchMoveView) {
            // Switch move
            isSwitch = 1;
            moveEncoding[0] = 0.0;
            moveEncoding[1] = 0.0;
        } else {
            Category cat = action.getCategory();
            if (cat == Category.PHYSICAL) {
                isPhysical = 1;
                moveEncoding = encodeMove(action, myActivePokemon, oppActivePokemon);
            } else if (cat == Category.SPECIAL) {
                isSpecial = 1;
                moveEncoding = encodeMove(action, myActivePokemon, oppActivePokemon);
            } else {
                // STATUS move
                isStatus = 1;
                moveEncoding[0] = 0.0;
                moveEncoding[1] = 0.0;
            }
        }

        int oneShot = 0;
        MoveView killMove = canIKillOpponent(state);
        if (killMove != null) {
            oneShot = 1;
        }

        int stabFlag = 0;

        Type actionType = action.getType();

        if (actionType == myActivePokemon.getCurrentType1()) {
            stabFlag = 1;
        } else if (myActivePokemon.getCurrentType2() != null && actionType == myActivePokemon.getCurrentType2()) {
            stabFlag = 1;
        }

        double effectivenessFlag = 1;

        effectivenessFlag *= getTypeEffectiveness(actionType, oppActivePokemon.getCurrentType1());

        if (oppActivePokemon.getCurrentType2() != null) {
            effectivenessFlag *= getTypeEffectiveness(actionType, oppActivePokemon.getCurrentType2());
        }

        int sensorIdx = 0;

        sensorValues.set(0, sensorIdx++, myAttackRatio);
        sensorValues.set(0, sensorIdx++, mySAttackRatio);
        sensorValues.set(0, sensorIdx++, myHPFrac);
        sensorValues.set(0, sensorIdx++, myFaster);
        sensorValues.set(0, sensorIdx++, speedRatio);

        sensorValues.set(0, sensorIdx++, oppAttackRatio);
        sensorValues.set(0, sensorIdx++, oppSAttackRatio);
        sensorValues.set(0, sensorIdx++, oppHPFrac);
        sensorValues.set(0, sensorIdx++, myAliveCount);
        sensorValues.set(0, sensorIdx++, oppAliveCount);

        sensorValues.set(0, sensorIdx++, isSwitch);
        sensorValues.set(0, sensorIdx++, isPhysical);
        sensorValues.set(0, sensorIdx++, isSpecial);
        sensorValues.set(0, sensorIdx++, isStatus);

        sensorValues.set(0, sensorIdx++, moveEncoding[0]);
        sensorValues.set(0, sensorIdx++, moveEncoding[1]);
        sensorValues.set(0, sensorIdx++, oneShot);
        sensorValues.set(0, sensorIdx++, stabFlag);
        sensorValues.set(0, sensorIdx++, effectivenessFlag);

        Matrix normalizedSensorValues = normalizeSensorValues(sensorValues);

        // Check for null values before returning
        for (int i = 0; i < normalizedSensorValues.getShape().getNumCols(); i++) {
            Double value = normalizedSensorValues.get(0, i);
            if (value == null) {
                throw new IllegalStateException("Null value found at sensor index " + i);
            }
        }

        // Log normalized sensor values
        // String[] featureNames = {
        // "myAttackRatio", "mySAttackRatio", "mySpeed", "myHP", "myAliveCount",
        // "oppAttackRatio", "oppSAttackRatio", "oppSpeed", "oppHP", "oppAliveCount",
        // "moveCategory", "moveDamageExp", "moveDamageVar"
        // };
        // StringBuilder sb = new StringBuilder("Normalized Sensors: {");
        // for (int i = 0; i < normalizedSensorValues.getShape().getNumCols(); i++) {
        // sb.append(featureNames[i]).append("=").append(String.format("%.4f",
        // normalizedSensorValues.get(0, i)));
        // if (i < normalizedSensorValues.getShape().getNumCols() - 1) {
        // sb.append(", ");
        // }
        // }
        // sb.append("}");
        // logDebug(sb.toString());

        return normalizedSensorValues;
    }

}
