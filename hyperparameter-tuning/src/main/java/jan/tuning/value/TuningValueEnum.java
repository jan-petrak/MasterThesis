package jan.tuning.value;

public class TuningValueEnum<T extends Enum<T>> implements TuningValue {

    Enum<T> enumValue;

    public TuningValueEnum(Enum<T> enumValue) {
        this.enumValue = enumValue;
    }

    @Override
    public Enum<T> getValue() {
        return enumValue;
    }
}
