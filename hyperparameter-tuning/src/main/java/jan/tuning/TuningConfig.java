package jan.tuning;

public class TuningConfig {

    private String name;
    private int samples = 10;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public int getSamples() {
        return samples;
    }
    public void setSamples(int samples) {
        this.samples = samples;
    }
}
