package jan.tuning.param;

import jan.tuning.value.TuningValue;

public interface TuningParam {
    String getName();

    Number getNumericValue();
    TuningValue getInternalValue(Number x);
}
