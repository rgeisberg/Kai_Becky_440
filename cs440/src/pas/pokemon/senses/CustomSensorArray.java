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
import edu.bu.pas.pokemon.core.Move.Category;

import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

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
        // Pokemon real_myPokemon = viewToPokemon(myPokemon);
        // Pokemon real_oppPokemon = viewToPokemon(oppPokemon);
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
        encoded[1] = damageVariance;
        return encoded;
    }

    public Matrix normalizeSensorValues(final Matrix sensorValues) {
        Matrix normalized = Matrix.zeros(sensorValues.getShape().getNumRows(), sensorValues.getShape().getNumCols());

        int idx = 0;
        // Normalize each feature to the range [0, 1] as appropriate

        // Active indices (0-5) -> normalize to [0, 1]
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 5.0);
        idx++;
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 5.0);
        idx++;

        for (int p = 0; p < 12; p++) {
            // Type 1: normalize to [0, 1] there are 15 types
            normalized.set(idx, 0, sensorValues.get(idx, 0) / 15.0);
            idx++;
            // Type 2: handle the -1 for no second type case
            normalized.set(idx, 0, Math.max(sensorValues.get(idx, 0), 0) / 15.0);
            idx++;

            // Stats (8 stats): normalize to [0, 1] max stat assuming 600 max (might need to
            // adjust as there can be edge cases)
            // might want to do different normalization for different levels or stats or
            // both but ig this is ok for now
            for (int s = 0; s < 8; s++) {
                normalized.set(idx, 0, sensorValues.get(idx, 0) / 600.0);
                idx++;
            }
        }

        // Move encoding (6 features)
        // Move type: normalize to [0, 1] for 15 types
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 15.0);
        idx++;
        // Base power: normalize to [0, 1] hyper beam (strongest raw power move) has 150
        // power
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 150.0);
        idx++;
        // Accuracy: normalize to [0, 1] i think accuracy can go above 100 but not sure
        // for gen 1
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 120.0);
        idx++;
        // Category: normalize to [0, 1] (3 categories: 0-2)
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 2.0);
        idx++;
        // Effectiveness: normalize to [0, 1] (max 4x)
        normalized.set(idx, 0, sensorValues.get(idx, 0) / 4.0);
        idx++;
        // STAB: already [0, 1]
        normalized.set(idx, 0, sensorValues.get(idx, 0));
        idx++;
        return normalized;
    }

    public Matrix getSensorValues(final BattleView state, final MoveView action) {

        int numfeatures = 129;

        // matrix for features
        Matrix sensorValues = Matrix.zeros(numfeatures, 1);

        // get the team views
        TeamView myTeam = state.getTeamView(0);
        TeamView oppTeam = state.getTeamView(1);

        // get active pokemon indices
        int myActivePokemonIndex = myTeam.getActivePokemonIdx();
        int oppActivePokemonIndex = oppTeam.getActivePokemonIdx();

        // store active indices as features
        sensorValues.set(0, 0, myActivePokemonIndex);
        sensorValues.set(1, 0, oppActivePokemonIndex);

        // get stats enum
        Stat[] statsEnum = Stat.values();

        // process all pokemon stats and types for my team (6 pokemon x 10 features =
        // 60)
        // each pokemon: 2 types + 8 stats
        int sensorIdx = 2;
        for (int i = 0; i < myTeam.size(); i++) {
            // depending on how this is handled might want to use try catch
            PokemonView pokemon = myTeam.getPokemonView(i);

            // store primary and secondary types
            Type primaryType = pokemon.getCurrentType1();
            Type secondaryType = pokemon.getCurrentType2();
            sensorValues.set(sensorIdx++, 0, primaryType.ordinal());
            sensorValues.set(sensorIdx++, 0, secondaryType != null ? secondaryType.ordinal() : -1);

            // store stats
            for (int j = 0; j < statsEnum.length; j++) {
                sensorValues.set(sensorIdx++, 0, pokemon.getCurrentStat(statsEnum[j]));
            }
        }

        // process all pokemon stats and types for opponent team (6 pokemon x 10
        // features = 60)
        for (int i = 0; i < oppTeam.size(); i++) {
            // depending on how this is handled might want to use try catch
            PokemonView pokemon = oppTeam.getPokemonView(i);

            // store primary and secondary types
            Type primaryType = pokemon.getCurrentType1();
            Type secondaryType = pokemon.getCurrentType2();
            sensorValues.set(sensorIdx++, 0, primaryType.ordinal());
            sensorValues.set(sensorIdx++, 0, secondaryType != null ? secondaryType.ordinal() : -1);

            // store stats
            for (int j = 0; j < statsEnum.length; j++) {
                sensorValues.set(sensorIdx++, 0, pokemon.getCurrentStat(statsEnum[j]));
            }
        }

        // encode the action move (6 features)
        PokemonView myActivePokemon = myTeam.getActivePokemonView();
        PokemonView oppActivePokemon = oppTeam.getActivePokemonView();
        double[] moveEncoding = encodeMove(action, myActivePokemon, oppActivePokemon);

        for (int i = 0; i < moveEncoding.length; i++) {
            sensorValues.set(sensorIdx++, 0, moveEncoding[i]);
        }
        sensorValues.set(0, sensorIdx++, 1.0);

        Matrix normalizedSensorValues = normalizeSensorValues(sensorValues);

        return normalizedSensorValues.transpose();
    }

}
