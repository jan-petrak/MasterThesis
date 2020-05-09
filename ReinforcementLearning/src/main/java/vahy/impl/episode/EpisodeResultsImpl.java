package vahy.impl.episode;

import vahy.api.episode.EpisodeResults;
import vahy.api.episode.EpisodeStepRecord;
import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.observation.Observation;
import vahy.api.policy.PolicyRecord;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class EpisodeResultsImpl<
    TAction extends Enum<TAction> & Action,
    TObservation extends Observation,
    TState extends State<TAction, TObservation, TState>,
    TPolicyRecord extends PolicyRecord>
    implements EpisodeResults<TAction, TObservation, TState, TPolicyRecord> {

    private final List<EpisodeStepRecord<TAction, TObservation, TState, TPolicyRecord>> episodeHistory;
    private final int policyCount;
    private final List<Integer> playerStepCountList;
    private final int totalStepCount;
    private final List<List<Double>> totalPayoff;
    private final Duration duration;

    public EpisodeResultsImpl(List<EpisodeStepRecord<TAction, TObservation, TState, TPolicyRecord>> episodeHistory,
                              int policyCount, List<Integer> playerStepCountList,
                              int totalStepCount,
                              List<List<Double>> totalPayoff,
                              Duration duration) {
        this.episodeHistory = episodeHistory;
        this.policyCount = policyCount;
        this.playerStepCountList = playerStepCountList;
        this.totalStepCount = totalStepCount;
        this.totalPayoff = totalPayoff;
        this.duration = duration;
    }

    @Override
    public List<EpisodeStepRecord<TAction, TObservation, TState, TPolicyRecord>> getEpisodeHistory() {
        return episodeHistory;
    }

    @Override
    public int getPolicyCount() {
        return policyCount;
    }

    @Override
    public int getTotalStepCount() {
        return totalStepCount;
    }

    @Override
    public List<Integer> getPlayerStepCountList() {
        return playerStepCountList;
    }

    @Override
    public List<List<Double>> getTotalPayoff() {
        return totalPayoff;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public TState getFinalState() {
        return episodeHistory.get(episodeHistory.size() - 1).getToState();
    }

    protected void appendLine(StringBuilder sb, String propertyName, String propertyValue) {
        sb.append(propertyName);
        sb.append(", ");
        sb.append(propertyValue);
        sb.append(System.lineSeparator());
    }

    @Override
    public String episodeMetadataToFile() {
        var sb = new StringBuilder();
        appendLine(sb, "Total step count", String.valueOf(getTotalStepCount()));
        appendLine(sb, "Player step count", playerStepCountList.stream().map(x -> x.toString()).collect(Collectors.joining(", ")));
        appendLine(sb, "Duration [ms]", String.valueOf(getDuration().toMillis()));
        appendLine(sb, "Total Payoff", String.valueOf(getTotalPayoff()));
        return sb.toString();
    }
}
