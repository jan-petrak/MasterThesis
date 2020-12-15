package jan.tuning;

import jan.tuning.param.TuningParam;
import jan.tuning.value.TuningValue;

import java.util.HashMap;

public class TuningParamConfig {

    HashMap<String, TuningValue> fixedValues;
    HashMap<String, TuningParam> tuningParams;

    public TuningParamConfig(HashMap<String, TuningValue> fixedValues, TuningParam ... tuningParams) {
        this.fixedValues = fixedValues;
        this.tuningParams = new HashMap<>();
        for (var param : tuningParams) {
            this.tuningParams.put(param.getName(), param);
        }
    }

    public HashMap<String, TuningValue> getFixedValues() {
        return fixedValues;
    }

    public HashMap<String, TuningParam> getTuningParams() {
        return tuningParams;
    }
}
