package jan.tuning;

import org.json.JSONObject;

public class TuningParser {

    TuningParamConfig paramConfig;

    public TuningParser(TuningParamConfig paramConfig) {
        this.paramConfig = paramConfig;
    }

    TuningTrial decodeTrial(String trialStr) {
        TuningTrial trial = new TuningTrial(paramConfig.getFixedValues());

        JSONObject obj = new JSONObject(trialStr);

        // TODO: make generic
        trial.addTuningValue("learningRate", paramConfig.getTuningParams().get("learningRate").getInternalValue(obj.getDouble("learningRate")));
        trial.addTuningValue("flowOptimizerType", paramConfig.getTuningParams().get("flowOptimizerType").getInternalValue(obj.getInt("flowOptimizerType")));

        return trial;
    }
}
