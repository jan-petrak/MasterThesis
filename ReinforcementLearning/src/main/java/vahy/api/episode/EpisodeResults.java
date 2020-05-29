package vahy.api.episode;

import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.observation.Observation;
import vahy.api.policy.PolicyRecord;

import java.time.Duration;
import java.util.List;

public interface EpisodeResults<
    TAction extends Enum<TAction> & Action,
    TObservation extends Observation,
    TState extends State<TAction, TObservation, TState>,
    TPolicyRecord extends PolicyRecord> {

    List<EpisodeStepRecord<TAction, TObservation, TState, TPolicyRecord>> getEpisodeHistory();

    int getPolicyCount();

    int getTotalStepCount();

    PolicyIdTranslationMap getPolicyIdTranslationMap();

    List<Integer> getPlayerStepCountList();

    List<Double> getTotalPayoff();

    Duration getDuration();

    TState getFinalState();

    String episodeMetadataToFile();

}
