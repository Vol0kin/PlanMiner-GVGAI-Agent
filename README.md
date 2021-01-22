# PlanMiner-GVGAI-Agent
Repository containing a GVGAI agent that is used by the [PlanMiner](https://github.com/Leontes/PlanMiner)
project to generate plan traces.

The developed agent executes each turn a random action that is selected between
the ones that are available. Once the game has finished its execution, it creates
a plan trace which contains the executed actions and the PDDL predicates associated
to each one of the game states.

## :unlock: Dependencies

- git
- OpenJDK 8
- Maven

## :wrench: Installation

First, clone this repository running the following command:

```sh
$ git clone https://github.com/Vol0kin/gvgai-pddl.git
```

Once the it has been cloned, change to the root directory of this project and
run the following command:

```sh
$ mvn package
```

This will create a JAR executable file in the `target` directory. This directory
also contains some external libraries which are required by the JAR file. Without
them, the program won't be able to run.

## :computer: Execution

To execute the generated JAR file run the following command:

```sh
$ java -jar target/RANDOM-AGENT-1.0.jar [options]
```

The allowed options are listed below:

```
Usage: GVGAI-PDDL [-hV] [--numeric] [-c=<configurationFile>] -g=<gameIdx>
                  -l=<levelIdx>
Launches a new GVGAI game played by a random agent or by a human.
  -c, --config=<configurationFile>
                           YAML configuration file that will be used by the
                             agent.
  -g, --game=<gameIdx>     Game to be played.
  -h, --help               Show this help message and exit.
  -l, --level=<levelIdx>   Level to be played.
      --numeric            Use NumericRandomAgent (numeric representation
                             of the world).
  -V, --version            Print version information and exit.
```
