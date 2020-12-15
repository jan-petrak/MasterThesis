package jan.tuning.value;

public class TuningValueDouble implements TuningValue {

    Double value;

    public TuningValueDouble(Double value) {
        this.value = value;
    }

    @Override
    public Double getValue() {
        return value;
    }
}
