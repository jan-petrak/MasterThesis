package vahy.impl.model.observation;

import vahy.api.model.observation.Observation;

import java.util.Arrays;

public class DoubleVector implements Observation {

    private final double[] observedVector;

    public DoubleVector(double[] observedVector) {
        this.observedVector = observedVector;
    }

    public double[] getObservedVector() {
        return observedVector;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleVector)) return false;

        DoubleVector that = (DoubleVector) o;

        return Arrays.equals(getObservedVector(), that.getObservedVector());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getObservedVector());
    }
}