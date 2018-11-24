package vahy.integration;

import org.testng.Assert;
import org.testng.annotations.Test;
import vahy.api.episode.EpisodeAggregator;
import vahy.api.model.reward.RewardAggregator;
import vahy.api.search.nodeEvaluator.NodeEvaluator;
import vahy.api.search.update.NodeTransitionUpdater;
import vahy.environment.ActionType;
import vahy.environment.agent.policy.environment.EnvironmentPolicy;
import vahy.environment.agent.policy.player.smart.Ucb1Policy;
import vahy.environment.config.ConfigBuilder;
import vahy.environment.config.GameConfig;
import vahy.environment.state.ImmutableStateImpl;
import vahy.game.HallwayGameInitialInstanceSupplier;
import vahy.game.NotValidGameStringRepresentationException;
import vahy.impl.episode.EpisodeAggregatorImpl;
import vahy.impl.model.observation.DoubleVectorialObservation;
import vahy.impl.model.reward.DoubleScalarReward;
import vahy.impl.model.reward.DoubleScalarRewardAggregator;
import vahy.impl.search.node.factory.MCTSSearchNodeMetadataFactory;
import vahy.impl.search.node.factory.SearchNodeBaseFactoryImpl;
import vahy.impl.search.node.nodeMetadata.MCTSNodeMetadata;
import vahy.impl.search.nodeEvaluator.OriginMonteCarloEvaluator;
import vahy.impl.search.tree.treeUpdateCondition.TreeUpdateConditionSuplierCountBased;
import vahy.impl.search.update.MCTSTransitionUpdater;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

public class IntegrationTest {

    @Test
    public void MonteCarloSimpleTest() throws IOException, NotValidGameStringRepresentationException {
        ClassLoader classLoader = IntegrationTest.class.getClassLoader();
        URL url = classLoader.getResource("examples/hallway_demo4.txt");
        File file = new File(url.getFile());

        SplittableRandom random = new SplittableRandom(0);
        GameConfig gameConfig = new ConfigBuilder().reward(10).noisyMoveProbability(0.0).stepPenalty(1).trapProbability(1).buildConfig();
        HallwayGameInitialInstanceSupplier hallwayGameInitialInstanceSupplier = new HallwayGameInitialInstanceSupplier(gameConfig, random, new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))));

        RewardAggregator<DoubleScalarReward> rewardAggregator = new DoubleScalarRewardAggregator();
        double discountFactor = 1.0; // 0.9

        // NodeTransitionUpdater<ActionType, DoubleScalarReward, DoubleVectorialObservation, MCTSNodeMetadata<DoubleScalarReward>, ImmutableStateImpl> transitionUpdater = new UniformAverageDiscountEstimateRewardTransitionUpdater<>(discountFactor, rewardAggregator);

        NodeTransitionUpdater<ActionType, DoubleScalarReward, DoubleVectorialObservation, MCTSNodeMetadata<DoubleScalarReward>, ImmutableStateImpl> transitionUpdater = new MCTSTransitionUpdater<>();

        NodeEvaluator<ActionType, DoubleScalarReward, DoubleVectorialObservation, MCTSNodeMetadata<DoubleScalarReward>, ImmutableStateImpl> rewardSimulator = new OriginMonteCarloEvaluator<>(
            new SearchNodeBaseFactoryImpl<>(new MCTSSearchNodeMetadataFactory<ActionType, DoubleScalarReward, DoubleVectorialObservation, ImmutableStateImpl>(rewardAggregator)),
            random,
            rewardAggregator,
            discountFactor);

        EpisodeAggregator<DoubleScalarReward> episodeAggregator = new EpisodeAggregatorImpl<>(
            1,
            10,
            hallwayGameInitialInstanceSupplier,
            immutableState ->
                new Ucb1Policy(
                    random,
                    new TreeUpdateConditionSuplierCountBased(10000),
                    3,
                    immutableState,
                    transitionUpdater,
                    rewardSimulator
                    ),
            new EnvironmentPolicy(random)
        );
        List<List<Double>> rewardHistory = episodeAggregator.runSimulation().stream().map(x -> x.stream().map(DoubleScalarReward::getValue).collect(Collectors.toList())).collect(Collectors.toList());
        Assert.assertTrue(rewardHistory.stream().allMatch(x -> x.size() == 17));
    }
}
