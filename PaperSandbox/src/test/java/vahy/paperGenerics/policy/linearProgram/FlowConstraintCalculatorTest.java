package vahy.paperGenerics.policy.linearProgram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import vahy.api.search.node.SearchNode;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.predictor.EmptyPredictor;
import vahy.impl.search.node.SearchNodeImpl;
import vahy.impl.search.node.factory.SearchNodeBaseFactoryImpl;
import vahy.impl.search.tree.SearchTreeImpl;
import vahy.impl.testdomain.emptySpace.EmptySpaceAction;
import vahy.paperGenerics.PaperTreeUpdater;
import vahy.paperGenerics.evaluator.PaperNodeEvaluator;
import vahy.paperGenerics.metadata.PaperMetadata;
import vahy.paperGenerics.metadata.PaperMetadataFactory;
import vahy.paperGenerics.policy.linearProgram.deprecated.OptimalFlowHardConstraintCalculatorDeprecated;
import vahy.paperGenerics.policy.linearProgram.deprecated.OptimalFlowSoftConstraintDeprecateed;
import vahy.paperGenerics.policy.linearProgram.deprecated.MinimalRiskReachAbilityCalculatorDeprecated;
import vahy.paperGenerics.policy.riskSubtree.MinimalRiskReachAbilityCalculator;
import vahy.paperGenerics.selector.PaperNodeSelector;
import vahy.paperGenerics.testDomain.EmptySpaceRiskState;
import vahy.utils.ImmutableTuple;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Function;

public class FlowConstraintCalculatorTest {

    private static final Logger logger = LoggerFactory.getLogger(FlowConstraintCalculatorTest.class.getName());
    private static final double TOLERANCE = Math.pow(10, -8);

    @Test
    public void optimalFlowHardConstraintComparisonTest() {
        var random = new SplittableRandom(0);
        EmptySpaceAction spaceAction = EmptySpaceAction.A;
        var paperNodeSelector = new PaperNodeSelector<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState>(1, random, spaceAction.getAllPlayerActions().length);

        EmptySpaceRiskState state = new EmptySpaceRiskState(true, random, false, 0.05);

        EmptySpaceAction[] playerActions = Arrays.stream(spaceAction.getAllPlayerActions()).toArray(EmptySpaceAction[]::new);
        EmptySpaceAction[] opponentActions = Arrays.stream(spaceAction.getAllOpponentActions()).toArray(EmptySpaceAction[]::new);

        var actionCount = playerActions.length;

        var metadataFactory = new PaperMetadataFactory<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState>(EmptySpaceAction.class);
        var nodeFactory = new SearchNodeBaseFactoryImpl<>(EmptySpaceAction.class, metadataFactory);
        Function<EmptySpaceRiskState, ImmutableTuple<List<EmptySpaceAction>, List<Double>>> function = x -> new ImmutableTuple<>(List.of(EmptySpaceAction.AA, EmptySpaceAction.BB), List.of(1.0/3, 2.0/3));
        var nodeEvaluator = new PaperNodeEvaluator<>(nodeFactory, new EmptyPredictor(new double[2 + actionCount]), function, playerActions, opponentActions);


        var total = 0;
        var sum_UCB = 0.0;
        var sum_BUILD_CALC_1 = 0.0;
        var sum_BUILD_CALC_2 = 0.0;
        var sum_BUILD_SOLVE_1 = 0.0;
        var sum_BUILD_SOLVE_2 = 0.0;

        for (int k = 900; k < 1000; k++) {
            for (int j = 0; j < 10; j++) {
                var searchTree = initializeTree(paperNodeSelector, state, nodeEvaluator);
                sum_UCB = buildTree(sum_UCB, k, searchTree);

                long start = System.currentTimeMillis();
                var calculator = new OptimalFlowHardConstraintCalculator<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState>(0.0, new SplittableRandom(0), NoiseStrategy.NOISY_05_06);
                sum_BUILD_CALC_1 += System.currentTimeMillis() - start;
                var calculator2 = new OptimalFlowHardConstraintCalculatorDeprecated<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState>(EmptySpaceAction.class, 0.0, new SplittableRandom(0), NoiseStrategy.NOISY_05_06);

                start = System.currentTimeMillis();
                boolean firstSolvable = calculator.optimizeFlow(searchTree.getRoot());
                sum_BUILD_SOLVE_1 += System.currentTimeMillis() - start;

                double[] values = fillArray(searchTree.getRoot(), actionCount);

                start = System.currentTimeMillis();
                boolean secondSolvable = calculator2.optimizeFlow(searchTree.getRoot());
                sum_BUILD_SOLVE_2 += System.currentTimeMillis() - start;

                double[] values2 = fillArray(searchTree.getRoot(), actionCount);

                assertResults(firstSolvable, values, secondSolvable, values2);

                total++;
                if (total % 100 == 0) {
                    printStatistics(total, sum_UCB, sum_BUILD_CALC_1, sum_BUILD_SOLVE_1, sum_BUILD_SOLVE_2, k, j);
                }
            }
        }
    }


