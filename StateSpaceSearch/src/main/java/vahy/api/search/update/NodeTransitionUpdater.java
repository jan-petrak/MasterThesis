package vahy.api.search.update;

import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.api.search.node.SearchNodeMetadata;

public interface NodeTransitionUpdater<TAction extends Enum<TAction> & Action, TObservation extends Observation, TSearchNodeMetadata extends SearchNodeMetadata, TState extends State<TAction, TObservation, TState>> {

    void applyUpdate(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> evaluatedNode,
                     SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> parent,
                     SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> child);
}
