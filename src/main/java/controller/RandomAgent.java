/*
 * RandomAgent.java
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
import core.player.AbstractPlayer;
import core.vgdl.VGDLRegistry;
import ontology.Types;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Random agent class. It represents an agent that has a random behaviour. This
 * means that the agent moves randomly.
 *
 * @author Vladislav Nikolov Vasilev
 */
public class RandomAgent extends AbstractPlayer {
    // The following attributes can be modified
    protected static String gameConfigFile;

    // Game information data structure (loaded from a .yaml file) and file path
    protected GameInformation gameInformation;

    // Set of connections between cells
    protected Set<String> connectionSet;
    protected Map<String, Set<String>> gameElementVars;

    protected Random randomGenerator;
    protected Map<Types.ACTIONS, String> actionCorrespondence;

    protected static List<String> executedActions = new ArrayList<>();
    protected static List<String> gamePredicates = new ArrayList<>();

    /**
     * Class constructor. Creates a new random agent.
     *
     * @param stateObservation State observation of the game.
     * @param elapsedCpuTimer  Elapsed CPU time.
     */
    public RandomAgent(StateObservation stateObservation, ElapsedCpuTimer elapsedCpuTimer) {
      // Load game information
      Yaml yaml = new Yaml(new Constructor(GameInformation.class));

      try {
          InputStream inputStream = new FileInputStream(new File(RandomAgent.gameConfigFile));
          this.gameInformation = yaml.load(inputStream);
      } catch (FileNotFoundException e) {
          System.out.println(e.getStackTrace());
      }

      // Initialize PDDL related variables
      this.gameElementVars = this.extractVariablesFromPredicates();
      this.connectionSet = this.generateConnectionPredicates(stateObservation);

      randomGenerator = new Random();

      actionCorrespondence = new HashMap<>();

      actionCorrespondence.put(Types.ACTIONS.ACTION_UP, "MOVE_UP");
      actionCorrespondence.put(Types.ACTIONS.ACTION_DOWN, "MOVE_DOWN");
      actionCorrespondence.put(Types.ACTIONS.ACTION_LEFT, "MOVE_LEFT");
      actionCorrespondence.put(Types.ACTIONS.ACTION_RIGHT, "MOVE_RIGHT");
      actionCorrespondence.put(Types.ACTIONS.ACTION_USE, "USE");
    }

    /**
     * Method called in each turn that returns the next action that the agent
     * will execute. It is responsible for controlling the agent's behaviour.
     *
     * @param stateObservation State observation of the game.
     * @param elapsedCpuTimer  Elapsed CPU time
     * @return Returns the action that will be executed by the agent in the
     * current turn.
     */
    @Override
    public Types.ACTIONS act(StateObservation stateObservation, ElapsedCpuTimer elapsedCpuTimer) {
      // Select a random action between the available ones
      ArrayList<Types.ACTIONS> availableActions = stateObservation.getAvailableActions();

      int index = randomGenerator.nextInt(availableActions.size());
      Types.ACTIONS action = availableActions.get(index);

      // Translate game state to PDDL predicates
      String predicates = this.translateGameStateToPDDL(stateObservation);
      RandomAgent.gamePredicates.add(predicates);

      // Instantiate action
      String instantiatedAction;
      String actionStr = this.actionCorrespondence.get(action);

      // Use forward model to extract information about the current action
      // and the next game state
      StateObservation nextState = stateObservation.copy();
      nextState.advance(action);

      int numResources = this.getNumberResources(stateObservation);
      System.out.println(stateObservation.getResourcesPositions(stateObservation.getAvatarPosition())[0]);

      //System.out.println(numResources);

      if (!stateObservation.getAvatarOrientation().equals(nextState.getAvatarOrientation())) {
        actionStr = actionStr.replace("MOVE", "TURN");
        instantiatedAction = this.createAvatarAction(actionStr);
      } else if (action.equals(Types.ACTIONS.ACTION_USE)) {
        instantiatedAction = this.createAvatarAction(actionStr);
      } else {
        Vector2d currentAvatarPos = stateObservation.getAvatarPosition();
        
        int x = (int)currentAvatarPos.x / stateObservation.getBlockSize();
        int y = (int)currentAvatarPos.y / stateObservation.getBlockSize();

        boolean isResourcePicked = numResources != this.getNumberResources(nextState);

        instantiatedAction = this.createMoveAction(actionStr, x, y, isResourcePicked);
      }

      if (nextState.isGameOver()) {
        String nextTurnPredicates = this.translateGameStateToPDDL(nextState);
        RandomAgent.gamePredicates.add(nextTurnPredicates);
      }

      RandomAgent.executedActions.add(instantiatedAction);

      return action;
    }

    /**
     * Method that translates a game state observation to PDDL predicates.
     *
     * @param stateObservation State observation of the game.
     * @return Returns an uppercase String that contains the PDDL predicates.
     * The type of each object is specified in the predicate.
     */
    public String translateGameStateToPDDL(StateObservation stateObservation) {
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

      // Add connections to predicates
      this.connectionSet.stream().forEach(connection -> predicates.add(connection));

      return String.format("(%s)", String.join(" ", predicates));
    }

