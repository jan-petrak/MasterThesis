package vahy.examples.patrolling;

import vahy.api.episode.PolicyCategoryInfo;
import vahy.api.episode.PolicyShuffleStrategy;
import vahy.api.experiment.CommonAlgorithmConfigBase;
import vahy.api.experiment.SystemConfig;
import vahy.api.model.StateWrapper;
import vahy.api.policy.OuterDefPolicySupplier;
import vahy.api.policy.Policy;
import vahy.api.policy.PolicyMode;
import vahy.impl.RoundBuilder;
import vahy.impl.learning.dataAggregator.ReplayBufferDataAggregator;
import vahy.impl.learning.trainer.PredictorTrainingSetup;
import vahy.impl.learning.trainer.ValueDataMaker;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.policy.RandomizedValuePolicy;
import vahy.impl.policy.ValuePolicy;
import vahy.impl.predictor.TrainableApproximator;
import vahy.impl.predictor.tensorflow.TensorflowTrainablePredictor;
import vahy.impl.runner.PolicyDefinition;
import vahy.tensorflow.TFHelper;
import vahy.tensorflow.TFModelImproved;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;

public class PatrollingExample_london_06 {

    private PatrollingExample_london_06() {
    }

    public static PolicyDefinition<PatrollingAction, DoubleVector, PatrollingState> getDefenderPolicy(PatrollingConfig patrollingConfig, SystemConfig systemConfig, int defenderLookbackSize) throws IOException, InterruptedException {

        double discountFactor = 1.0;

        var sampleState = new PatrollingInitializer(patrollingConfig, new SplittableRandom(0)).createInitialState(PolicyMode.TRAINING);
        var modelInputSize = new StateWrapper<>(PatrollingState.DEFENDER_ID, defenderLookbackSize, sampleState).getObservation().getObservedVector().length;

        var path = Paths.get("PythonScripts", "tensorflow_models", "patrollingExample_london_06", "create_value_model_london_06.py");
        var tfModelAsBytes = TFHelper.loadTensorFlowModel(path, systemConfig.getPythonVirtualEnvPath(), systemConfig.getRandomSeed(), modelInputSize, 1, 0);
        var tfModel = new TFModelImproved(
            modelInputSize,
            1,
            1024,
            1,
            0.8,
            0.001,
            tfModelAsBytes,
            systemConfig.getParallelThreadsCount(),
            new SplittableRandom(systemConfig.getRandomSeed()));


        var trainablePredictor = new TrainableApproximator(new TensorflowTrainablePredictor(tfModel));
        var dataAggregator = new ReplayBufferDataAggregator(100000);
        var episodeDataMaker = new ValueDataMaker<PatrollingAction, PatrollingState>(discountFactor, PatrollingState.DEFENDER_ID, defenderLookbackSize, dataAggregator);

        var predictor = new PredictorTrainingSetup<>(PatrollingState.DEFENDER_ID, trainablePredictor, episodeDataMaker, dataAggregator);

        var supplier = new OuterDefPolicySupplier<PatrollingAction, DoubleVector, PatrollingState>() {
            @Override
            public Policy<PatrollingAction, DoubleVector, PatrollingState> apply(StateWrapper<PatrollingAction, DoubleVector, PatrollingState> initialState, PolicyMode policyMode, int policyId, SplittableRandom random) {
                if (policyMode == PolicyMode.INFERENCE) {
                    return new RandomizedValuePolicy<>(random.split(), policyId, trainablePredictor, 0.0);
                }
                return new RandomizedValuePolicy<>(random.split(), policyId, trainablePredictor, 0.1);
            };
        };
        return new PolicyDefinition<>(PatrollingState.DEFENDER_ID, 1, defenderLookbackSize, supplier, List.of(predictor));
    }

