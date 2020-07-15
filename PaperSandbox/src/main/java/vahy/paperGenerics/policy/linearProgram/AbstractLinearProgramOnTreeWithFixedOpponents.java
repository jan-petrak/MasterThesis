package vahy.paperGenerics.policy.linearProgram;

import com.quantego.clp.CLP;
import com.quantego.clp.CLPExpression;
import com.quantego.clp.CLPVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vahy.api.model.Action;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.paperGenerics.PaperState;
import vahy.paperGenerics.metadata.PaperMetadata;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SplittableRandom;

public abstract class AbstractLinearProgramOnTreeWithFixedOpponents<
    TAction extends Enum<TAction> & Action,
    TObservation extends Observation,
    TSearchNodeMetadata extends PaperMetadata<TAction>,
    TState extends PaperState<TAction, TObservation, TState>>  {

    private static final Logger logger = LoggerFactory.getLogger(AbstractLinearProgramOnTreeWithFixedOpponents.class.getName());
    public static final boolean TRACE_ENABLED = logger.isTraceEnabled();
    public static final boolean DEBUG_ENABLED = logger.isDebugEnabled();

    private static final double FLOW_TOLERANCE = 1.0 - Math.pow(10, -10);

    private static final double LOWER_BOUND = 0.0;
    private static final double UPPER_BOUND = 1.0;
    private static final double CHILD_VARIABLE_COEFFICIENT = 1.0;
    private static final double PARENT_VARIABLE_COEFFICIENT = -1.0;
    private static final double RISK_COEFFICIENT = 1.0;


    protected class FlowWithCoefficient {
        protected final CLPVariable closestParentFlow;
        protected double coefficient;

        protected FlowWithCoefficient(CLPVariable closestParentFlow) {
            this.closestParentFlow = closestParentFlow;
        }

        public CLPVariable getClosestParentFlow() {
            return closestParentFlow;
        }

        public double getCoefficient() {
            return coefficient;
        }

        public void setCoefficient(double coefficient) {
            this.coefficient = coefficient;
        }
    }

    protected class InnerElement {

        protected final SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> node;
        protected final double modifier;
        protected FlowWithCoefficient flowWithCoefficient;

        protected InnerElement(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> node, double modifier, FlowWithCoefficient flowWithCoefficient) {
            this.node = node;
            this.modifier = modifier;
            this.flowWithCoefficient = flowWithCoefficient;
        }

        public SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> getNode() {
            return node;
        }

        public double getModifier() {
            return modifier;
        }

        public FlowWithCoefficient getFlowWithCoefficient() {
            return flowWithCoefficient;
        }
    }

    private final boolean maximize;
    private final NoiseStrategy strategy;
    private final SplittableRandom random;
    private final double noiseUpperBound;
    private final double noiseLowerBound;
    protected CLP model;

    private LinkedList<InnerElement> masterQueue;
    private List<FlowWithCoefficient> flowList;

    protected AbstractLinearProgramOnTreeWithFixedOpponents(boolean maximize, SplittableRandom random, NoiseStrategy strategy) {
        this.model = new CLP();
//        this.model.algorithm(CLP.ALGORITHM.PRIMAL);
        this.masterQueue = new LinkedList<>();
        this.flowList = new ArrayList<>(); // TODO: redo to LinkedList?
        this.maximize = maximize;
        this.random = random;
        this.strategy = strategy;
        this.noiseLowerBound = strategy.getLowerBound();
        this.noiseUpperBound = strategy.getUpperBound();
    }

    protected abstract void setLeafObjective(InnerElement node);
//    protected abstract void setLeafObjectiveWithFlow(List<InnerElement> nodeList, CLPVariable parentFlow);

    protected abstract void finalizeHardConstraints();

    public double getObjectiveValue() {
        return model.getObjectiveValue();
    }

    public boolean optimizeFlow(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> root) {
//        if(root.isPlayerTurn()) {
//            return optimizePlayerNode(root);
//        } else {
//            return optimizeOpponentNode(root);
//        }
        return optimizePlayerNode(root);
    }

    public void finalizeFlowCoefficients() {
        for (FlowWithCoefficient flowWithCoefficient : flowList) {
            model.setObjectiveCoefficient(flowWithCoefficient.closestParentFlow, addNoiseToLeaf(flowWithCoefficient.coefficient));
        }
    }

    private boolean optimizeOpponentNode(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> root) {
        throw new UnsupportedOperationException("Linear optimization of opponent node is not supported for now");
    }

    private boolean optimizePlayerNode(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> root) {
        long startBuildingLinearProgram = System.currentTimeMillis();
        initializeQueues(root);
        while(!masterQueue.isEmpty()) {
            var innerElement = masterQueue.pop();
            if(!innerElement.node.isLeaf()) {
                if(innerElement.node.isPlayerTurn()) {
                    CLPExpression parentFlowDistribution = model.createExpression();
                    for (var node : innerElement.node.getChildNodeMap().values()) {
                        CLPVariable childFlow = model.addVariable().lb(LOWER_BOUND).ub(UPPER_BOUND);
                        node.getSearchNodeMetadata().setNodeProbabilityFlow(childFlow);
                        parentFlowDistribution.add(CHILD_VARIABLE_COEFFICIENT, childFlow);
                        var flowWithCoefficient = new FlowWithCoefficient(childFlow);
                        masterQueue.add(new InnerElement(node, 1.0, flowWithCoefficient));
                        flowList.add(flowWithCoefficient);
                    }
                    parentFlowDistribution.add(-innerElement.modifier, innerElement.flowWithCoefficient.closestParentFlow);
                    parentFlowDistribution.eq(0.0);
                } else {
                    for (var node : innerElement.node.getChildNodeMap().values()) {
                        masterQueue.add(new InnerElement(node, innerElement.modifier * node.getSearchNodeMetadata().getPriorProbability(), innerElement.flowWithCoefficient));
                    }
                }
            } else {
                setLeafObjective(innerElement);
            }
        }

        finalizeFlowCoefficients();
        finalizeHardConstraints();
        if(DEBUG_ENABLED) {
            logger.debug("Building linear program took [{}]ms", System.currentTimeMillis() - startBuildingLinearProgram);
        }
        long startOptimization = System.currentTimeMillis();
        CLP.STATUS status = maximize ? model.maximize() : model.minimize();
        if(status != CLP.STATUS.OPTIMAL) {
            logger.debug("Optimal solution was not found.");
            return false;
        }

        var queue2 = new LinkedList<SearchNode<TAction, TObservation, TSearchNodeMetadata, TState>>();
        queue2.addFirst(root);

        while(!queue2.isEmpty()) {
            var node = queue2.pop();

            if(node.getSearchNodeMetadata().getNodeProbabilityFlow() == null) {
                node.getSearchNodeMetadata().setFlow(node.getParent().getSearchNodeMetadata().getFlow() * node.getSearchNodeMetadata().getPriorProbability());
            }
            queue2.addAll(node.getChildNodeMap().values());
        }

        if(root.getSearchNodeMetadata().getFlow() < FLOW_TOLERANCE) {
            throw new IllegalStateException("Flow is not equal to 1");
        }

        if(!root.getChildNodeMap().isEmpty()) {
            var sum = 0.0;
            for (var entry : root.getChildNodeMap().values()) {
                sum += entry.getSearchNodeMetadata().getFlow();
            }
            if(sum < FLOW_TOLERANCE) {
                throw new IllegalStateException("Flow is not equal to 1");
            }
        }
        if(DEBUG_ENABLED) {
            logger.debug("Optimizing linear program took [{}] ms", System.currentTimeMillis() - startOptimization);
        }
        return true;
    }

//    private List<InnerElement> getAllDirectLeafList(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> subRoot) {
//        LinkedList<InnerElement> opponentNodeQueue = new LinkedList<>();
//        List<InnerElement> leafList = new ArrayList<>();
//        opponentNodeQueue.addFirst(new InnerElement(subRoot, 1.0));
//        while(!opponentNodeQueue.isEmpty()) {
//            var innerElement = opponentNodeQueue.pop();
//            if(!innerElement.node.isLeaf()) {
//                for (var node : innerElement.node.getChildNodeMap().values()) {
//
//                }
//            } else {
//                leafList.add(new InnerElement(innerElement.node, innerElement.modifier));
//            }
//        }
//        return leafList;
//    }
//
//    private void resolvePlayerNode(CLPExpression parentFlowDistribution, SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> node) {
//        CLPVariable childFlow = model.addVariable().lb(LOWER_BOUND).ub(UPPER_BOUND);
//        node.getSearchNodeMetadata().setNodeProbabilityFlow(childFlow);
//        parentFlowDistribution.add(CHILD_VARIABLE_COEFFICIENT, childFlow);
//        masterQueue.add(new InnerElement(node, 1.0, childFlow));
//    }

    protected final double getNodeValue(TSearchNodeMetadata metadata, int inGameEntityId) {
        double cumulativeReward = metadata.getCumulativeReward()[inGameEntityId];
        double expectedReward = metadata.getExpectedReward()[inGameEntityId];
        return addNoiseToLeaf(cumulativeReward + expectedReward);
    }

    protected final double addNoiseToLeaf(double leafCoefficient) {
        if (strategy != NoiseStrategy.NONE) {
            var value = random.nextDouble(noiseLowerBound, noiseUpperBound);
            leafCoefficient = leafCoefficient + (random.nextBoolean() ? value : -value);
        }
        return leafCoefficient;
    }

    private void initializeQueues(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> root) {
        root.getSearchNodeMetadata().setNodeProbabilityFlow(model.addVariable().lb(UPPER_BOUND).ub(UPPER_BOUND));
        var flow = new FlowWithCoefficient(root.getSearchNodeMetadata().getNodeProbabilityFlow());
        masterQueue.addFirst(new InnerElement(root, 1.0, flow));
        if(!root.isPlayerTurn()) {
            flowList.add(flow);
        }
    }

//    private void resolveNonLeafSubChild(SearchNode<TAction, TObservation, TSearchNodeMetadata, TState> opponentNode, CLPVariable childFlow) {
//        var leafSubParentNodeList = new LinkedList<SearchNode<TAction, TObservation, TSearchNodeMetadata, TState>>();
//        for (var playerNode : opponentNode.getChildNodeMap().values()) {
//            var priorProbability = playerNode.getSearchNodeMetadata().getPriorProbability();
//            if(playerNode.isLeaf()) {
//                leafSubParentNodeList.add(playerNode);
//            } else {
//                masterQueue.addLast(new InnerElement(playerNode, priorProbability));
//            }
//        }
//        setLeafObjectiveWithFlow(leafSubParentNodeList, opponentNode.getSearchNodeMetadata().getNodeProbabilityFlow());
//    }
}
