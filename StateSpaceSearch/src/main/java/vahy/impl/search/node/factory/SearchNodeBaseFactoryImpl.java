package vahy.impl.search.node.factory;

import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.StateRewardReturn;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.api.search.node.SearchNodeMetadata;
import vahy.api.search.node.factory.SearchNodeFactory;
import vahy.api.search.node.factory.SearchNodeMetadataFactory;
import vahy.impl.search.node.SearchNodeImpl;

import java.util.LinkedHashMap;

public class SearchNodeBaseFactoryImpl<
    TAction extends Enum<TAction> & Action,
    TPlayerObservation extends Observation,
    TOpponentObservation extends Observation,
    TSearchNodeMetadata extends SearchNodeMetadata,
    TState extends State<TAction, TPlayerObservation, TOpponentObservation, TState>>
    implements SearchNodeFactory<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> {

    private final SearchNodeMetadataFactory<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> searchNodeMetadataFactory;

    public SearchNodeBaseFactoryImpl(SearchNodeMetadataFactory<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> searchNodeMetadataFactory) {
        this.searchNodeMetadataFactory = searchNodeMetadataFactory;
    }

    @Override
    public SearchNode<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> createNode(
        StateRewardReturn<TAction, TPlayerObservation, TOpponentObservation, TState> stateRewardReturn,
        SearchNode<TAction, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> parent,
        TAction action) {
        return new SearchNodeImpl<>(
            stateRewardReturn.getState(),
            searchNodeMetadataFactory.createSearchNodeMetadata(parent, stateRewardReturn, action),
            new LinkedHashMap<>(),
            parent,
            action);
    }
}
