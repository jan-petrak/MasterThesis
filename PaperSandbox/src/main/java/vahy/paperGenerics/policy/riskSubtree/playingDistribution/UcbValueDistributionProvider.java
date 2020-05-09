package vahy.paperGenerics.policy.riskSubtree.playingDistribution;

import vahy.api.model.Action;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.paperGenerics.PaperState;
import vahy.paperGenerics.metadata.PaperMetadata;
import vahy.paperGenerics.policy.riskSubtree.ConstantRiskCalculator;
import vahy.utils.RandomDistributionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public class UcbValueDistributionProvider<
    TAction extends Enum<TAction> & Action,
    TObservation extends Observation,
    TSearchNodeMetadata extends PaperMetadata<TAction>,
    TState extends PaperState<TAction, TObservation, TState>>
    extends AbstractPlayingDistributionProvider<TAction, TObservation, TSearchNodeMetadata, TState> {

    public UcbValueDistributionProvider(boolean applyTemperature) {
        super(applyTemperature, () -> new ConstantRiskCalculator<>(1.0));
    }

    @Override
    public PlayingDistribution<TAction, TObservation, TSearchNodeMetadata, TState> createDistribution(
        SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> node,
        double temperature,
        SplittableRandom random,
        double totalRiskAllowed)
    {
        int childCount = node.getChildNodeMap().size();
        double originMin = node.getChildNodeStream().mapToDouble(x -> x.getSearchNodeMetadata().getExpectedReward() + x.getSearchNodeMetadata().getGainedReward()).min().orElseThrow(() -> new IllegalStateException("Min does not exist"));
        double originMax = node.getChildNodeStream().mapToDouble(x -> x.getSearchNodeMetadata().getExpectedReward() + x.getSearchNodeMetadata().getGainedReward()).max().orElseThrow(() -> new IllegalStateException("Min does not exist"));

        List<TAction> actionList = new ArrayList<>(childCount);
        double[] rewardArray = new double[childCount];
        double[] riskArray = new double[childCount];

        int j = 0;
        for (Map.Entry<TAction, SearchNode<TAction, TObservation, TSearchNodeMetadata, TState>> entry : node.getChildNodeMap().entrySet()) {
            actionList.add(entry.getKey());
            var metadata = entry.getValue().getSearchNodeMetadata();
            rewardArray[j] = originMin == originMax ? 1.0 / childCount : (((metadata.getExpectedReward() + metadata.getGainedReward()) - originMin) / (originMax - originMin));
            riskArray[j] = 1.0d;
            j++;
        }
        if(applyTemperature) {
            RandomDistributionUtils.applyBoltzmannNoise(rewardArray, temperature);
        }
        int index = RandomDistributionUtils.getRandomIndexFromDistribution(rewardArray, random);
        TAction action = actionList.get(index);
        return new PlayingDistribution<>(action, index, rewardArray, riskArray, actionList, Map.of(action, subtreeRiskCalculatorSupplier));
    }
}
