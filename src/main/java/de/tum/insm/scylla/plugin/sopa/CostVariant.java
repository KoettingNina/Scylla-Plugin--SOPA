package de.tum.insm.scylla.plugin.sopa;

import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.NonNull;

public class CostVariant {

    @NonNull
    protected String id;
    @NonNull
    protected Double frequency;
    @NonNull
    protected Map<String, String> concretisedACD;
    private Map<String, Map<String, Object>> driverDistributions;

    public CostVariant(@NonNull String id, @NonNull Double frequency, @NonNull Map<String, String> concretisedACD) {
        this.concretisedACD = concretisedACD;
        this.frequency = frequency;
        this.id = id;
        this.driverDistributions = new HashMap<>();
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

    public Map<String, Map<String, Object>> getDriverDistributions() {
        return driverDistributions;
    }

    public void setDriverDistributions(Map<String, Map<String, Object>> driverDistributions) {
        this.driverDistributions = driverDistributions;
    }

}
