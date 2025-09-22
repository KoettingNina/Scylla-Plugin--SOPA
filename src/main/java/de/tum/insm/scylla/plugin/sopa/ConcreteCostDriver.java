package de.tum.insm.scylla.plugin.sopa;

import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.NonNull;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;

public class ConcreteCostDriver extends CostDriver {
    @NonNull
    protected CostDriver parent;
    @NonNull
    protected Double LCAScore;
    private static OpenLcaCostCalculator costCalculator;
    
    private static String bridgeServerUrl = "http://localhost:8081";
    
    // Add cache for calculated costs
    private static final Map<String, Double> costCache = new HashMap<>();
    private static final Object cacheLock = new Object();

    // Add distribution field
    private Map<String, Object> distribution;
    
    public ConcreteCostDriver(@NonNull String id, @NonNull CostDriver parent, @NonNull Double LCAScore) throws ScyllaValidationException {
        super(id);
        if (parent instanceof AbstractCostDriver) {
            this.parent = parent;
        } else {
            throw new ScyllaValidationException("Parent cost driver is abstract c");
        }

        this.LCAScore = LCAScore;
        if (costCalculator == null) {
            costCalculator = new OpenLcaCostCalculator(bridgeServerUrl);
        }
    }

    public Map<String, Object> getDistribution() {
        return distribution;
    }

    public void setDistribution(Map<String, Object> distribution) {
        this.distribution = distribution;
    }

    public static void setBridgeServerUrl(String url) {
        bridgeServerUrl = url;
        costCalculator = new OpenLcaCostCalculator(url);
    }

    public Double getLCAScore() {
        return LCAScore;
    }

    public CostDriver getParent() {
        return parent;
    }

    public void setParent(CostDriver parent) {
        this.parent = parent;
    }

    public String getOpenLCAProductSystemRef() {
        return id;
    }

    
    public Double calculateCost(String impactMethodId, String normalizationSetId, double amount) {
        // Use id as the OpenLCA reference
        String productSystemId = id;
        String impactMethodIdToUse = impactMethodId;
        String normalizationSetIdToUse = normalizationSetId;
        double amountToUse = amount;

        // Create a unique cache key
        String cacheKey = String.format("%s_%s_%s_%.2f", 
            productSystemId, 
            impactMethodIdToUse, 
            normalizationSetIdToUse != null ? normalizationSetIdToUse : "null",
            amountToUse);
        
        // Check cache first
        synchronized (cacheLock) {
            Double cachedCost = costCache.get(cacheKey);
            if (cachedCost != null) {
                return cachedCost;
            }
        }

        try {
            if (costCalculator == null) {
                System.err.println("[ConcreteCostDriver] ERROR: Cost calculator is null!");
                return LCAScore;
            }

            String resultJson = costCalculator.calculateCostViaBridge(
                productSystemId,
                impactMethodIdToUse,
                normalizationSetIdToUse,
                amountToUse
            );
            
            // Parse the JSON response
            try {
                
                resultJson = resultJson.trim();
                if (resultJson.startsWith("{") && resultJson.endsWith("}")) {
                    // Extract the success value
                    int successIndex = resultJson.indexOf("\"success\":");
                    if (successIndex != -1) {
                        String successPart = resultJson.substring(successIndex + "\"success\":".length());
                        successPart = successPart.substring(0, successPart.indexOf(",")).trim();
                        boolean isSuccess = Boolean.parseBoolean(successPart);
                        
                        if (isSuccess) {
                            // Extract cost value
                            int costIndex = resultJson.indexOf("\"cost\":");
                            if (costIndex != -1) {
                                String costPart = resultJson.substring(costIndex + "\"cost\":".length());
                                costPart = costPart.substring(0, costPart.indexOf("}")).trim();
                                double calculatedCost = Double.parseDouble(costPart);
                                
                                // Store in cache
                                synchronized (cacheLock) {
                                    costCache.put(cacheKey, calculatedCost);
                                }
                                
                                System.out.println("[ConcreteCostDriver] Successfully calculated cost: " + calculatedCost);
                                return calculatedCost;
                            }
                        }
                    }
                }
                System.err.println("[ConcreteCostDriver] Error parsing response for " + id + ": " + resultJson);
                return LCAScore;
            } catch (Exception e) {
                System.err.println("[ConcreteCostDriver] Error parsing response for " + id + ": " + e.getMessage());
                System.err.println("[ConcreteCostDriver] Stack trace:");
                e.printStackTrace(System.err);
                return LCAScore;
            }
        } catch (Exception e) {
            System.err.println("[ConcreteCostDriver] Error calculating cost for " + id + ": " + e.getMessage());
            System.err.println("[ConcreteCostDriver] Stack trace:");
            e.printStackTrace(System.err);
            return LCAScore;
        }
    }

    // Add method to clear cache if needed
    public static void clearCostCache() {
        synchronized (cacheLock) {
            costCache.clear();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ConcreteCostDriver concreteCostDriver)) return false;
        boolean isParentEqual = parent.id.compareTo(concreteCostDriver.parent.id) == 0;
        return id.compareTo(concreteCostDriver.id) == 0 &&
                isParentEqual &&
                Double.compare(LCAScore, concreteCostDriver.LCAScore) == 0;
    }

    @Override
    public String toString() {
        return "\nConcreteCostDriver{" +
                "id='" + id + '\'' +
                ", parentID=" + (parent != null ? parent.id : "null") +
                ", LCAScore=" + LCAScore +
                '}';
    }
}