    public static PolicyDefinition<PatrollingAction, DoubleVector, PatrollingState> getAttackerPolicy(PatrollingConfig patrollingConfig, SystemConfig systemConfig, int attackerLookbackSize) throws IOException, InterruptedException {

        double discountFactor = 1.0;

        var sampleState = new PatrollingInitializer(patrollingConfig, new SplittableRandom(0)).createInitialState(PolicyMode.TRAINING);
        var modelInputSize = new StateWrapper<>(PatrollingState.ATTACKER_ID, attackerLookbackSize, sampleState).getObservation().getObservedVector().length;

        var path = Paths.get("PythonScripts", "tensorflow_models", "patrollingExample_london_06", "create_value_model_london_06.py");
        var tfModelAsBytes = TFHelper.loadTensorFlowModel(path, systemConfig.getPythonVirtualEnvPath(), systemConfig.getRandomSeed(), modelInputSize, 1, 0);
        var tfModel = new TFModelImproved(
            modelInputSize,
            1,
            1024,
            1,
            0.8,
            0.001,
            tfModelAsBytes,
            systemConfig.getParallelThreadsCount(),
            new SplittableRandom(systemConfig.getRandomSeed()));


        var trainablePredictor2 = new TrainableApproximator(new TensorflowTrainablePredictor(tfModel));
        var dataAggregator2 = new ReplayBufferDataAggregator(100000);
        var episodeDataMaker2 = new ValueDataMaker<PatrollingAction, PatrollingState>(discountFactor, PatrollingState.ATTACKER_ID, attackerLookbackSize, dataAggregator2);

        var predictor2 = new PredictorTrainingSetup<>(PatrollingState.ATTACKER_ID, trainablePredictor2, episodeDataMaker2, dataAggregator2);

        var supplier2 = new OuterDefPolicySupplier<PatrollingAction, DoubleVector, PatrollingState>() {
            @Override
            public Policy<PatrollingAction, DoubleVector, PatrollingState> apply(StateWrapper<PatrollingAction, DoubleVector, PatrollingState> initialState, PolicyMode policyMode, int policyId, SplittableRandom random) {
                if (policyMode == PolicyMode.INFERENCE) {
                    return new ValuePolicy<PatrollingAction, PatrollingState>(random.split(), policyId, trainablePredictor2, 0.0);
                }
                return new ValuePolicy<PatrollingAction, PatrollingState>(random.split(), policyId, trainablePredictor2, 0.1);
            };
        };

        return new PolicyDefinition<PatrollingAction, DoubleVector, PatrollingState>(PatrollingState.ATTACKER_ID, 1, attackerLookbackSize, supplier2, List.of(predictor2));
    }



    public static void main(String[] args) throws IOException, InterruptedException {

        var systemConfig = new SystemConfig(
            987568,
            false,
            7,
            true,
            10_000,
            1000,
            true,
            false,
            false,
            Path.of("TEST_PATH"),
            System.getProperty("user.home") + "/.local/virtualenvs/tf_2_3/bin/python");

        var algorithmConfig = new CommonAlgorithmConfigBase(10000, 100);

        var moveCostMatrix = new double[][] {
            new double[] {-100.0, 1622.34, 2206.24, 3431.17, 2758.20, 1084.68},
            new double[] {1622.34, -100.0, 583.91, 1808.83, 1135.86, 816.23},
            new double[] {2206.24, 583.91, -100.0, 1224.93, 551.96, 1400.14},
            new double[] {3431.17, 1808.83, 1224.93, -100.0, 706.35, 2625.06},
            new double[] {2758.20, 1135.86, 551.96, 706.35, -100-.0, 1952.09},
            new double[] {1084.68, 816.23, 1400.14, 2625.06, 1952.09, -100.0}
        };

        var graph = new boolean[moveCostMatrix.length][];

        for (int i = 0; i < moveCostMatrix.length; i++) {
            graph[i] = new boolean[moveCostMatrix.length];
            for (int j = 0; j < moveCostMatrix.length; j++) {
                graph[i][j] = moveCostMatrix[i][j] >= 0;
            }
        }

        var isTargetSet = new HashSet<Integer>();
        var attackLengthMap = new HashMap<Integer, Double>();
        var attackCostMap = new HashMap<Integer, Double>();

        var givenCosts = List.of(470.0, 470.0, 330.0, 400.0, 459.99999999999994, 509.99999999999994);
        var givenAttackLengths = List.of(5025.0, 5025.0, 5025.0, 5025.0, 5025.0, 5025.0);

        for (int i = 0; i < graph.length; i++) {
            isTargetSet.add(i);
            attackLengthMap.put(i, givenAttackLengths.get(i));
            attackCostMap.put(i, givenCosts.get(i));
        }

        var graphDef = new GraphDef(graph, moveCostMatrix, isTargetSet, attackLengthMap, attackCostMap);
        var patrollingConfig = new PatrollingConfig(1000, false, 0, 2, List.of(new PolicyCategoryInfo(false, 1, 2)), PolicyShuffleStrategy.NO_SHUFFLE, graphDef);

        var defenderLookbackSize = 5;
        var attackerLookbackSize = 5;


        var policyArgumentsList = List.of(
            getDefenderPolicy(patrollingConfig, systemConfig, defenderLookbackSize),
            getAttackerPolicy(patrollingConfig, systemConfig, attackerLookbackSize)
        );


//        var statsCalculator = new EpisodeStatisticsCalculatorBase<>();
//
//
//
//        var additionalDataPointGeneratorList = new ArrayList<DataPointGeneratorGeneric<EpisodeStatistics>>();
//        additionalDataPointGeneratorList.add(new DataPointGeneratorGeneric<>("Win ratio", episodeStatistics -> episodeStatistics.));



        var roundBuilder = RoundBuilder.getRoundBuilder("Patrolling", patrollingConfig, systemConfig, algorithmConfig, policyArgumentsList, PatrollingInitializer::new);
        var result = roundBuilder.execute();

        var playerOneResult = result.getEvaluationStatistics().getTotalPayoffAverage().get(PatrollingState.DEFENDER_ID);
        var playerTwoResult = result.getEvaluationStatistics().getTotalPayoffAverage().get(PatrollingState.ATTACKER_ID);

        System.out.println("Defender: " + playerOneResult);
        System.out.println("Attacker: " + playerTwoResult);


    }


}
