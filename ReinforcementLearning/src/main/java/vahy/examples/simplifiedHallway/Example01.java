package vahy.examples.simplifiedHallway;

import vahy.api.benchmark.EpisodeStatistics;
import vahy.api.experiment.CommonAlgorithmConfig;
import vahy.api.experiment.SystemConfig;
import vahy.api.model.StateWrapper;
import vahy.api.policy.PolicyMode;
import vahy.impl.RoundBuilder;
import vahy.impl.benchmark.EpisodeStatisticsCalculatorBase;
import vahy.impl.episode.EpisodeResultsFactoryBase;
import vahy.impl.learning.dataAggregator.FirstVisitMonteCarloDataAggregator;
import vahy.impl.learning.trainer.PredictorTrainingSetup;
import vahy.impl.learning.trainer.ValueDataMaker;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.policy.ValuePolicy;
import vahy.impl.predictor.DataTablePredictor;
import vahy.impl.runner.PolicyDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class Example01 {

    private Example01() {
    }

    public static void main(String[] args) {

        var config = new SHConfigBuilder()
            .isModelKnown(true)
            .reward(100)
            .gameStringRepresentation(SHInstance.BENCHMARK_03)
            .maximalStepCountBound(500)
            .stepPenalty(10)
            .trapProbability(0.10)
            .buildConfig();


        var systemConfig = new SystemConfig(987568, true, 1, false, 10000, 0, false, false, false, Path.of("TEST_PATH"), null);

        var algorithmConfig = new CommonAlgorithmConfig() {

            @Override
            public String toLog() {
                return "";
            }

            @Override
            public String toFile() {
                return "";
            }

            @Override
            public int getBatchEpisodeCount() {
                return 100;
            }

            @Override
            public int getStageCount() {
                return 1000;
            }
        };

        double discountFactor = 1;

        var trainablePredictor = new DataTablePredictor(new double[] {0.0});
        var episodeDataMaker = new ValueDataMaker<SHAction, SHState>(discountFactor, 1);
        var dataAggregator = new FirstVisitMonteCarloDataAggregator(new LinkedHashMap<>());

        var predictorTrainingSetup = new PredictorTrainingSetup<>(
            1,
            trainablePredictor,
            episodeDataMaker,
            dataAggregator
        );

        var playerSupplier = new PolicyDefinition<SHAction, DoubleVector, SHState>(
            1,
            1,
            (initialState, policyMode, policyId, random) -> {
                if(policyMode == PolicyMode.INFERENCE) {
                    return new ValuePolicy<>(random.split(), 1, trainablePredictor, 0.0);
                }
                return new ValuePolicy<>(random.split(), 1, trainablePredictor, 0.5);
            },
            List.of(predictorTrainingSetup)
        );

        var policyArgumentsList = new ArrayList<PolicyDefinition<SHAction, DoubleVector, SHState>>();
        policyArgumentsList.add(playerSupplier);

        var roundBuilder = new RoundBuilder<SHConfig, SHAction, SHState, EpisodeStatistics>()
            .setRoundName("SHIntegrationTest")
            .setAdditionalDataPointGeneratorListSupplier(null)
            .setCommonAlgorithmConfig(algorithmConfig)
            .setProblemConfig(config)
            .setSystemConfig(systemConfig)
            .setProblemInstanceInitializerSupplier((config_, splittableRandom_) -> policyMode -> new SHInstanceSupplier(config_, splittableRandom_).createInitialState(policyMode))
            .setResultsFactory(new EpisodeResultsFactoryBase<>())
            .setStatisticsCalculator(new EpisodeStatisticsCalculatorBase<>())
            .setStateStateWrapperInitializer(StateWrapper::new)
            .setPlayerPolicySupplierList(policyArgumentsList);
        var result = roundBuilder.execute();

        System.out.println(result.getEvaluationStatistics().getTotalPayoffAverage().get(1));
    }

}
