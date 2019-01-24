package vahy.paperGenerics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vahy.api.model.Action;
import vahy.api.model.StateRewardReturn;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.api.search.nodeEvaluator.NodeEvaluator;
import vahy.api.search.nodeSelector.NodeSelector;
import vahy.api.search.update.TreeUpdater;
import vahy.impl.model.reward.DoubleReward;
import vahy.impl.search.tree.SearchTreeImpl;
import vahy.utils.ImmutableTuple;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RiskAverseSearchTree<
    TAction extends Action,
    TReward extends DoubleReward,
    TPlayerObservation extends Observation,
    TOpponentObservation extends Observation,
    TSearchNodeMetadata extends PaperMetadata<TAction, TReward>,
    TState extends PaperState<TAction, TReward, TPlayerObservation, TOpponentObservation, TState>>
    extends SearchTreeImpl<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> {

    private static final Logger logger = LoggerFactory.getLogger(RiskAverseSearchTree.class);

    public static final double NUMERICAL_RISK_DIFF_TOLERANCE = Math.pow(10, -10);
    public static final double NUMERICAL_PROBABILITY_TOLERANCE = Math.pow(10, -10);
    public static final double NUMERICAL_ACTION_RISK_TOLERANCE = Math.pow(10, -10);

    private boolean isFlowOptimized = false;
    private double totalRiskAllowed;

    public RiskAverseSearchTree(SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> root,
                                NodeSelector<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> nodeSelector,
                                TreeUpdater<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> treeUpdater,
                                NodeEvaluator<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> nodeEvaluator,
                                double totalRiskAllowed) {
        super(root, nodeSelector, treeUpdater, nodeEvaluator);
        this.totalRiskAllowed = totalRiskAllowed;
    }

    public double getTotalRiskAllowed() {
        return totalRiskAllowed;
    }

    public boolean isFlowOptimized() {
        return isFlowOptimized;
    }

    public void setFlowOptimized(boolean flowOptimized) {
        isFlowOptimized = flowOptimized;
    }

    public List<TAction> getAllowedActionsForExploration() {
        TAction[] actions = getRoot().getAllPossibleActions();
        var allowedActions = new LinkedList<TAction>();
        for (TAction action : actions) {
            if (calculateRiskOfOpponentNodes(getRoot().getChildNodeMap().get(action)) <= totalRiskAllowed) {
                allowedActions.add(action);
            }
        }
        return allowedActions;
    }

    private double calculateRiskOfOpponentNodes(SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> node) {
        if(node.isFinalNode()) {
            return node.getWrappedState().isRiskHit() ?  1.0 : 0.0;
        }
        if(node.isPlayerTurn()) {
            return 0.0;
        }
        if(node.isLeaf()) {
            throw new IllegalStateException("Risk can't be calculated from leaf nodes which are not player turns. Tree should be expanded up to player or final nodes");
        }
        return node
            .getChildNodeStream()
            .map(x -> new ImmutableTuple<>(x, x.getSearchNodeMetadata().getPriorProbability()))
            .mapToDouble(x -> calculateRiskOfOpponentNodes(x.getFirst()) * x.getSecond())
            .sum();
    }

    @Override
    public StateRewardReturn<TAction, TReward, TPlayerObservation, TOpponentObservation, TState> applyAction(TAction action) {
        checkApplicableAction(action);
        // TODO make general in applicable action
        if(!getRoot().getChildNodeMap().containsKey(action)) {
            throw new IllegalStateException("Action [" + action + "] is invalid and cannot be applied to current policy state");
        }
//        logger.info("Old Global risk: [{}] and applying action: [{}]", totalRiskAllowed, action);
        isFlowOptimized = false;
        if(action.isPlayerAction()) {
            calculateNumericallyStableNewRiskThreshold(action);
        }
        var stateReward = innerApplyAction(action);
//        logger.info("New Global risk: [{}]", totalRiskAllowed);
        return stateReward;
    }

    @Override
    public boolean updateTree() {
        isFlowOptimized = false;
        return super.updateTree();
    }

    private void calculateNumericallyStableNewRiskThreshold(TAction appliedAction) {

        double riskOfOtherActions = calculateNumericallyStableRiskOfAnotherActions(appliedAction);
        // vypocitat risk z druhycho pootomku (az muj node) a pak vynasobit pravdepodobnosti (jsou dve  - mono vice) ze do nich pujdu


        // pokud exploruji, tak brat pravdepodobnost z te distribuce, kterou epxloruji
        double riskDiff = calculateNumericallyStableRiskDiff(riskOfOtherActions);
        double actionProbability = calculateNumericallyStableActionProbability(getRoot()
            .getChildNodeMap()
            .get(appliedAction)
            .getSearchNodeMetadata()
            .getNodeProbabilityFlow()
            .getSolution());
        totalRiskAllowed = calculateNewRiskValue(riskDiff, actionProbability, riskOfOtherActions, appliedAction);
    }

    private double calculateNumericallyStableRiskOfAnotherActions(TAction appliedAction) {
        double riskOfOtherActions = 0.0;
        for (Map.Entry<TAction, SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState>> entry : getRoot().getChildNodeMap().entrySet()) {
            if(entry.getKey() != appliedAction) {
                riskOfOtherActions += calculateRiskContributionInSubTree(entry.getValue());
            }
        }

        if(Math.abs(riskOfOtherActions) < NUMERICAL_ACTION_RISK_TOLERANCE) {
            if (riskOfOtherActions != 0.0) {
                logger.trace("Rounding risk of other actions to 0. This is done because linear optimization is not numerically stable");
            }
            return 0.0;
        } else if(Math.abs(riskOfOtherActions - 1.0) < NUMERICAL_ACTION_RISK_TOLERANCE) {
            if(riskOfOtherActions != 1.0) {
                logger.trace("Rounding risk of other actions to 1. This is done because linear optimization is not numerically stable");
            }
            return 1.0;
        } else if(riskOfOtherActions < 0.0) {
            throw new IllegalStateException("Risk of other actions cannot be lower than 0. This would cause program failure later in simulation");
        } else if(riskOfOtherActions > 1.0) {
            throw new IllegalStateException("Risk of other actions cannot be higher than 1. This would cause program failure later in simulation");
        }
        return riskOfOtherActions;

    }

    private double calculateNewRiskValue(double riskDiff, double actionProbability, double riskOfOtherActions, TAction appliedAction) {
        if(actionProbability == 0.0) {
            logger.trace("Taken action with zero probability according to linear optimization. Setting risk to 1.0, since such action is probably taken due to exploration.");
            return 1.0;
        } else {
            double newRisk = riskDiff / actionProbability;
            if((newRisk < -NUMERICAL_RISK_DIFF_TOLERANCE) || (newRisk - 1.0 > NUMERICAL_RISK_DIFF_TOLERANCE)) {
                throw new IllegalStateException(
                    "Risk out of bounds. " +
                        "Old risk [" + totalRiskAllowed + "]. " +
                        "Risk diff numerically stabilised: [" +  riskDiff + "] " +
                        "New risk calculated: [" + newRisk + "], " +
                        "Numerically stable risk of other actions: [" + riskOfOtherActions + "], " +
                        "Dividing probability: [" + getRoot().getChildNodeMap().get(appliedAction).getSearchNodeMetadata().getNodeProbabilityFlow().getSolution() + "], " +
                        "Numerically stabilised dividing probability: [" + actionProbability + "]");
            }
            if(newRisk > 1.0) {
                logger.trace("Rounding new risk to 1.0.");
                return 1.0;
            }
            if(newRisk < 0.0) {
                logger.trace("Rounding newRisk to 0.0");
                return 0.0;
            }
            return newRisk;
        }
    }

    private double calculateNumericallyStableActionProbability(double calculatedProbability) {
        if(Math.abs(calculatedProbability) < NUMERICAL_PROBABILITY_TOLERANCE) {
            if (calculatedProbability != 0.0) {
                logger.trace("Rounding action probability to 0. This is done because linear optimization is not numerically stable");
            }
            return 0.0;
        } else if(Math.abs(calculatedProbability - 1.0) < NUMERICAL_PROBABILITY_TOLERANCE) {
            if(calculatedProbability != 1.0) {
                logger.trace("Rounding action probability to 1. This is done because linear optimization is not numerically stable");
            }
            return 1.0;
        } else if(calculatedProbability < 0.0) {
            throw new IllegalStateException("Probability cannot be lower than 0. This would cause program failure later in simulation");
        } else if(calculatedProbability > 1.0) {
            throw new IllegalStateException("Probability cannot be higher than 1. This would cause program failure later in simulation");
        }
        return calculatedProbability;
    }

    private double calculateNumericallyStableRiskDiff(double totalRiskOfOtherActions) {
        double riskDiff = (totalRiskAllowed - totalRiskOfOtherActions);
        if(Math.abs(riskDiff) < NUMERICAL_RISK_DIFF_TOLERANCE) {
            if(riskDiff != 0) {
                logger.trace("Rounding risk difference to 0. This si done because linear optimization is not numerically stable");
            }
            riskDiff = 0.0;
        } else if(riskDiff < 0.0) {
            throw new IllegalStateException("Risk difference is out of bounds. New risk difference [" + riskDiff + "]. Risk exceeds tolerated bound: [" + -NUMERICAL_RISK_DIFF_TOLERANCE + "]");
        }
        return riskDiff;
    }

    private double calculateRiskContributionInSubTree(SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> subTreeRoot) {
        double risk = 0;

        LinkedList<SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState>> queue = new LinkedList<>();
        queue.addFirst(subTreeRoot);

        while(!queue.isEmpty()) {
            SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState> node = queue.poll();
            if(node.isLeaf()) {
                if(node.getWrappedState().isRiskHit()) {
                    risk += node.getSearchNodeMetadata().getNodeProbabilityFlow().getSolution();
                }
                // TODO: go through all lists and add risk according to predictions
            } else {
                for (Map.Entry<TAction, SearchNode<TAction, TReward, TPlayerObservation, TOpponentObservation, TSearchNodeMetadata, TState>> entry : node.getChildNodeMap().entrySet()) {
                    queue.addLast(entry.getValue());
                }
            }
        }
        return risk;
    }

}
