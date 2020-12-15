package jan.examples.simplifiedHallway;

import jan.tuning.*;
import jan.tuning.value.TuningValue;
import jan.tuning.param.TuningParamEnum;
import jan.tuning.param.TuningParamNumber;
import jan.tuning.value.TuningValueEnum;
import vahy.api.experiment.CommonAlgorithmConfigBase;
import vahy.api.experiment.ProblemConfig;
import vahy.api.experiment.SystemConfig;
import vahy.examples.simplifiedHallway.*;
import vahy.impl.RoundBuilder;
import vahy.impl.benchmark.PolicyResults;
import vahy.impl.episode.EpisodeResultsFactoryBase;
import vahy.impl.learning.dataAggregator.FirstVisitMonteCarloDataAggregator;
import vahy.impl.learning.trainer.PredictorTrainingSetup;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.runner.PolicyDefinition;
import vahy.impl.search.node.factory.SearchNodeBaseFactoryImpl;
import vahy.impl.search.tree.treeUpdateCondition.FixedUpdateCountTreeConditionFactory;
import vahy.paperGenerics.PaperStateWrapper;
import vahy.paperGenerics.PaperTreeUpdater;
import vahy.paperGenerics.benchmark.PaperEpisodeStatistics;
import vahy.paperGenerics.benchmark.PaperEpisodeStatisticsCalculator;
import vahy.paperGenerics.evaluator.PaperNodeEvaluator;
import vahy.paperGenerics.metadata.PaperMetadata;
import vahy.paperGenerics.metadata.PaperMetadataFactory;
import vahy.paperGenerics.policy.PaperPolicyImpl;
import vahy.paperGenerics.policy.RiskAverseSearchTree;
import vahy.paperGenerics.policy.flowOptimizer.FlowOptimizerType;
import vahy.paperGenerics.policy.linearProgram.NoiseStrategy;
import vahy.paperGenerics.policy.riskSubtree.SubTreeRiskCalculatorType;
import vahy.paperGenerics.policy.riskSubtree.strategiesProvider.*;
import vahy.paperGenerics.reinforcement.PaperDataTablePredictorWithLr;
import vahy.paperGenerics.reinforcement.learning.PaperEpisodeDataMaker_V1;
import vahy.paperGenerics.selector.PaperNodeSelector;
import vahy.test.ConvergenceAssert;
import vahy.utils.EnumUtils;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

public class ExampleSH3Tuning {
    public static void main(String[] args) {
        ExampleSH3Tuning example = new ExampleSH3Tuning();
        example.runExample();
    }

    private void runExample() {
        TuningConfig tuningConfig = new TuningConfigBuilder()
                .setName("SH3_learningRate_flowOptimizerType")
                .setSamples(10)
                .build();

        HashMap<String, TuningValue> fixedValues = new HashMap<>();
        fixedValues.put("inferenceExistingFlowStrategy", new TuningValueEnum<>(InferenceExistingFlowStrategy.SAMPLE_OPTIMAL_FLOW));

        TuningParamConfig tuneParams = new TuningParamConfig(fixedValues,
                new TuningParamNumber("learningRate", 0.01, 1, 0.01),
                new TuningParamEnum<>("flowOptimizerType", FlowOptimizerType.values()));

        var tuning = new Tuning<>(tuningConfig, tuneParams, this::solveSH3);
        TuningResult result = tuning.run();

        TuningTrial bestTrial = null; // result.getBestTrial();
    }

    private PolicyResults<SHAction, DoubleVector, SHRiskState, PaperEpisodeStatistics> solveSH3(TuningTrial trial) {
        var config = new SHConfigBuilder()
                .isModelKnown(true)
                .reward(100)
                .gameStringRepresentation(SHInstance.BENCHMARK_03)
                .maximalStepCountBound(100)
                .stepPenalty(10)
                .trapProbability(0.5)
                .buildConfig();

        var systemConfig = new SystemConfig(
                9548681,
                false,
                ConvergenceAssert.TEST_THREAD_COUNT,
                false,
                5000,
                0,
                false,
                false,
                false,
                Path.of("TEST_PATH"),
                null);

        var algorithmConfig = new CommonAlgorithmConfigBase(100, 100);

        Supplier<Double> temperatureSupplier = new Supplier<>() {
            private int callCount = 0;
            @Override
            public Double get() {
                callCount++;
                return Math.exp(-callCount / 5000.0);
            }
        };

        var player = getPlayer(trial, config, 1, temperatureSupplier, 0.5);

        var roundBuilder = RoundBuilder.getRoundBuilder(
                "SH3_learningRate_flowOptimizerType",
                config,
                systemConfig,
                algorithmConfig,
                List.of(player),
                null,
                SHRiskInstanceSupplier::new,
                PaperStateWrapper::new,
                new PaperEpisodeStatisticsCalculator<>(),
                new EpisodeResultsFactoryBase<>()
        );

        return roundBuilder.execute();
    }

