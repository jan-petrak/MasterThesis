package vahy.experiment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vahy.PaperGenericsPrototype;
import vahy.api.episode.TrainerAlgorithm;
import vahy.api.search.nodeSelector.NodeSelector;
import vahy.data.HallwayGameSupplierFactory;
import vahy.environment.HallwayAction;
import vahy.environment.config.GameConfig;
import vahy.environment.state.EnvironmentProbabilities;
import vahy.environment.state.HallwayStateImpl;
import vahy.game.HallwayGameInitialInstanceSupplier;
import vahy.game.NotValidGameStringRepresentationException;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.model.reward.DoubleReward;
import vahy.impl.model.reward.DoubleScalarRewardAggregator;
import vahy.impl.search.node.factory.SearchNodeBaseFactoryImpl;
import vahy.paperGenerics.PaperMetadata;
import vahy.paperGenerics.PaperMetadataFactory;
import vahy.paperGenerics.PaperModel;
import vahy.paperGenerics.PaperNodeEvaluator;
import vahy.paperGenerics.PaperNodeSelector;
import vahy.paperGenerics.PaperTreeUpdater;
import vahy.paperGenerics.benchmark.PaperBenchmark;
import vahy.paperGenerics.benchmark.PaperBenchmarkingPolicy;
import vahy.paperGenerics.policy.PaperPolicySupplier;
import vahy.paperGenerics.policy.TrainablePaperPolicySupplier;
import vahy.paperGenerics.policy.environment.EnvironmentPolicySupplier;
import vahy.paperGenerics.reinforcement.EmptyApproximator;
import vahy.paperGenerics.reinforcement.TrainableApproximator;
import vahy.paperGenerics.reinforcement.learning.AbstractTrainer;
import vahy.paperGenerics.reinforcement.learning.EveryVisitMonteCarloTrainer;
import vahy.paperGenerics.reinforcement.learning.FirstVisitMonteCarloTrainer;
import vahy.paperGenerics.reinforcement.learning.ReplayBufferTrainer;
import vahy.paperGenerics.reinforcement.learning.tf.TFModel;
import vahy.riskBasedSearch.RiskBasedSelector;
import vahy.riskBasedSearch.RiskBasedSelectorVahy;
import vahy.riskBasedSearch.SelectorType;
import vahy.utils.EnumUtils;
import vahy.utils.ImmutableTuple;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.SplittableRandom;

public class Experiment {

    private final Logger logger = LoggerFactory.getLogger(Experiment.class);

    public void prepareAndRun(ImmutableTuple<GameConfig, ExperimentSetup> setup, SplittableRandom random) throws NotValidGameStringRepresentationException, IOException {
        initializeModelAndRun(setup, random);
    }

    private void initializeModelAndRun(ImmutableTuple<GameConfig, ExperimentSetup> setup, SplittableRandom random) throws NotValidGameStringRepresentationException, IOException {
        var provider = new HallwayGameSupplierFactory();
        var hallwayGameInitialInstanceSupplier = provider.getInstanceProvider(setup.getSecond().getHallwayInstance(), setup.getFirst(), random);
        var inputLenght = hallwayGameInitialInstanceSupplier.createInitialState().getPlayerObservation().getObservedVector().length;
        try(TFModel model = new TFModel(
            inputLenght,
            PaperModel.POLICY_START_INDEX + HallwayAction.playerActions.length,
            setup.getSecond().getTrainingEpochCount(),
            setup.getSecond().getTrainingBatchSize(),
            PaperGenericsPrototype.class.getClassLoader().getResourceAsStream("tfModel/graph_" + setup.getSecond().getHallwayInstance().toString() + ".pb").readAllBytes(),
            random)
        ) //            SavedModelBundle.load("C:/Users/Snurka/init_model", "serve"),
        {
//            TrainableApproximator<DoubleVector> trainableApproximator = new TrainableApproximator<>(model);
            TrainableApproximator<DoubleVector> trainableApproximator = new EmptyApproximator<>();
            createPolicyAndRunProcess(setup, random, hallwayGameInitialInstanceSupplier, trainableApproximator);
        }
    }

