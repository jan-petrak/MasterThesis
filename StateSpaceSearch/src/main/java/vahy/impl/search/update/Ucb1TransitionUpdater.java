package vahy.impl.search.update;

import vahy.api.model.Action;
import vahy.api.model.observation.Observation;
import vahy.api.model.State;
import vahy.api.model.reward.RewardAggregator;
import vahy.api.search.node.SearchNode;
import vahy.api.search.node.nodeMetadata.StateActionMetadata;
import vahy.api.search.update.NodeTransitionUpdater;
import vahy.impl.model.reward.DoubleScalarRewardDouble;
import vahy.impl.search.node.nodeMetadata.ucb1.Ucb1SearchNodeMetadata;
import vahy.impl.search.node.nodeMetadata.ucb1.Ucb1StateActionMetadata;

public class Ucb1TransitionUpdater<
    TAction extends Action,
    TObservation extends Observation,
    TState extends State<TAction, DoubleScalarRewardDouble, TObservation>>
    implements NodeTransitionUpdater<TAction, DoubleScalarRewardDouble, TObservation, Ucb1StateActionMetadata<DoubleScalarRewardDouble>, Ucb1SearchNodeMetadata<TAction, DoubleScalarRewardDouble>, TState> {

    private final double discountFactor;
    private final RewardAggregator<DoubleScalarRewardDouble> rewardAggregator;

    public Ucb1TransitionUpdater(double discountFactor, RewardAggregator<DoubleScalarRewardDouble> rewardAggregator) {
        this.discountFactor = discountFactor;
        this.rewardAggregator = rewardAggregator;
    }

    @Override
    public void applyUpdate(
        SearchNode<TAction, DoubleScalarRewardDouble, TObservation, Ucb1StateActionMetadata<DoubleScalarRewardDouble>, Ucb1SearchNodeMetadata<TAction, DoubleScalarRewardDouble>, TState> parent,
        SearchNode<TAction, DoubleScalarRewardDouble, TObservation, Ucb1StateActionMetadata<DoubleScalarRewardDouble>, Ucb1SearchNodeMetadata<TAction, DoubleScalarRewardDouble>, TState> child, TAction action) {
        Ucb1SearchNodeMetadata<TAction, DoubleScalarRewardDouble> parentSearchNodeMetadata = parent.getSearchNodeMetadata();
        Ucb1StateActionMetadata<DoubleScalarRewardDouble> stateActionMetadata = parentSearchNodeMetadata.getStateActionMetadataMap().get(action);

        stateActionMetadata.setEstimatedTotalReward(new DoubleScalarRewardDouble(rewardAggregator.aggregateDiscount(
            stateActionMetadata.getGainedReward(),
            child.getSearchNodeMetadata().getEstimatedTotalReward(),
            discountFactor).getValue()));
        double parentCumulativeEstimates = parentSearchNodeMetadata.getEstimatedTotalReward().getValue() * (parentSearchNodeMetadata.getVisitCounter() - 1);

        DoubleScalarRewardDouble newParentCumulativeEstimate = parent.isOpponentTurn() ?
            rewardAggregator.averageReward(parentSearchNodeMetadata
                .getStateActionMetadataMap()
                .values()
                .stream()
                .map(StateActionMetadata::getEstimatedTotalReward))
            : parentSearchNodeMetadata
                .getStateActionMetadataMap()
                .values()
                .stream()
                .map(StateActionMetadata::getEstimatedTotalReward)
                .max(Comparable::compareTo)
                .orElseThrow(() -> new IllegalStateException("Children should be always expanded when doing transition update"));

        double sum = parentCumulativeEstimates + newParentCumulativeEstimate.getValue();
        parentSearchNodeMetadata.setEstimatedTotalReward(new DoubleScalarRewardDouble(sum / parentSearchNodeMetadata.getVisitCounter()));
    }
}