    @Test
    public void optimalFlowSoftConstraintComparisonTest() {
        var random = new SplittableRandom(0);
        EmptySpaceAction spaceAction = EmptySpaceAction.A;
        var paperNodeSelector = new PaperNodeSelector<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState>(1, random, spaceAction.getAllPlayerActions().length);

        EmptySpaceRiskState state = new EmptySpaceRiskState(true, random, false, 0.05);

        EmptySpaceAction[] playerActions = Arrays.stream(spaceAction.getAllPlayerActions()).toArray(EmptySpaceAction[]::new);
        EmptySpaceAction[] opponentActions = Arrays.stream(spaceAction.getAllOpponentActions()).toArray(EmptySpaceAction[]::new);

        var actionCount = playerActions.length;

        var metadataFactory = new PaperMetadataFactory<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState>(EmptySpaceAction.class);
        var nodeFactory = new SearchNodeBaseFactoryImpl<>(EmptySpaceAction.class, metadataFactory);
        Function<EmptySpaceRiskState, ImmutableTuple<List<EmptySpaceAction>, List<Double>>> function = x -> new ImmutableTuple<>(List.of(EmptySpaceAction.AA, EmptySpaceAction.BB), List.of(1.0/3, 2.0/3));
        var nodeEvaluator = new PaperNodeEvaluator<>(nodeFactory, new EmptyPredictor(new double[2 + actionCount]), function, playerActions, opponentActions);


        var total = 0;
        var sum_UCB = 0.0;
        var sum_BUILD_CALC_1 = 0.0;
        var sum_BUILD_CALC_2 = 0.0;
        var sum_BUILD_SOLVE_1 = 0.0;
        var sum_BUILD_SOLVE_2 = 0.0;

        for (int k = 900; k < 1000; k++) {
            for (int j = 0; j < 10; j++) {
                var searchTree = initializeTree(paperNodeSelector, state, nodeEvaluator);
                sum_UCB = buildTree(sum_UCB, k, searchTree);

                long start = System.currentTimeMillis();
                var calculator = new OptimalFlowSoftConstraintCalculator<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState>(0.5, new SplittableRandom(0), NoiseStrategy.NOISY_05_06);
                sum_BUILD_CALC_1 += System.currentTimeMillis() - start;
                var calculator2 = new OptimalFlowSoftConstraintDeprecateed<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState>(EmptySpaceAction.class, 0.5, new SplittableRandom(0), NoiseStrategy.NOISY_05_06);

                start = System.currentTimeMillis();
                boolean firstSolvable = calculator.optimizeFlow(searchTree.getRoot());
                sum_BUILD_SOLVE_1 += System.currentTimeMillis() - start;

                double[] values = fillArray(searchTree.getRoot(), actionCount);

                start = System.currentTimeMillis();
                boolean secondSolvable = calculator2.optimizeFlow(searchTree.getRoot());
                sum_BUILD_SOLVE_2 += System.currentTimeMillis() - start;

                double[] values2 = fillArray(searchTree.getRoot(), actionCount);

                assertResults(firstSolvable, values, secondSolvable, values2);

                total++;
                if (total % 100 == 0) {
                    printStatistics(total, sum_UCB, sum_BUILD_CALC_1, sum_BUILD_SOLVE_1, sum_BUILD_SOLVE_2, k, j);
                }
            }
        }
    }

