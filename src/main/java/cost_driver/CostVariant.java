package cost_driver;

import org.springframework.lang.NonNull;

import java.util.*;

public class CostVariant {

    @NonNull
    protected String id;
    @NonNull
    protected Double frequency;
    @NonNull
    protected Map<String, Double> concretisedACD;

    public CostVariant(@NonNull String id, @NonNull Double frequency, @NonNull Map<String, Double> concretisedACD) {
        this.concretisedACD = concretisedACD;
        this.frequency = frequency;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Double getFrequency() {
        return frequency;
    }

    public Map<String, Double> getConcretisedACD() {
        return concretisedACD;
    }

    public Double getSum() {
        return concretisedACD.values().stream().mapToDouble(i -> i).sum();
    }
}
