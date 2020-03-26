package vahy.paperGenerics.metadata;

import com.quantego.clp.CLPVariable;
import vahy.api.model.Action;
import vahy.impl.search.MCTS.MonteCarloTreeSearchMetadata;

import java.util.Map;

public class PaperMetadata<TAction extends Action> extends MonteCarloTreeSearchMetadata {

    private final Map<TAction, Double> childPriorProbabilities;
    private CLPVariable nodeProbabilityFlow;
    private double priorProbability;
    private double predictedRisk;
    private double sumOfRisk;

    public PaperMetadata(double cumulativeReward,
                         double gainedReward,
                         double predictedReward,
                         double priorProbability,
                         double predictedRisk,
                         Map<TAction, Double> childPriorProbabilities) {
        super(cumulativeReward, gainedReward, predictedReward);
        this.priorProbability = priorProbability;
        this.predictedRisk = predictedRisk;
        this.childPriorProbabilities = childPriorProbabilities;
        this.sumOfRisk = predictedRisk;
    }

    public double getPriorProbability() {
        return priorProbability;
    }

    public void setPriorProbability(double priorProbability) {
        this.priorProbability = priorProbability;
    }

    public double getPredictedRisk() {
        return predictedRisk;
    }

    public void setPredictedRisk(double predictedRisk) {
        this.predictedRisk = predictedRisk;
    }

    public CLPVariable getNodeProbabilityFlow() {
        return nodeProbabilityFlow;
    }

    public void setNodeProbabilityFlow(CLPVariable nodeProbabilityFlow) {
        this.nodeProbabilityFlow = nodeProbabilityFlow;
    }

    public Map<TAction, Double> getChildPriorProbabilities() {
        return childPriorProbabilities;
    }

    public double getSumOfRisk() {
        return sumOfRisk;
    }

    public void setSumOfRisk(double sumOfRisk) {
        this.sumOfRisk = sumOfRisk;
    }

    @Override
    public String toString() {
        String baseString = super.toString();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(baseString);
        stringBuilder.append("\\nPriorProbability: ");
        stringBuilder.append(this.priorProbability);
        stringBuilder.append("\\nPredictedRisk: ");
        stringBuilder.append(this.predictedRisk);
        stringBuilder.append("\\nSumOfPredictedRisk: ");
        stringBuilder.append(this.sumOfRisk);
        stringBuilder.append("\\nCalculatedFlow: ");
        stringBuilder.append(nodeProbabilityFlow != null ? this.nodeProbabilityFlow.getSolution() : null);
        return stringBuilder.toString();
    }
}
