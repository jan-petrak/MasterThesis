package jan.tuning;

public class TuningConfigBuilder {
    TuningConfig config;

    public TuningConfigBuilder() {
        config = new TuningConfig();
    }
    
    public TuningConfig build() {
        return config;
    }

    public TuningConfigBuilder setSamples(int samples) {
        config.setSamples(samples);
        return this;
    }
    public TuningConfigBuilder setName(String name) {
        config.setName(name);
        return this;
    }
}
