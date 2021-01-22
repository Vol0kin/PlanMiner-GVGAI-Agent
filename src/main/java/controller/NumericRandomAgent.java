/*
 * NumericRandomAgent.java
 *
 * Copyright (C) 2021 Vladislav Nikolov Vasilev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0.html.
 */

/**
 * Package that contains the random agents. 
 */
package controller;

import core.game.Observation;
import core.game.StateObservation;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.util.*;

/**
 * Numeric random agent class. It represents an agent that has a random behaviour.
 * This means that the agent moves randomly. It uses a numeric world representation.
 *
 * @author Vladislav Nikolov Vasilev
 */
public class NumericRandomAgent extends AbstractRandomAgent {
    /**
     * Class constructor. Creates a new random agent.
     *
     * @param stateObservation State observation of the game.
     * @param elapsedCpuTimer  Elapsed CPU time.
     */
    public NumericRandomAgent(StateObservation stateObservation, ElapsedCpuTimer elapsedCpuTimer) {
      super(stateObservation, elapsedCpuTimer);
    }

    /**
     * Method that translates a game state observation to PDDL predicates.
     *
     * @param stateObservation State observation of the game.
     * @return Returns an uppercase String that contains the PDDL predicates.
     * The type of each object is specified in the predicate.
     */
    protected String translateGameStateToPDDL(StateObservation stateObservation) {
      List<String> predicates = new ArrayList<>();

      // Get the observations of the game state as elements of the VGDDLRegistry
      HashSet<String>[][] gameMap = this.getGameElementsMatrix(stateObservation);

      final int X_MAX = gameMap.length, Y_MAX = gameMap[0].length;

      // Process game elements
      for (int y = 0; y < Y_MAX; y++) {
          for (int x = 0; x < X_MAX; x++) {
              for (String cellObservation : gameMap[x][y]) {
                  // If the observation is in the domain, instantiate its predicates
                  if (this.gameInformation.gameElementsCorrespondence.containsKey(cellObservation)) {
                      List<String> predicateList = this.gameInformation.gameElementsCorrespondence.get(cellObservation);

                      // Instantiate each predicate
                      for (String predicate : predicateList) {
                          String predicateInstance = predicate;

                          // Iterate over all the variables associated to the game element and
                          // instantiate those who appear in the predicate
                          for (String variable : this.gameElementVars.get(cellObservation)) {
                              if (predicate.contains(variable)) {
                                  String variableInstance;

                                  if (variable.equals(this.gameInformation.avatarVariable)) {
                                      variableInstance = String.format(
                                          "%s - %s", variable,
                                          this.gameInformation.variablesTypes.get(variable))
                                        .replace("?", "");

                                      // If orientations are being used, add predicate associated
                                      // to the player's orientation
                                      if (this.gameInformation.orientationCorrespondence != null) {
                                          Vector2d avatarOrientation = stateObservation.getAvatarOrientation();
                                          Position orientation = null;

                                          if (avatarOrientation.x == 1.0) {
                                              orientation = Position.RIGHT;
                                          } else if (avatarOrientation.x == -1.0) {
                                              orientation = Position.LEFT;
                                          } else if (avatarOrientation.y == 1.0) {
                                              orientation = Position.DOWN;
                                          } else if (avatarOrientation.y == -1.0) {
                                              orientation = Position.UP;
                                          }

                                          predicates.add(this.gameInformation.orientationCorrespondence
                                                  .get(orientation)
                                                  .replace(variable, variableInstance)
                                                  .toUpperCase());
                                      }
                                  } else {
                                      variableInstance = String.format(
                                          "%s_%d_%d - %s", variable, x, y,
                                          this.gameInformation.variablesTypes.get(variable))
                                        .replace("?", "");
                                  }

                                  // Add instantiated variables to the predicate
                                  predicateInstance = predicateInstance.replace(variable, variableInstance);
                              }
                          }

                          int coordinate = predicateInstance.contains("column") ? x : y;
                          predicateInstance = String.format("(= %s %d)", predicateInstance, coordinate);

                          // Save instantiated predicate
                          predicates.add(predicateInstance.toUpperCase());
                      }
                  }
              }
          }
      }

      return String.format("(%s)", String.join(" ", predicates));
    }

    /**
     * Method used to create an action instance of a movement action. An instance
     * of a movement action follows this pattern:
     * (MOV_ACTION AVATAR_VARIABLE - AVATAR_TYPE CURRENT_CELL - CELL_TYPE NEXT_CELL - CELL_TYPE).
     *
     * @param stateObs Current state observation.
     * @param actionStr String that contains the movement action that is going
     * to be instantiated.
     * @param currentX Position of the avatar on the X-axis.
     * @param currentY Position of the avatar on the Y-axis.
     * @param isResourcePicked Boolean that tells if a resource will be picked
     * when the action is executed.
     * @return Returns a String containing the instantiated action.
     */ 
    protected String createMoveAction(StateObservation stateObs, String actionStr,
        int currentX, int currentY, boolean isResourcePicked) {

      String avatarVariable = this.gameInformation.avatarVariable;
      String avatarType = this.gameInformation.variablesTypes.get(avatarVariable);

      int nextX = currentX;
      int nextY = currentY;
      
      switch(actionStr) {
        case "MOVE_UP":
          nextY--;
          break;
        case "MOVE_DOWN":
          nextY++;
          break;
        case "MOVE_RIGHT":
          nextX++;
          break;
        case "MOVE_LEFT":
          nextX--;
          break;
      }

      // Instantiate current cell and next cell objects
      String instantiatedAction;

      if (isResourcePicked) {
        actionStr += "_PICK_RESOURCE";

        int blockSize = stateObs.getBlockSize();

        Vector2d resourcePosition = new Vector2d(nextX * blockSize, nextY * blockSize);
        ArrayList<Observation>[] resources = stateObs.getResourcesPositions(resourcePosition);

        Observation resourceObservation = null;

        for (int i = 0; i < resources.length; i++) {
          for (Observation obs: resources[i]) {
            if (obs.sqDist == 0.0) {
              resourceObservation = obs;
            }
          }
        }

        String gameElement = this.getGameElementFromObservation(resourceObservation);
        String resourceObject = "";

        for (String object: this.gameElementVars.get(gameElement)) {
          if (!object.equals(this.gameInformation.cellVariable)) {
            resourceObject = object;
          }
        }

        String resourceType = this.gameInformation.variablesTypes.get(resourceObject);
        resourceObject = String.format("%s_%d_%d", resourceObject, nextX, nextY);

        instantiatedAction = String.format("(%s %s - %s  %s - %s)",
            actionStr, avatarVariable, avatarType, resourceObject, resourceType)
          .replace("?", "")
          .toUpperCase();
      } else {
        instantiatedAction = String.format("(%s %s - %s)",
            actionStr, avatarVariable, avatarType)
          .replace("?", "")
          .toUpperCase();
      }

      return instantiatedAction;
    }
}
