package vahy.paperGenerics.policy.flowOptimizer;

import vahy.api.model.Action;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.paperGenerics.metadata.PaperMetadata;
import vahy.paperGenerics.PaperState;
import vahy.paperGenerics.policy.linearProgram.NoiseStrategy;
import vahy.paperGenerics.policy.linearProgram.OptimalFlowHardConstraintCalculator;
import vahy.paperGenerics.policy.linearProgram.OptimalFlowSoftConstraint;
import vahy.utils.ImmutableTuple;

import java.util.SplittableRandom;

public class HardSoftFlowOptimizer<
    TAction extends Enum<TAction> & Action,
    TPlayerObservation extends Observation,
    TOpponentObservation extends Observation,
    TSearchNodeMetadata extends PaperMetadata<TAction>,
    TState extends PaperState<TAction, TPlayerObservation, TOpponentObservation, TState>>
    extends AbstractFlowOptimizer<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata , TState> {

    public HardSoftFlowOptimizer(Class<TAction> actionClass, SplittableRandom random, NoiseStrategy noiseStrategy) {
        super(actionClass, random, noiseStrategy);
    }

    @Override
    public ImmutableTuple<Double, Boolean> optimizeFlow(SearchNode<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> node, double totalRiskAllowed) {
        var optimalFlowCalculator = new OptimalFlowHardConstraintCalculator<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState>(actionClass, totalRiskAllowed, random, noiseStrategy);
        boolean optimalSolutionExists = optimalFlowCalculator.optimizeFlow(node);
        if(!optimalSolutionExists) {
            var optimalSoftFlowCalculator = new OptimalFlowSoftConstraint<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState>(actionClass, totalRiskAllowed, random, noiseStrategy);
            optimalSolutionExists = optimalSoftFlowCalculator.optimizeFlow(node);
        }
        return new ImmutableTuple<>(totalRiskAllowed, optimalSolutionExists);
    }
}
