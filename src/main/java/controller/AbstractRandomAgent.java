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

public abstract class AbstractRandomAgent extends AbstractPlayer { 
    // The following attributes can be modified
    protected static String gameConfigFile;

    // Game information data structure (loaded from a .yaml file) and file path
    protected GameInformation gameInformation;

    // Set of connections between cells
    protected Map<String, Set<String>> gameElementVars;

    protected Random randomGenerator;
    protected Map<Types.ACTIONS, String> actionCorrespondence;

    protected static List<String> executedActions = new ArrayList<>();
    protected static List<String> gamePredicates = new ArrayList<>();

    protected boolean isGameOverDetected;

    /**
     * Class constructor. Creates a new random agent.
     *
     * @param stateObservation State observation of the game.
     * @param elapsedCpuTimer  Elapsed CPU time.
     */
    public AbstractRandomAgent(StateObservation stateObservation, ElapsedCpuTimer elapsedCpuTimer) {
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

      this.randomGenerator = new Random();

      this.actionCorrespondence = new HashMap<>();

      this.actionCorrespondence.put(Types.ACTIONS.ACTION_UP, "MOVE_UP");
      this.actionCorrespondence.put(Types.ACTIONS.ACTION_DOWN, "MOVE_DOWN");
      this.actionCorrespondence.put(Types.ACTIONS.ACTION_LEFT, "MOVE_LEFT");
      this.actionCorrespondence.put(Types.ACTIONS.ACTION_RIGHT, "MOVE_RIGHT");
      this.actionCorrespondence.put(Types.ACTIONS.ACTION_USE, "USE");

      this.isGameOverDetected = false;
    }

    /**
     * Method that translates a game state observation to PDDL predicates.
     *
     * @param stateObservation State observation of the game.
     * @return Returns an uppercase String that contains the PDDL predicates.
     * The type of each object is specified in the predicate.
     */
    protected abstract String translateGameStateToPDDL(StateObservation stateObservation);

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
    protected abstract String createMoveAction(StateObservation stateObs,
        String actionStr, int currentX, int currentY, boolean isResourcePicked);

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
     * Method that translates a game state observation to a matrix of strings which
     * represent the elements of the game in each position according to the VGDDL
     * registry. There can be more than one game element in each position.
     *
     * @param stateObservation State observation of the game.
     * @return Returns a matrix containing the elements of the game in each position.
     */
    private HashSet<String>[][] getGameElementsMatrix(StateObservation stateObservation) {
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
                        gameStringMap[x][y].add(this.getGameElementFromObservation(gameState[x][y].get(i)));
                    }
                } else {
                    gameStringMap[x][y].add("background");
                }
            }
        }

        return gameStringMap;
    }

    public static void setGameConfigFile(String path) {
        AbstractRandomAgent.gameConfigFile = path;
    }

    public static void displayPostGameInformation() {
      System.out.println("##Tasks##");
      for (int i = 0; i < AbstractRandomAgent.executedActions.size(); i++) {
        System.out.println(String.format("[%d, %d]: %s", i, i+1, AbstractRandomAgent.executedActions.get(i)));
      }

      System.out.println("\n\n##States##");
      for (int i = 0; i < AbstractRandomAgent.gamePredicates.size(); i++) {
        System.out.println(String.format("[%d]: %s\n", i, AbstractRandomAgent.gamePredicates.get(i)));
      }
    }

    private String getGameElementFromObservation(Observation obs) {
      return VGDLRegistry.GetInstance().getRegisteredSpriteKey(obs.itype);
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


    private int getNumberResources(StateObservation stateObs) {
      int numResources = 0;

      ArrayList<Observation>[] resources = stateObs.getResourcesPositions();

      for (int i = 0; i < resources.length; i++) {
        numResources += resources[i].size();
      }

      return numResources;
    }
}
