package vahy.api.episode;

import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.observation.Observation;
import vahy.api.policy.PolicyRecord;
import vahy.api.policy.PolicySupplier;

import java.util.List;
public class PolicyCategory<TAction extends Enum<TAction> & Action, TObservation extends Observation, TState extends State<TAction, TObservation, TState>, TPolicyRecord extends PolicyRecord> {

    private final int categoryId;
    private final List<PolicySupplier<TAction, TObservation, TState, TPolicyRecord>> policySupplierList;

    public PolicyCategory(int categoryId, List<PolicySupplier<TAction, TObservation, TState, TPolicyRecord>> policySupplierList) {
        this.categoryId = categoryId;
        this.policySupplierList = policySupplierList;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public List<PolicySupplier<TAction, TObservation, TState, TPolicyRecord>> getPolicySupplierList() {
        return policySupplierList;
    }
}