    private PolicyDefinition<SHAction, DoubleVector, SHRiskState> getPlayer(TuningTrial trial, ProblemConfig config, int treeUpdateCount, Supplier<Double> temperatureSupplier, double riskAllowed) {

        var playerId = 1;
        var totalEntityCount = 2;
        var actionClass = SHAction.class;
        var totalActionCount = actionClass.getEnumConstants().length;
        var predictorTrainingSetup = getTrainingSetup(trial, playerId, totalEntityCount, totalActionCount);
        var trainablePredictor = predictorTrainingSetup.getTrainablePredictor();

        var metadataFactory = new PaperMetadataFactory<SHAction, DoubleVector, SHRiskState>(actionClass, totalEntityCount);
        var searchNodeFactory = new SearchNodeBaseFactoryImpl<SHAction, DoubleVector, PaperMetadata<SHAction>, SHRiskState>(actionClass, metadataFactory);

        var totalRiskAllowedInference = riskAllowed;
        Supplier<Double> explorationSupplier = () -> 1.0;
        Supplier<Double> trainingRiskSupplier = () -> totalRiskAllowedInference;

        var treeUpdateConditionFactory = new FixedUpdateCountTreeConditionFactory(treeUpdateCount);

        var strategiesProvider = new StrategiesProvider<SHAction, DoubleVector, PaperMetadata<SHAction>, SHRiskState>(
                actionClass,
                (InferenceExistingFlowStrategy) trial.getValue("inferenceExistingFlowStrategy"),
                InferenceNonExistingFlowStrategy.MAX_UCB_VALUE,
                ExplorationExistingFlowStrategy.SAMPLE_OPTIMAL_FLOW_BOLTZMANN_NOISE,
                ExplorationNonExistingFlowStrategy.SAMPLE_UCB_VALUE_WITH_TEMPERATURE,
                (FlowOptimizerType) trial.getValue("flowOptimizerType"),
                SubTreeRiskCalculatorType.MINIMAL_RISK_REACHABILITY,
                NoiseStrategy.NOISY_05_06);

        var updater = new PaperTreeUpdater<SHAction, DoubleVector, SHRiskState>();
        var nodeEvaluator = new PaperNodeEvaluator<SHAction, SHRiskState>(searchNodeFactory, trainablePredictor, config.isModelKnown());
        var cpuctParameter = 1.0;


        return new PolicyDefinition<SHAction, DoubleVector, SHRiskState>(
                playerId,
                1,
                (initialState_, policyMode_, policyId_, random_) -> {
                    Supplier<PaperNodeSelector<SHAction, DoubleVector, SHRiskState>> nodeSelectorSupplier = () -> new PaperNodeSelector<>(random_, config.isModelKnown(), cpuctParameter, totalActionCount);
                    var node = searchNodeFactory.createNode(initialState_, metadataFactory.createEmptyNodeMetadata(), new EnumMap<>(actionClass));
                    switch(policyMode_) {
                        case INFERENCE:
                            return new PaperPolicyImpl<>(
                                    policyId_,
                                    random_,
                                    treeUpdateConditionFactory.create(),
                                    new RiskAverseSearchTree<SHAction, DoubleVector, PaperMetadata<SHAction>, SHRiskState>(
                                            searchNodeFactory,
                                            node,
                                            nodeSelectorSupplier.get(),
                                            updater,
                                            nodeEvaluator,
                                            random_,
                                            totalRiskAllowedInference,
                                            strategiesProvider));
                        case TRAINING:
                            return new PaperPolicyImpl<SHAction, DoubleVector, PaperMetadata<SHAction>, SHRiskState>(
                                    policyId_,
                                    random_,
                                    treeUpdateConditionFactory.create(),
                                    new RiskAverseSearchTree<SHAction, DoubleVector, PaperMetadata<SHAction>, SHRiskState>(
                                            searchNodeFactory,
                                            node,
                                            nodeSelectorSupplier.get(),
                                            updater,
                                            nodeEvaluator,
                                            random_,
                                            trainingRiskSupplier.get(),
                                            strategiesProvider),
                                    explorationSupplier.get(),
                                    temperatureSupplier.get());
                        default: throw EnumUtils.createExceptionForNotExpectedEnumValue(policyMode_);
                    }
                },
                List.of(predictorTrainingSetup)
        );
    }

    private PredictorTrainingSetup<SHAction, DoubleVector, SHRiskState> getTrainingSetup(TuningTrial trial, int playerId, int totalEntityCount, int totalActionCount) {
        double discountFactor = 1;

        var defaultPrediction = new double[totalEntityCount * 2 + totalActionCount];
        for (int i = totalEntityCount * 2; i < defaultPrediction.length; i++) {

            defaultPrediction[i] = 1.0 / totalActionCount;
        }

        var trainablePredictor = new PaperDataTablePredictorWithLr(defaultPrediction, (double) trial.getValue("learningRate"), totalActionCount, totalEntityCount);
        var episodeDataMaker = new PaperEpisodeDataMaker_V1<SHAction, SHRiskState>(discountFactor, totalActionCount, playerId);
        var dataAggregator = new FirstVisitMonteCarloDataAggregator(new LinkedHashMap<>());

        var predictorTrainingSetup = new PredictorTrainingSetup<SHAction, DoubleVector, SHRiskState>(
                playerId,
                trainablePredictor,
                episodeDataMaker,
                dataAggregator
        );


        return predictorTrainingSetup;
    }
}
