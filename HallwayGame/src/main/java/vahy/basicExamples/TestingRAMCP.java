package vahy.basicExamples;

import vahy.api.learning.dataAggregator.DataAggregationAlgorithm;
import vahy.config.PaperAlgorithmConfig;
import vahy.config.AlgorithmConfigBuilder;
import vahy.config.EvaluatorType;
import vahy.config.SelectorType;
import vahy.impl.config.StochasticStrategy;
import vahy.api.experiment.SystemConfig;
import vahy.api.experiment.SystemConfigBuilder;
import vahy.environment.config.ConfigBuilder;
import vahy.environment.config.GameConfig;
import vahy.environment.state.StateRepresentation;
import vahy.experiment.Experiment;
import vahy.game.HallwayInstance;
import vahy.impl.search.tree.treeUpdateCondition.FixedUpdateCountTreeConditionFactory;
import vahy.paperGenerics.policy.flowOptimizer.FlowOptimizerType;
import vahy.paperGenerics.policy.riskSubtree.SubTreeRiskCalculatorType;
import vahy.paperGenerics.policy.riskSubtree.strategiesProvider.ExplorationExistingFlowStrategy;
import vahy.paperGenerics.policy.riskSubtree.strategiesProvider.ExplorationNonExistingFlowStrategy;
import vahy.paperGenerics.policy.riskSubtree.strategiesProvider.InferenceExistingFlowStrategy;
import vahy.paperGenerics.policy.riskSubtree.strategiesProvider.InferenceNonExistingFlowStrategy;
import vahy.api.learning.ApproximatorType;
import vahy.utils.ImmutableTuple;
import vahy.utils.ThirdPartBinaryUtils;

import java.util.function.Supplier;

public class TestingRAMCP {

    public static void main(String[] args) {
        ThirdPartBinaryUtils.cleanUpNativeTempFiles();

        GameConfig gameConfig = new ConfigBuilder()
            .reward(100)
            .noisyMoveProbability(0.1)
            .stepPenalty(1)
            .trapProbability(1)
            .stateRepresentation(StateRepresentation.COMPACT)
            .buildConfig();

        //  EXAMPLE 1
        var setup = createExperiment();
        var experiment = new Experiment(setup.getFirst(), setup.getSecond());
        experiment.run(gameConfig, HallwayInstance.BENCHMARK_05);

    }

    public static ImmutableTuple<PaperAlgorithmConfig, SystemConfig> createExperiment() {

        var systemConfig = new SystemConfigBuilder()
            .randomSeed(0)
            .setStochasticStrategy(StochasticStrategy.REPRODUCIBLE)
            .setDrawWindow(true)
            .setParallelThreadsCount(7)
            .setSingleThreadedEvaluation(true)
            .setEvalEpisodeCount(1000)
            .buildSystemConfig();


        var algorithmConfig = new AlgorithmConfigBuilder()
            //MCTS
            .cpuctParameter(3)
            .treeUpdateConditionFactory(new FixedUpdateCountTreeConditionFactory(100))
            //.mcRolloutCount(1)
            //NN
            .trainingBatchSize(1)
            .trainingEpochCount(10)
            // REINFORCEMENT
            .discountFactor(1)

            .batchEpisodeCount(100)
            .stageCount(100)

            .maximalStepCountBound(1000)
            .trainerAlgorithm(DataAggregationAlgorithm.EVERY_VISIT_MC)
            .approximatorType(ApproximatorType.HASHMAP_LR)
            .evaluatorType(EvaluatorType.RAMCP)
            .learningRate(0.1)

            .replayBufferSize(10000)
            .selectorType(SelectorType.UCB)

            .globalRiskAllowed(0.10)
            .riskSupplier(() -> 0.10)
            .explorationConstantSupplier(new Supplier<>() {
                @Override
                public Double get() {
                    return 0.0;
                }
            })
            .temperatureSupplier(new Supplier<>() {
                @Override
                public Double get() {
                    return 1.5;
                }
            })

            .setInferenceExistingFlowStrategy(InferenceExistingFlowStrategy.SAMPLE_OPTIMAL_FLOW)
            .setInferenceNonExistingFlowStrategy(InferenceNonExistingFlowStrategy.MAX_UCB_VISIT)
            .setExplorationExistingFlowStrategy(ExplorationExistingFlowStrategy.SAMPLE_OPTIMAL_FLOW_BOLTZMANN_NOISE)
            .setExplorationNonExistingFlowStrategy(ExplorationNonExistingFlowStrategy.SAMPLE_UCB_VISIT)
            .setFlowOptimizerType(FlowOptimizerType.HARD_HARD)
            .setSubTreeRiskCalculatorTypeForKnownFlow(SubTreeRiskCalculatorType.FLOW_SUM)
            .setSubTreeRiskCalculatorTypeForUnknownFlow(SubTreeRiskCalculatorType.MINIMAL_RISK_REACHABILITY)
            .buildAlgorithmConfig();
        return new ImmutableTuple<>(algorithmConfig, systemConfig);
    }
}