    /**
     * Method that translates a game state observation to a matrix of strings which
     * represent the elements of the game in each position according to the VGDDL
     * registry. There can be more than one game element in each position.
     *
     * @param stateObservation State observation of the game.
     * @return Returns a matrix containing the elements of the game in each position.
     */
    public HashSet<String>[][] getGameElementsMatrix(StateObservation stateObservation) {
        // Get the current game state
        ArrayList<Observation>[][] gameState = stateObservation.getObservationGrid();

        // Get the number of X tiles and Y tiles
        final int X_MAX = gameState.length, Y_MAX = gameState[0].length;

        // Create a new matrix, representing the game's map
        HashSet<String>[][] gameStringMap = new HashSet[X_MAX][Y_MAX];

        // Iterate over the map and transform the observations in a [x, y] cell
        // to a HashSet of Strings. In case there's no observation, add a
        // "background" string. The VGDLRegistry contains the needed information
        // to transform the StateObservation to a matrix of sets of Strings.
        for (int y = 0; y < Y_MAX; y++) {
            for (int x = 0; x < X_MAX; x++) {
                gameStringMap[x][y] = new HashSet<>();

                if (gameState[x][y].size() > 0) {
                    for (int i = 0; i < gameState[x][y].size(); i++) {
                        int itype = gameState[x][y].get(i).itype;
                        gameStringMap[x][y].add(VGDLRegistry.GetInstance().getRegisteredSpriteKey(itype));
                    }
                } else {
                    gameStringMap[x][y].add("background");
                }
            }
        }

        return gameStringMap;
    }

    public static void setGameConfigFile(String path) {
        RandomAgent.gameConfigFile = path;
    }

    public static void displayPostGameInformation() {
      System.out.println("##Tasks##");
      for (int i = 0; i < RandomAgent.executedActions.size(); i++) {
        System.out.println(String.format("[%d, %d]: %s", i, i+1, RandomAgent.executedActions.get(i)));
      }

      System.out.println("\n\n##States##");
      for (int i = 0; i < RandomAgent.gamePredicates.size(); i++) {
        System.out.println(String.format("[%d]: %s\n", i, RandomAgent.gamePredicates.get(i)));
      }
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
     * Method used to extract the variables from the predicates associated to a game
     * element and associate them to game elements directly.
     *
     * @return Returns a mapping between the game elements and the variables
     * associated to them.
     */
    private Map<String, Set<String>> extractVariablesFromPredicates() {
      Map<String, Set<String>> varsFromPredicates = new HashMap<>();

      // Pattern that matches a variable
      Pattern variablePattern = Pattern.compile("\\?[a-zA-Z]+");

      // Iterate over all the pairs <game element: [predicates]>
      for (Map.Entry<String, ArrayList<String>> entry : this.gameInformation.gameElementsCorrespondence.entrySet()) {
          String gameObservation = entry.getKey();
          Set<String> variables = new HashSet<>();

          // Iterate over the predicates searching for variables
          for (String observation : entry.getValue()) {
              Matcher variableMatcher = variablePattern.matcher(observation);

              while (variableMatcher.find()) {
                  for (int i = 0; i <= variableMatcher.groupCount(); i++) {
                      variables.add(variableMatcher.group(i));
                  }
              }
          }

          // Add the predicates associated to the game element
          varsFromPredicates.put(gameObservation, variables);
      }

      return varsFromPredicates;
    }

    /**
     * Method used to create an action instance which only involves the avatar such
     * as an action that changes the avatar's orientation or an USE action.
     *
     * @param actionStr String that contains the action to be instantiated.
     * @return Returns a String containing the instantiated action.
     */
    private String createAvatarAction(String actionStr) {
      String avatarVariable = this.gameInformation.avatarVariable;

      return String.format("(%s %s - %s)", actionStr, avatarVariable,
            this.gameInformation.variablesTypes.get(avatarVariable)
          )
        .replace("?", "")
        .toUpperCase();
    }

    /**
     * Method used to create an action instance of a movement action. An instance
     * of a movement action follows this pattern:
     * (MOV_ACTION AVATAR_VARIABLE - AVATAR_TYPE CURRENT_CELL - CELL_TYPE NEXT_CELL - CELL_TYPE).
     *
     * @param actionStr String that contains the movement action that is going
     * to be instantiated.
     * @param currentX Position of the avatar on the X-axis.
     * @param currentY Position of the avatar on the Y-axis.
     * @param isResourcePicked Boolean that tells if a resource will be picked
     * when the action is executed.
     * @return Returns a String containing the instantiated action.
     */ 
    private String createMoveAction(String actionStr, int currentX, int currentY, boolean isResourcePicked) {
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

      if (isResourcePicked) {
        actionStr += "_PICK_RESOURCE";
      }

      // Instantiate current cell and next cell objects
      String currentCell = String.format("%s_%d_%d", cellVariable, currentX, currentY);
      String nextCell = String.format("%s_%d_%d", cellVariable, nextX, nextY);

      String instantiatedAction = String.format("(%s %s - %s %s - %s %s - %s)",
          actionStr, avatarVariable, avatarType, currentCell, cellType, nextCell,
          cellType)
        .replace("?", "")
        .toUpperCase();

      return instantiatedAction;
    }

    private int getNumberResources(StateObservation stateObs) {
      int numResources = 0;

      ArrayList<Observation>[] resources = stateObs.getResourcesPositions();

      for (int i = 0; i < resources.length; i++) {
        numResources += resources[i].size();
      }

      return numResources;
    }
}
