package vahy.api.learning.model;

import vahy.api.model.Action;
import vahy.api.model.reward.DoubleVectorialReward;
import vahy.impl.model.observation.DoubleVectorialObservation;

public abstract class AbstractTrainableStateEvaluatingPolicySupplier<TAction extends Action, TReward extends DoubleVectorialReward, TObservation extends DoubleVectorialObservation> implements TrainablePolicySupplier<TAction, TReward, TObservation> {

    private final TrainableRewardApproximator<TReward, TObservation> trainableRewardApproximator;

    public AbstractTrainableStateEvaluatingPolicySupplier(TrainableRewardApproximator<TReward, TObservation> trainableRewardApproximator) {
        this.trainableRewardApproximator = trainableRewardApproximator;
    }

    public TrainableRewardApproximator<TReward, TObservation> getTrainableRewardApproximator() {
        return trainableRewardApproximator;
    }
}
