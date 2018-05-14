package vahy.impl.search.nodeSelector.treeTraversing;

import vahy.api.model.Action;
import vahy.api.model.Observation;
import vahy.api.model.State;
import vahy.api.search.node.SearchNode;
import vahy.api.search.nodeSelector.NodeSelector;
import vahy.impl.model.reward.DoubleScalarReward;
import vahy.impl.search.node.nodeMetadata.ucb1.Ucb1SearchNodeMetadata;
import vahy.impl.search.node.nodeMetadata.ucb1.Ucb1StateActionMetadata;
import vahy.utils.StreamUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.SplittableRandom;

public class Ucb1NodeSelector<
    TAction extends Action,
    TReward extends DoubleScalarReward,
    TObservation extends Observation,
    TState extends State<TAction, TReward, TObservation>>
    implements NodeSelector<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> {

    protected SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> root;
    protected final SplittableRandom random;
    protected final double weight;

    public Ucb1NodeSelector(SplittableRandom random, double weight) {
        this.random = random;
        this.weight = weight;
    }

    @Override
    public void addNode(SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> node) {

    }

    @Override
    public void addNodes(Collection<SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState>> searchNodes) {

    }

    @Override
    public void setNewRoot(SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> root) {
        this.root = root;
    }

    @Override
    public SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> selectNextNode() {
        checkRoot();
        SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> node = root;
        while(!node.isLeaf()) {
            Ucb1SearchNodeMetadata<TAction, TReward> nodeMetadata = node.getSearchNodeMetadata();
            SearchNode<TAction, TReward, TObservation, Ucb1StateActionMetadata<TReward>, Ucb1SearchNodeMetadata<TAction, TReward>, TState> finalNode = node;
            TAction bestAction = node.getSearchNodeMetadata()
                .getStateActionMetadataMap()
                .entrySet()
                .stream()
                .collect(StreamUtils.toRandomizedMaxCollector(
                    Comparator.comparing(
                        o -> calculateUCBValue(
                            finalNode.getChildNodeMap().get(o.getKey()).getSearchNodeMetadata().getEstimatedTotalReward().getValue(),
                            nodeMetadata.getVisitCounter(),
                            o.getValue().getVisitCounter())),
                    random))
                .getKey();
            nodeMetadata.increaseVisitCounter();
            nodeMetadata.getStateActionMetadataMap().get(bestAction).increaseVisitCounter();
            node = node.getChildNodeMap().get(bestAction);
        }
        return node;
    }

    protected void checkRoot() {
        if(root == null) {
            throw new IllegalStateException("Root was not initialized");
        }
    }

    protected double calculateUCBValue(double estimatedValue, int parentVisitCount, int actionVisitCount) {
        return estimatedValue + weight * Math.sqrt(Math.log(parentVisitCount) / actionVisitCount);
    }
}
