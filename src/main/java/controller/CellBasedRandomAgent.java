/*
 * CellBasedRandomAgent.java
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
 * Package that contains the planning agent along with its data structures.
 */
package controller;

import core.game.Observation;
import core.game.StateObservation;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.util.*;

/**
 * Cell based random agent class. It represents an agent that has a random behaviour.
 * This means that the agent moves randomly. It uses a cell based world representation
 * (cell objects).
 *
 * @author Vladislav Nikolov Vasilev
 */
public class CellBasedRandomAgent extends AbstractRandomAgent {
    protected Set<String> connectionSet;

    /**
     * Class constructor. Creates a new random agent.
     *
     * @param stateObservation State observation of the game.
     * @param elapsedCpuTimer  Elapsed CPU time.
     */
    public CellBasedRandomAgent(StateObservation stateObservation, ElapsedCpuTimer elapsedCpuTimer) {
      super(stateObservation, elapsedCpuTimer);
      this.connectionSet = this.generateConnectionPredicates(stateObservation);
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

                          // Save instantiated predicate
                          predicates.add(predicateInstance.toUpperCase());
                      }
                  }
              }
          }
      }

      // Add resource predicates
      predicates.addAll(this.createResourcePredicates());

      // Add connections to predicates
      this.connectionSet.stream().forEach(connection -> predicates.add(connection));

      return String.format("(%s)", String.join(" ", predicates));
    }
    /**
     * Method that creates the resource predicates.
     *
     * @return Returns a list containing the resource predicates that
     * must be included in the list of predicates.
     */ 
    protected List<String> createResourcePredicates() {
      List<String> resourcePredicates = new ArrayList<>();
      String avatarVariable = this.gameInformation.avatarVariable;
      String avatarType = this.gameInformation.variablesTypes.get(avatarVariable);

      for (String resource: this.pickedResources.keySet()) {
        if (this.pickedResources.get(resource) > 0) {
          resourcePredicates.add(this.gameInformation.pickedResourcesPredicates.get(resource)
              .replace(avatarVariable, String.format("%s - %s", avatarVariable, avatarType)
              .replace("?", ""))
              .toUpperCase());
        }
      }

      return resourcePredicates;
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
      String cellVariable = this.gameInformation.cellVariable;
      String cellType = this.gameInformation.variablesTypes.get(cellVariable);

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
      String currentCell = String.format("%s_%d_%d", cellVariable, currentX, currentY);
      String nextCell = String.format("%s_%d_%d", cellVariable, nextX, nextY);
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

        instantiatedAction = String.format("(%s %s - %s %s - %s %s - %s %s - %s)",
            actionStr, avatarVariable, avatarType, currentCell, cellType, nextCell,
            cellType, resourceObject, resourceType)
          .replace("?", "")
          .toUpperCase();
      } else {
        instantiatedAction = String.format("(%s %s - %s %s - %s %s - %s)",
            actionStr, avatarVariable, avatarType, currentCell, cellType, nextCell,
            cellType)
          .replace("?", "")
          .toUpperCase();
      }

      return instantiatedAction;
    }
    /**
     * Method used to create an action instance of the USE action. An instance
     * of the USE action follows this pattern:
     * (USE AVATAR_VARIABLE - AVATAR_TYPE CURRENT_CELL - CELL_TYPE DESTINATION_CELL - CELL_TYPE).
     *
     * @param stateObs Current state observation.
     * @param actionStr String that contains the movement action that is going
     * to be instantiated.
     * @param currentX Position of the avatar on the X-axis.
     * @param currentY Position of the avatar on the Y-axis.
     */
    protected String createUseAction(StateObservation stateObs, String actionStr,
        int currentX, int currentY) {
      
      String cellVariable = this.gameInformation.cellVariable;
      String cellType = this.gameInformation.variablesTypes.get(cellVariable);

      String avatarVariable = this.gameInformation.avatarVariable;
      String avatarType = this.gameInformation.variablesTypes.get(avatarVariable);

      int nextX = currentX;
      int nextY = currentY;
      
      Vector2d avatarOrientation = stateObs.getAvatarOrientation();

      if (avatarOrientation.x == 1.0) {
        nextX++;
      } else if (avatarOrientation.x == -1.0) {
        nextX--;
      } else if (avatarOrientation.y == 1.0) {
        nextY++;
      } else if (avatarOrientation.y == -1.0) {
        nextY--;
      }

      // Instantiate current cell and next cell objects
      String currentCell = String.format("%s_%d_%d", cellVariable, currentX, currentY);
      String destCell = String.format("%s_%d_%d", cellVariable, nextX, nextY);

      String useAction = String.format("(%s %s - %s %s - %s %s - %s)",
          actionStr, avatarVariable, avatarType, currentCell, cellType, destCell,
          cellType)
        .replace("?", "")
        .toUpperCase();

      return useAction;
    }

    /**
     * Method that generates the connection predicates between the cells of the
     * map.
     *
     * @param stateObservation State observation of the game.
     * @return Returns a set which preserves insertion order and contains
     * the PDDL predicates associated to the cells connections.
     */
    private Set<String> generateConnectionPredicates(StateObservation stateObservation) {
        // Initialize connection set
        Set<String> connections = new LinkedHashSet<>();

        // Get the observations of the game state as elements of the VGDDLRegistry
        HashSet<String>[][] gameMap = this.getGameElementsMatrix(stateObservation);

        final int X_MAX = gameMap.length, Y_MAX = gameMap[0].length;

        for (int y = 0; y < Y_MAX; y++) {
            for (int x = 0; x < X_MAX; x++) {
                // Create string containing the current cell
                String currentCell = String.format("%s_%d_%d - %s",
                    this.gameInformation.cellVariable, x, y,
                    this.gameInformation.variablesTypes.get(
                      this.gameInformation.cellVariable)
                    )
                  .replace("?", "");

                if (y - 1 >= 0) {
                    String connection = this.gameInformation.connections.get(Position.UP);
                    connection = connection.replace("?c", currentCell);
                    connection = connection.replace("?u", String.format(
                          "%s_%d_%d - %s", this.gameInformation.cellVariable, x, y - 1,
                          this.gameInformation.variablesTypes.get(
                            this.gameInformation.cellVariable)
                          )
                        .replace("?", ""));

                    connections.add(connection.toUpperCase());
                }

                if (y + 1 < Y_MAX) {
                    String connection = this.gameInformation.connections.get(Position.DOWN);
                    connection = connection.replace("?c", currentCell);
                    connection = connection.replace("?d", String.format(
                          "%s_%d_%d - %s", this.gameInformation.cellVariable, x, y + 1,
                          this.gameInformation.variablesTypes.get(
                            this.gameInformation.cellVariable)
                          )
                        .replace("?", ""));

                    connections.add(connection.toUpperCase());
                }

                if (x - 1 >= 0) {
                    String connection = this.gameInformation.connections.get(Position.LEFT);
                    connection = connection.replace("?c", currentCell);
                    connection = connection.replace("?l", String.format(
                          "%s_%d_%d - %s", this.gameInformation.cellVariable, x - 1, y,
                          this.gameInformation.variablesTypes.get(
                            this.gameInformation.cellVariable)
                          )
                        .replace("?", ""));

                    connections.add(connection.toUpperCase());
                }

                if (x + 1 < X_MAX) {
                    String connection = this.gameInformation.connections.get(Position.RIGHT);
                    connection = connection.replace("?c", currentCell);
                    connection = connection.replace("?r", String.format(
                          "%s_%d_%d - %s", this.gameInformation.cellVariable, x + 1, y,
                          this.gameInformation.variablesTypes.get(
                            this.gameInformation.cellVariable)
                          )
                        .replace("?", ""));

                    connections.add(connection.toUpperCase());
                }
            }
        }

        return connections;
    }

    /**
     * Method used to create an action instance of a movement action. An instance
     * of a movement action follows this pattern:
     * (ACTION_NAME AVATAR_VARIABLE - AVATAR_TYPE CURRENT_CELL - CELL_TYPE DESTINATION_CELL - CELL_TYPE).
     *
     * @param stateObs Current state observation.
     * @param actionStr String that contains the movement action that is going
     * to be instantiated.
     * @param currentX Position of the avatar on the X-axis.
     * @param currentY Position of the avatar on the Y-axis.
     * @param isSameOrientation Boolean that tells whether the agent's orientation
     * will change in the next turn.
     */
    protected String createMovementAction(StateObservation stateObs, String actionStr,
        int currentX, int currentY, boolean isSameOrientation) {
    
      String cellVariable = this.gameInformation.cellVariable;
      String cellType = this.gameInformation.variablesTypes.get(cellVariable);

      String avatarVariable = this.gameInformation.avatarVariable;
      String avatarType = this.gameInformation.variablesTypes.get(avatarVariable);

      int nextX = currentX;
      int nextY = currentY;
      
      Vector2d avatarOrientation = stateObs.getAvatarOrientation();

      if (isSameOrientation) {
        if (avatarOrientation.x == 1.0) {
          nextX++;
        } else if (avatarOrientation.x == -1.0) {
          nextX--;
        } else if (avatarOrientation.y == 1.0) {
          nextY++;
        } else if (avatarOrientation.y == -1.0) {
          nextY--;
        }
      }

      // Instantiate current cell and next cell objects
      String currentCell = String.format("%s_%d_%d", cellVariable, currentX, currentY);
      String destCell = String.format("%s_%d_%d", cellVariable, nextX, nextY);

      String movementAction = String.format("(%s %s - %s %s - %s %s - %s)",
          actionStr, avatarVariable, avatarType, currentCell, cellType, destCell,
          cellType)
        .replace("?", "")
        .toUpperCase();

      return movementAction;
    }
}