    @Test
    public void minimalRiskReachabilityCalculatorTest() {
        var random = new SplittableRandom(0);
        EmptySpaceAction spaceAction = EmptySpaceAction.A;
        var paperNodeSelector = new PaperNodeSelector<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState>(1, random, spaceAction.getAllPlayerActions().length);

        EmptySpaceRiskState state = new EmptySpaceRiskState(true, random, false, 0.05);

        EmptySpaceAction[] playerActions = Arrays.stream(spaceAction.getAllPlayerActions()).toArray(EmptySpaceAction[]::new);
        EmptySpaceAction[] opponentActions = Arrays.stream(spaceAction.getAllOpponentActions()).toArray(EmptySpaceAction[]::new);

        var actionCount = playerActions.length;

        var metadataFactory = new PaperMetadataFactory<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState>(EmptySpaceAction.class);
        var nodeFactory = new SearchNodeBaseFactoryImpl<>(EmptySpaceAction.class, metadataFactory);
        Function<EmptySpaceRiskState, ImmutableTuple<List<EmptySpaceAction>, List<Double>>> function = x -> new ImmutableTuple<>(List.of(EmptySpaceAction.AA, EmptySpaceAction.BB), List.of(1.0/3, 2.0/3));
        var nodeEvaluator = new PaperNodeEvaluator<>(nodeFactory, new EmptyPredictor(new double[2 + actionCount]), function, playerActions, opponentActions);


        var total = 0;
        var sum_UCB = 0.0;
        var sum_BUILD_CALC_1 = 0.0;
        var sum_BUILD_CALC_2 = 0.0;
        var sum_BUILD_SOLVE_1 = 0.0;
        var sum_BUILD_SOLVE_2 = 0.0;

        for (int k = 900; k < 1000; k++) {
            for (int j = 0; j < 10; j++) {
                var searchTree = initializeTree(paperNodeSelector, state, nodeEvaluator);
                sum_UCB = buildTree(sum_UCB, k, searchTree);

                long start = System.currentTimeMillis();
                var calculator = new MinimalRiskReachAbilityCalculatorDeprecated<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState>(EmptySpaceAction.class);
                sum_BUILD_CALC_1 += System.currentTimeMillis() - start;
                var calculator2 = new MinimalRiskReachAbilityCalculator<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState>();

                start = System.currentTimeMillis();
                double solution1 = calculator.calculateRisk(searchTree.getRoot());
                sum_BUILD_SOLVE_1 += System.currentTimeMillis() - start;

                start = System.currentTimeMillis();
                double solution2 = calculator2.calculateRisk(searchTree.getRoot());
                sum_BUILD_SOLVE_2 += System.currentTimeMillis() - start;

                Assert.assertEquals(solution1, solution2, TOLERANCE, "Solutions are different");

                total++;
                if (total % 100 == 0) {
                    printStatistics(total, sum_UCB, sum_BUILD_CALC_1, sum_BUILD_SOLVE_1, sum_BUILD_SOLVE_2, k, j);
                }
            }
        }
    }

    private static double[] fillArray(SearchNode<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState> root, int actionCount) {
        double[] values = new double[actionCount];
        int index = 0;
        for (var entry : root.getChildNodeMap().entrySet()) {
            values[index] = entry.getValue().getSearchNodeMetadata().getFlow();
            index++;
        }
        return values;
    }

    private SearchTreeImpl<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState> initializeTree(
        PaperNodeSelector<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, EmptySpaceRiskState> paperNodeSelector, EmptySpaceRiskState state,
        PaperNodeEvaluator<EmptySpaceAction, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState> nodeEvaluator)
    {
        var root = new SearchNodeImpl<>(
            state,
            new PaperMetadata<>(0.0, 0.0, 0.0, 0.0, 0.0, new EnumMap<>(EmptySpaceAction.class)),
            new EnumMap<>(EmptySpaceAction.class)
        );
        return new SearchTreeImpl<>(
            root,
            paperNodeSelector,
            new PaperTreeUpdater<>(),
            nodeEvaluator
        );
    }

    private double buildTree(double sum_UCB, int k, SearchTreeImpl<EmptySpaceAction, DoubleVector, EmptySpaceRiskState, PaperMetadata<EmptySpaceAction>, EmptySpaceRiskState> searchTree) {
        var start = System.currentTimeMillis();
        for (int i = 0; i < k; i++) {
            searchTree.updateTree();
        }
        sum_UCB += System.currentTimeMillis() - start;
        return sum_UCB;
    }

    private void printStatistics(int total, double sum_UCB, double sum_BUILD_CALC_1, double sum_BUILD_SOLVE_1, double sum_BUILD_SOLVE_2, int k, int j) {
        logger.info("------------------------" + k + "------------------------");
        logger.info("------------------------" + j + "------------------------");
        logger.info("------------------------" + total + "------------------------");
        logger.info("Tree update took: [{}] milliseconds", sum_UCB / total);
        logger.info("Calculator creation took: [{}] milliseconds", sum_BUILD_CALC_1 / total);
        logger.info("Optimizing FLOW_1 took: [{}] milliseconds", sum_BUILD_SOLVE_1 / total);
        logger.info("Optimizing FLOW_2 took: [{}] milliseconds", sum_BUILD_SOLVE_2 / total);
    }

    private void assertResults(boolean firstSolvable, double[] values, boolean secondSolvable, double[] values2) {
        Assert.assertEquals(secondSolvable, firstSolvable, "Solutions differs");
        if(firstSolvable) {
            for (int i = 0; i < 2; i++) {
                Assert.assertEquals(values[i], values2[i], TOLERANCE, "Different results. First: " + Arrays.toString(values) + " second: " + Arrays.toString(values2)+ ".");
            }
        } else {
//            logger.info("Unsolvable problem.");
        }
    }
}
