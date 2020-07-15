package vahy.impl.policy.alphazero;

import vahy.api.episode.EpisodeResults;
import vahy.api.learning.trainer.EpisodeDataMaker;
import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.impl.learning.model.MutableDoubleArray;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.model.reward.DoubleVectorRewardAggregator;
import vahy.utils.ImmutableTuple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AlphaZeroDataMaker_V1<TAction extends Enum<TAction> & Action, TState extends State<TAction, DoubleVector, TState>>
    implements EpisodeDataMaker<TAction, DoubleVector, TState> {

    private final double discountFactor;
    private final int playerPolicyId;
    private final int actionCount;

    public AlphaZeroDataMaker_V1(int playerPolicyId, int actionCount, double discountFactor) {
        this.discountFactor = discountFactor;
        this.playerPolicyId = playerPolicyId;
        this.actionCount = actionCount;
    }

    @Override
    public List<ImmutableTuple<DoubleVector, MutableDoubleArray>> createEpisodeDataSamples(EpisodeResults<TAction, DoubleVector, TState> episodeResults) {
        var episodeHistory = episodeResults.getEpisodeHistory();
        var entityInGameCount = episodeHistory.get(0).getFromState().getTotalEntityCount();
        var translationMap = episodeResults.getPolicyIdTranslationMap();
        var inGameEntityId = translationMap.getInGameEntityId(playerPolicyId);
        var aggregatedTotalPayoff = new double[entityInGameCount];
        var iterator = episodeHistory.listIterator(episodeResults.getTotalStepCount());
        var mutableDataSampleList = new ArrayList<ImmutableTuple<DoubleVector, MutableDoubleArray>>(episodeResults.getPlayerStepCountList().get(inGameEntityId));
        while(iterator.hasPrevious()) {
            var previous = iterator.previous();

            if (previous.getFromState().isInGame(inGameEntityId)) {
                aggregatedTotalPayoff = DoubleVectorRewardAggregator.aggregateDiscount(previous.getReward(), aggregatedTotalPayoff, discountFactor);

                var doubleArray = new double[entityInGameCount + actionCount];

                if (previous.getFromState().isEnvironmentEntityOnTurn()) {
                    var action = previous.getAction();
                    var actionId = action.ordinal();
                    doubleArray[entityInGameCount + actionId] = 1.0;
                    System.arraycopy(aggregatedTotalPayoff, 0, doubleArray, 0, aggregatedTotalPayoff.length);
                    mutableDataSampleList.add(new ImmutableTuple<>(previous.getFromState().getInGameEntityObservation(inGameEntityId), new MutableDoubleArray(doubleArray, false)));
                } else {
                    if (previous.getPolicyIdOnTurn() == playerPolicyId) {
                        var policyArray = previous.getPolicyStepRecord().getPolicyProbabilities();
                        System.arraycopy(policyArray, 0, doubleArray, entityInGameCount, policyArray.length);
                        System.arraycopy(aggregatedTotalPayoff, 0, doubleArray, 0, aggregatedTotalPayoff.length);
                        mutableDataSampleList.add(new ImmutableTuple<>(previous.getFromState().getInGameEntityObservation(inGameEntityId), new MutableDoubleArray(doubleArray, false)));
                    }
                }
            }
        }
        Collections.reverse(mutableDataSampleList);
        return mutableDataSampleList;
    }
}
