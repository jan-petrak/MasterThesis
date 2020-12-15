package jan.tuning;

import jan.tuning.value.TuningValue;

import java.util.HashMap;

public class TuningTrial {
    HashMap<String, TuningValue> trial;

    public TuningTrial() {
        trial = new HashMap<>();
    }

    public TuningTrial(HashMap<String, TuningValue> fixedValues) {
        trial = new HashMap<>(fixedValues);
    }

    public Object getValue(String key) {
        return trial.get(key).getValue();
    }

    public void addTuningValue(String key, TuningValue value) {
        trial.put(key, value);
    }
}