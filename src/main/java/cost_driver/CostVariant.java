package cost_driver;

import org.springframework.lang.NonNull;

import java.util.*;

public class CostVariant {

    @NonNull
    protected String id;
    @NonNull
    protected Double frequency;
    @NonNull
    protected Map<String, String> concretisedACD;

    public CostVariant(@NonNull String id, @NonNull Double frequency, @NonNull Map<String, String> concretisedACD) {
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

    public Map<String, String> getConcretisedACD() {
        return concretisedACD;
    }

}
