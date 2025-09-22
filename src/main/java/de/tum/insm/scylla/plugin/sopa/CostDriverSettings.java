package de.tum.insm.scylla.plugin.sopa;

public class CostDriverSettings {
    private String impactMethod;
    private String normalizationSet;
    private static CostDriverSettings instance;

    private CostDriverSettings() {
        // Private constructor for singleton pattern
    }

    public static CostDriverSettings getInstance() {
        if (instance == null) {
            instance = new CostDriverSettings();
        }
        return instance;
    }

    public String getImpactMethod() {
        return impactMethod;
    }

    public void setImpactMethod(String impactMethod) {
        this.impactMethod = impactMethod;
    }

    public String getNormalizationSet() {
        return normalizationSet;
    }

    public void setNormalizationSet(String normalizationSet) {
        this.normalizationSet = normalizationSet;
    }
} 