    private void createPolicyAndRunProcess(ImmutableTuple<GameConfig, ExperimentSetup> setup,
                                                  SplittableRandom random,
                                                  HallwayGameInitialInstanceSupplier hallwayGameInitialInstanceSupplier,
                                                  TrainableApproximator<DoubleVector> approximator) {
        var experimentSetup = setup.getSecond();
        var rewardAggregator = new DoubleScalarRewardAggregator();
        var clazz = HallwayAction.class;
        var searchNodeMetadataFactory = new PaperMetadataFactory<HallwayAction, DoubleReward, DoubleVector, EnvironmentProbabilities, HallwayStateImpl>(rewardAggregator);
        var paperTreeUpdater = new PaperTreeUpdater<HallwayAction, DoubleVector, EnvironmentProbabilities, HallwayStateImpl>();
        var nodeSelector = createNodeSelector(experimentSetup.getCpuctParameter(), random, experimentSetup.getGlobalRiskAllowed(), experimentSetup.getSelectorType());

        var nnbasedEvaluator = new PaperNodeEvaluator<>(
            new SearchNodeBaseFactoryImpl<>(searchNodeMetadataFactory),
            approximator,
            EnvironmentProbabilities::getProbabilities,
            HallwayAction.playerActions, HallwayAction.environmentActions);

        var paperTrainablePolicySupplier = new TrainablePaperPolicySupplier<>(
            clazz,
            searchNodeMetadataFactory,
            experimentSetup.getGlobalRiskAllowed(),
            random,
            nodeSelector,
            nnbasedEvaluator,
            paperTreeUpdater,
            experimentSetup.getTreeUpdateConditionFactory(),
            experimentSetup.getExplorationConstantSupplier(),
            experimentSetup.getTemperatureSupplier(),
            rewardAggregator
        );

        var nnBasedPolicySupplier = new PaperPolicySupplier<>(
            clazz,
            searchNodeMetadataFactory,
            experimentSetup.getGlobalRiskAllowed(),
            random,
            nodeSelector,
            nnbasedEvaluator,
            paperTreeUpdater,
            experimentSetup.getTreeUpdateConditionFactory(),
            rewardAggregator);

        var trainer = getAbstractTrainer(
            experimentSetup.getTrainerAlgorithm(),
            random,
            hallwayGameInitialInstanceSupplier,
            experimentSetup.getDiscountFactor(),
            nnbasedEvaluator,
            paperTrainablePolicySupplier,
            experimentSetup.getReplayBufferSize(),
            experimentSetup.getMaximalStepCountBound());

        runProcess(experimentSetup, trainer, random, hallwayGameInitialInstanceSupplier, nnbasedEvaluator, nnBasedPolicySupplier);
    }

    private void runProcess(ExperimentSetup experimentSetup,
                                   AbstractTrainer trainer,
                                   SplittableRandom random,
                                   HallwayGameInitialInstanceSupplier hallwayGameInitialInstanceSupplier,
                                   PaperNodeEvaluator<HallwayAction, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> nnbasedEvaluator,
                                   PaperPolicySupplier<HallwayAction, DoubleReward, DoubleVector, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> nnBasedPolicySupplier) {
        long trainingTimeInMs = trainPolicy(experimentSetup, trainer);
        evaluatePolicy(random, hallwayGameInitialInstanceSupplier, experimentSetup, nnbasedEvaluator, nnBasedPolicySupplier, trainingTimeInMs);
    }

    private long trainPolicy(ExperimentSetup experimentSetup, AbstractTrainer trainer) {
        long trainingStart = System.currentTimeMillis();
        for (int i = 0; i < experimentSetup.getStageCount(); i++) {
            logger.info("Training policy for [{}]th iteration", i);
            trainer.trainPolicy(experimentSetup.getBatchEpisodeCount());
            trainer.printDataset();
        }
        return System.currentTimeMillis() - trainingStart;
    }

