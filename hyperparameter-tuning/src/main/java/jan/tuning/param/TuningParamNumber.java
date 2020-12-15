package jan.tuning.param;

import jan.tuning.value.TuningValue;
import jan.tuning.value.TuningValueDouble;
import jan.tuning.value.TuningValueInteger;

public class TuningParamNumber implements TuningParam {

    String paramName;
    Number domainFrom;
    Number domainTo;
    Number step;

    public TuningParamNumber(String paramName, Number domainFrom, Number domainTo, Number step) {
        this.paramName = paramName;
        this.domainFrom = domainFrom;
        this.domainTo = domainTo;
        this.step = step;
    }

    @Override
    public String getName() {
        return paramName;
    }

    @Override
    public Number getNumericValue() {
        return null;
    }

    @Override
    public TuningValue getInternalValue(Number x) {
        if (x instanceof Double) {
            return new TuningValueDouble((Double) x);
        }
        return new TuningValueInteger((Integer) x);
    }
}
