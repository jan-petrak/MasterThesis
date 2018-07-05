package vahy.search;

import org.testng.annotations.Test;
import vahy.api.model.State;
import vahy.environment.ActionType;
import vahy.impl.model.observation.DoubleVectorialObservation;
import vahy.impl.model.reward.DoubleScalarRewardDouble;
import vahy.impl.search.node.nodeMetadata.AbstractStateActionMetadata;

import java.util.Map;

public class MaximizingRewardGivenProbabilitiesTest {

    public class MaximizingRewardGivenProbabilitiesUnderTest extends MaximizingRewardGivenProbabilities {

        public DoubleScalarRewardDouble resolveRewardHandle(State<ActionType, DoubleScalarRewardDouble, DoubleVectorialObservation> state,
                                                            Map<ActionType, AbstractStateActionMetadata<DoubleScalarRewardDouble>> stateActionMap) {
            return resolveReward(state, stateActionMap);
        }
    }

    @Test
    public void DummyTest() {

    }

}