    private void evaluatePolicy(SplittableRandom random,
                                       HallwayGameInitialInstanceSupplier hallwayGameInitialInstanceSupplier,
                                       ExperimentSetup experimentSetup,
                                       PaperNodeEvaluator<HallwayAction, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> nnbasedEvaluator,
                                       PaperPolicySupplier<HallwayAction, DoubleReward, DoubleVector, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> nnBasedPolicySupplier,
                                       long trainingTimeInMs) {
        logger.info("PaperPolicy test starts");
        String nnBasedPolicyName = "NNBased";
        var benchmark = new PaperBenchmark<>(
            Arrays.asList(new PaperBenchmarkingPolicy<>(nnBasedPolicyName, nnBasedPolicySupplier)),
            new EnvironmentPolicySupplier(random),
            hallwayGameInitialInstanceSupplier
        );
        long start = System.currentTimeMillis();
        var policyResultList = benchmark.runBenchmark(experimentSetup.getEvalEpisodeCount(), experimentSetup.getMaximalStepCountBound());
        long end = System.currentTimeMillis();
        logger.info("Benchmarking took [{}] milliseconds", end - start);

        var nnResults = policyResultList
            .stream()
            .filter(x -> x.getBenchmarkingPolicy().getPolicyName().equals(nnBasedPolicyName))
            .findFirst()
            .get();
        logger.info("NN Based Average reward: [{}]", nnResults.getAverageReward());
        logger.info("NN Based millis per episode: [{}]", nnResults.getAverageMillisPerEpisode());
        logger.info("NN Based total expanded nodes: [{}]", nnbasedEvaluator.getNodesExpandedCount());
        logger.info("NN Based kill ratio: [{}]", nnResults.getRiskHitRatio());
        logger.info("NN Based kill counter: [{}]", nnResults.getRiskHitCounter());
        logger.info("NN Based training time: [{}]ms", trainingTimeInMs);
    }

    private AbstractTrainer<
        HallwayAction,
        EnvironmentProbabilities,
        PaperMetadata<HallwayAction, DoubleReward>,
        HallwayStateImpl>
    getAbstractTrainer(TrainerAlgorithm trainerAlgorithm,
                       SplittableRandom random,
                       HallwayGameInitialInstanceSupplier hallwayGameInitialInstanceSupplier,
                       double discountFactor,
                       PaperNodeEvaluator<HallwayAction, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> nodeEvaluator,
                       TrainablePaperPolicySupplier<HallwayAction, DoubleReward, DoubleVector, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> trainablePaperPolicySupplier,
                       int replayBufferSize,
                       int stepCountLimit) {
        switch(trainerAlgorithm) {
            case REPLAY_BUFFER:
                return new ReplayBufferTrainer<>(
                    hallwayGameInitialInstanceSupplier,
                    trainablePaperPolicySupplier,
                    new EnvironmentPolicySupplier(random),
                    nodeEvaluator,
                    discountFactor,
                    new DoubleScalarRewardAggregator(),
                    stepCountLimit,
                    new LinkedList<>(),
                    replayBufferSize);
            case FIRST_VISIT_MC:
                return new FirstVisitMonteCarloTrainer<>(
                    hallwayGameInitialInstanceSupplier,
                    trainablePaperPolicySupplier,
                    new EnvironmentPolicySupplier(random),
                    nodeEvaluator,
                    discountFactor,
                    new DoubleScalarRewardAggregator(),
                    stepCountLimit);
            case EVERY_VISIT_MC:
                return new EveryVisitMonteCarloTrainer<>(
                    hallwayGameInitialInstanceSupplier,
                    trainablePaperPolicySupplier,
                    new EnvironmentPolicySupplier(random),
                    nodeEvaluator,
                    discountFactor,
                    new DoubleScalarRewardAggregator(),
                    stepCountLimit);
            default:
                throw EnumUtils.createExceptionForUnknownEnumValue(trainerAlgorithm);
        }
    }

    private NodeSelector<HallwayAction, DoubleReward, DoubleVector, EnvironmentProbabilities, PaperMetadata<HallwayAction, DoubleReward>, HallwayStateImpl> createNodeSelector(
        double cpuctParameter,
        SplittableRandom random,
        double totalRiskAllowed,
        SelectorType selectorType)
    {
        switch (selectorType) {
            case UCB:
                return new PaperNodeSelector<>(cpuctParameter, random);
            case VAHY_1:
                return new RiskBasedSelectorVahy<>(cpuctParameter, random);
            case LINEAR_HARD_VS_UCB:
                return new RiskBasedSelector<>(cpuctParameter, random, totalRiskAllowed);
                default:
                    throw EnumUtils.createExceptionForUnknownEnumValue(selectorType);
        }
    }
}
