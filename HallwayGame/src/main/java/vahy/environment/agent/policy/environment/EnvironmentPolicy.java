package vahy.environment.agent.policy.environment;

import vahy.api.model.State;
import vahy.api.policy.Policy;
import vahy.environment.ActionType;
import vahy.environment.state.ImmutableStateImpl;
import vahy.impl.model.observation.DoubleVectorialObservation;
import vahy.impl.model.reward.DoubleScalarRewardDouble;
import vahy.utils.ImmutableTuple;
import vahy.utils.RandomDistributionUtils;

import java.util.List;
import java.util.SplittableRandom;

public class EnvironmentPolicy implements Policy<ActionType, DoubleScalarRewardDouble, DoubleVectorialObservation> {

    private final SplittableRandom random;

    public EnvironmentPolicy(SplittableRandom random) {
        this.random = random;
    }

    @Override
    public ActionType getDiscreteAction(State<ActionType, DoubleScalarRewardDouble, DoubleVectorialObservation> gameState) {
        ImmutableStateImpl castedGameState = (ImmutableStateImpl) gameState;
        ImmutableTuple<List<ActionType>, List<Double>> actions = castedGameState.environmentActionsWithProbabilities();
        return actions.getFirst().get(RandomDistributionUtils.getRandomIndexFromDistribution(actions.getSecond(), random));
    }

    @Override
    public double[] getActionProbabilityDistribution(State<ActionType, DoubleScalarRewardDouble, DoubleVectorialObservation> gameState) {
        ImmutableStateImpl castedGameState = (ImmutableStateImpl) gameState;
        ImmutableTuple<List<ActionType>, List<Double>> actions = castedGameState.environmentActionsWithProbabilities();
        return actions.getSecond().stream().mapToDouble(value -> value).toArray();
    }

    @Override
    public void updateStateOnOpponentActions(List<ActionType> opponentActionList) {
        // this is it
    }
}
