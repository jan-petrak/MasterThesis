package vahy.paperGenerics.selector;

import com.quantego.clp.CLP;
import com.quantego.clp.CLPExpression;
import com.quantego.clp.CLPVariable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.observation.Observation;
import vahy.api.search.node.SearchNode;
import vahy.paperGenerics.metadata.PaperMetadata;
import vahy.utils.Experimental;
import vahy.utils.ImmutableTuple;
import vahy.utils.RandomDistributionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

@Experimental
public class RiskBasedSelector_V2<
    TAction extends Enum<TAction> & Action,
    TPlayerObservation extends Observation,
    TOpponentObservation extends Observation,
    TState extends State<TAction, TPlayerObservation, TOpponentObservation, TState>>
    extends PaperNodeSelector<TAction, TPlayerObservation, TOpponentObservation, TState> {

    private final Logger logger = LoggerFactory.getLogger(RiskBasedSelector_V1.class.getName());

    @Experimental
    public RiskBasedSelector_V2(double cpuctParameter, SplittableRandom random) {
        super(cpuctParameter, random);
    }

    protected final ImmutableTuple<Double, Double> getMinMax(SearchNode<TAction, TPlayerObservation, TOpponentObservation, PaperMetadata<TAction>, TState> node) {
        double helpMax = -Double.MAX_VALUE;
        double helpMin = Double.MAX_VALUE;

        for (SearchNode<TAction, TPlayerObservation, TOpponentObservation, PaperMetadata<TAction>, TState> entry : node.getChildNodeMap().values()) {
            double value = entry.getSearchNodeMetadata().getExpectedReward() + entry.getSearchNodeMetadata().getGainedReward();
            if(helpMax < value) {
                helpMax = value;
            }
            if(helpMin > value) {
                helpMin = value;
            }
        }
        return new ImmutableTuple<>(helpMin, helpMax);
    }

    protected final double getExtremeElement(SearchNode<TAction, TPlayerObservation, TOpponentObservation, PaperMetadata<TAction>, TState> node,
                                             Function<DoubleStream, OptionalDouble> function,
                                             String nonExistingElementMessage) {
        return function.apply(node
            .getChildNodeStream()
            .mapToDouble(x -> x.getSearchNodeMetadata().getExpectedReward())
        ).orElseThrow(() -> new IllegalStateException(nonExistingElementMessage));
    }

    @NotNull
    private Function<
        SearchNode<TAction, TPlayerObservation, TOpponentObservation, PaperMetadata<TAction>, TState>,
        ImmutableTuple<TAction, Double>>
    getSearchNodeImmutableTupleFunction(final int totalNodeVisitCount, final double min, final double max)
    {
        return x -> {
            var metadata = x.getSearchNodeMetadata();
            TAction action = x.getAppliedAction();
            double uValue = calculateUValue(metadata.getPriorProbability(), metadata.getVisitCounter(), totalNodeVisitCount);
            double qValue = max == min
                ? 0.5
                : (((metadata.getExpectedReward() + metadata.getGainedReward()) - min) / (max - min));
            return new ImmutableTuple<>(action, qValue + uValue);
        };
    }


    @Override
    protected TAction getBestAction(SearchNode<TAction, TPlayerObservation, TOpponentObservation, PaperMetadata<TAction>, TState> node) {
        int totalNodeVisitCount = node.getSearchNodeMetadata().getVisitCounter();

//                final double max = getExtremeElement(node, DoubleStream::max, "Maximum Does not exists");
//                final double min = getExtremeElement(node, DoubleStream::min, "Minimum Does not exists");

        ImmutableTuple<Double, Double> minMax = getMinMax(node);
        final double min = minMax.getFirst();
        final double max = minMax.getSecond();
        assert(min <= max); // paranoia

        List<ImmutableTuple<TAction, Double>> actionsUcbValue = node.getChildNodeStream()
            .map(getSearchNodeImmutableTupleFunction(totalNodeVisitCount, min, max))
            .collect(Collectors.toList());

        CLP model = new CLP();
        final CLPExpression sumToOneExpression = model.createExpression();
        final SearchNode<TAction, TPlayerObservation, TOpponentObservation, PaperMetadata<TAction>, TState> finalNodeReference = node;
        List<ImmutableTuple<ImmutableTuple<TAction, Double>, CLPVariable>> collect = actionsUcbValue
            .stream()
            .map(x -> {
                CLPVariable probabilityVariable = model.addVariable().lb(0.0).ub(1.0);
                model.setObjectiveCoefficient(probabilityVariable, x.getSecond() * (1 - finalNodeReference.getChildNodeMap().get(x.getFirst()).getSearchNodeMetadata().getPredictedRisk()));
                sumToOneExpression.add(probabilityVariable, 1.0);
                return new ImmutableTuple<>(x, probabilityVariable);
            })
            .collect(Collectors.toCollection(ArrayList::new));
        sumToOneExpression.eq(1.0);

        CLP.STATUS status = model.maximize();

        if(status != CLP.STATUS.OPTIMAL) {
            throw new IllegalStateException("Optimal solution was not found");
        }

        ArrayList<Double> probabilities = collect.stream().map(x -> x.getSecond().getSolution()).collect(Collectors.toCollection(ArrayList::new));
        int actionIndex = RandomDistributionUtils.getRandomIndexFromDistribution(probabilities, random);

        return collect.get(actionIndex).getFirst().getFirst();


    }
}
