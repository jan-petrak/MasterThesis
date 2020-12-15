package jan.tuning.value;

public class TuningValueInteger implements TuningValue {
    Integer value;

    public TuningValueInteger(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}
