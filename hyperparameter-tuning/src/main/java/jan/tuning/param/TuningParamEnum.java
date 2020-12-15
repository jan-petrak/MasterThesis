package jan.tuning.param;

import jan.tuning.value.TuningValueEnum;

public class TuningParamEnum<T extends Enum<T>> implements TuningParam {

    String paramName;
    Enum<T>[] enumValues;

    public TuningParamEnum(String paramName, Enum<T>[] enumValues) {
        this.paramName = paramName;
        this.enumValues = enumValues;
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
    public TuningValueEnum<T> getInternalValue(Number x) {
        return new TuningValueEnum<>(enumValues[(Integer) x]);
    }
}
