package de.tum.insm.scylla.plugin.sopa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;
import org.jdom2.Namespace;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.plugin_type.parser.SimulationConfigurationParserPluggable;

public class CostDriverSCParserPlugin extends SimulationConfigurationParserPluggable {

    @Override
    public String getName() {
        return CostDriverPluginUtils.PLUGIN_NAME;
    }

    @Override
    public Map<String, Object> parse(SimulationConfiguration simulationInput, Element sim)
            throws ScyllaValidationException {
        /*
        Parse cost variants
        */
        Namespace bsimNamespace = sim.getNamespace();
        Element costVariantConfig = sim.getChildren("costVariantConfig", bsimNamespace).get(0);
        Integer count = Integer.valueOf(sim.getAttributeValue("processInstances"));

        Map<String, Object> extensionAttributes = new HashMap<>();
        List<CostVariant> costVariantList = new ArrayList<>();

        Double frequencyCount = 0.0;

        for (Element variant : costVariantConfig.getChildren()) {
            String id = variant.getAttributeValue("id");
            Double frequency = Double.valueOf(variant.getAttributeValue("frequency"));
            frequencyCount += frequency;

            Map<String, String> concretisedACD = new HashMap<>();
            Map<String, Map<String, Object>> driverDistributions = new HashMap<>();

            for (Element driver : variant.getChildren()) {
                String abstractId = driver.getAttributeValue("abstractId");
                String concreteId = driver.getAttributeValue("concreteId");

                concretisedACD.put(abstractId, concreteId);

                // Extract distribution information 
                Element distributionElement = driver.getChild("distribution", bsimNamespace);
                if (distributionElement != null) {
                    Map<String, Object> distribution = new HashMap<>();
                    
                    // Check for constant distribution
                    Element constantDistElement = distributionElement.getChild("constantDistribution", bsimNamespace);
                    if (constantDistElement != null) {
                        distribution.put("distributionType", "constantDistribution");
                        List<Map<String, Object>> values = new ArrayList<>();
                        Map<String, Object> value = new HashMap<>();
                        value.put("id", "constantValue");
                        value.put("value", Double.valueOf(constantDistElement.getChildText("constantValue", bsimNamespace)));
                        values.add(value);
                        distribution.put("values", values);
                    } else {
                        // Check for exponential distribution
                        Element expDistElement = distributionElement.getChild("exponentialDistribution", bsimNamespace);
                        if (expDistElement != null) {
                            distribution.put("distributionType", "exponentialDistribution");
                            List<Map<String, Object>> values = new ArrayList<>();
                            Map<String, Object> value = new HashMap<>();
                            value.put("id", "mean");
                            value.put("value", Double.valueOf(expDistElement.getChildText("mean", bsimNamespace)));
                            values.add(value);
                            distribution.put("values", values);
                        } else {
                            // Check for normal distribution
                            Element normalDistElement = distributionElement.getChild("normalDistribution", bsimNamespace);
                            if (normalDistElement != null) {
                                distribution.put("distributionType", "normalDistribution");
                                List<Map<String, Object>> values = new ArrayList<>();
                                
                                Map<String, Object> meanValue = new HashMap<>();
                                meanValue.put("id", "mean");
                                meanValue.put("value", Double.valueOf(normalDistElement.getChildText("mean", bsimNamespace)));
                                values.add(meanValue);
                                
                                // Nur <variance> wird unterstützt, daraus Standardabweichung berechnen
                                String varianceText = normalDistElement.getChildText("variance", bsimNamespace);
                                double stdDev = 0.0;
                                if (varianceText != null && !varianceText.isEmpty()) {
                                    stdDev = Math.sqrt(Double.valueOf(varianceText));
                                }
                                Map<String, Object> stdDevValue = new HashMap<>();
                                stdDevValue.put("id", "standardDeviation");
                                stdDevValue.put("value", stdDev);
                                values.add(stdDevValue);
                                
                                distribution.put("values", values);
                            } else {
                                // Check for uniform distribution
                                Element uniformDistElement = distributionElement.getChild("uniformDistribution", bsimNamespace);
                                if (uniformDistElement != null) {
                                    distribution.put("distributionType", "uniformDistribution");
                                    List<Map<String, Object>> values = new ArrayList<>();
                                    
                                    Map<String, Object> lowerValue = new HashMap<>();
                                    lowerValue.put("id", "lower");
                                    lowerValue.put("value", Double.valueOf(uniformDistElement.getChildText("lower", bsimNamespace)));
                                    values.add(lowerValue);
                                    
                                    Map<String, Object> upperValue = new HashMap<>();
                                    upperValue.put("id", "upper");
                                    upperValue.put("value", Double.valueOf(uniformDistElement.getChildText("upper", bsimNamespace)));
                                    values.add(upperValue);
                                    
                                    distribution.put("values", values);
                                } else {
                                    // Check for erlang distribution
                                    Element erlangDistElement = distributionElement.getChild("erlangDistribution", bsimNamespace);
                                    if (erlangDistElement != null) {
                                        distribution.put("distributionType", "erlangDistribution");
                                        List<Map<String, Object>> values = new ArrayList<>();
                                        Map<String, Object> orderValue = new HashMap<>();
                                        orderValue.put("id", "order");
                                        orderValue.put("value", Integer.valueOf(erlangDistElement.getChildText("order", bsimNamespace)));
                                        values.add(orderValue);
                                        Map<String, Object> meanValue = new HashMap<>();
                                        meanValue.put("id", "mean");
                                        meanValue.put("value", Double.valueOf(erlangDistElement.getChildText("mean", bsimNamespace)));
                                        values.add(meanValue);
                                        distribution.put("values", values);
                                    } else {
                                        // Check for triangular distribution
                                        Element triangularDistElement = distributionElement.getChild("triangularDistribution", bsimNamespace);
                                        if (triangularDistElement != null) {
                                            distribution.put("distributionType", "triangularDistribution");
                                            List<Map<String, Object>> values = new ArrayList<>();
                                            Map<String, Object> lowerValue = new HashMap<>();
                                            lowerValue.put("id", "lower");
                                            lowerValue.put("value", Double.valueOf(triangularDistElement.getChildText("lower", bsimNamespace)));
                                            values.add(lowerValue);
                                            Map<String, Object> peakValue = new HashMap<>();
                                            peakValue.put("id", "peak");
                                            peakValue.put("value", Double.valueOf(triangularDistElement.getChildText("peak", bsimNamespace)));
                                            values.add(peakValue);
                                            Map<String, Object> upperValue = new HashMap<>();
                                            upperValue.put("id", "upper");
                                            upperValue.put("value", Double.valueOf(triangularDistElement.getChildText("upper", bsimNamespace)));
                                            values.add(upperValue);
                                            distribution.put("values", values);
                                        } else {
                                            // Check for binomial distribution
                                            Element binomialDistElement = distributionElement.getChild("binomialDistribution", bsimNamespace);
                                            if (binomialDistElement != null) {
                                                distribution.put("distributionType", "binomialDistribution");
                                                List<Map<String, Object>> values = new ArrayList<>();
                                                Map<String, Object> probabilityValue = new HashMap<>();
                                                probabilityValue.put("id", "probability");
                                                probabilityValue.put("value", Double.valueOf(binomialDistElement.getChildText("probability", bsimNamespace)));
                                                values.add(probabilityValue);
                                                Map<String, Object> amountValue = new HashMap<>();
                                                amountValue.put("id", "amount");
                                                amountValue.put("value", Integer.valueOf(binomialDistElement.getChildText("amount", bsimNamespace)));
                                                values.add(amountValue);
                                                distribution.put("values", values);
                                            } else {
                                                // Check for arbitrary finite probability distribution
                                                Element arbitraryFiniteDistElement = distributionElement.getChild("arbitraryFiniteProbabilityDistribution", bsimNamespace);
                                                if (arbitraryFiniteDistElement != null) {
                                                    distribution.put("distributionType", "arbitraryFiniteProbabilityDistribution");
                                                    List<Map<String, Object>> values = new ArrayList<>();
                                                    List<Element> entryElements = arbitraryFiniteDistElement.getChildren("entry", bsimNamespace);
                                                    for (Element entry : entryElements) {
                                                        String valueText = entry.getChildText("value", bsimNamespace);
                                                        if (valueText == null || valueText.isEmpty()) {
                                                            continue; // Leere Werte überspringen
                                                        }
                                                        Map<String, Object> entryValue = new HashMap<>();
                                                        entryValue.put("id", "value");
                                                        entryValue.put("value", Double.valueOf(entry.getChildText("value", bsimNamespace)));
                                                        String freqText = entry.getChildText("frequency", bsimNamespace);
                                                        if (freqText != null && !freqText.isEmpty()) {
                                                            entryValue.put("frequency", Double.valueOf(freqText));
                                                        }
                                                        values.add(entryValue);
                                                    }
                                                    distribution.put("values", values);
                                                } else {
                                                    // Handle other distribution types here if needed
                                                    System.out.println("Warning: Unsupported distribution type for driver " + concreteId);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    driverDistributions.put(concreteId, distribution);
                }
            }

            CostVariant costVariant = new CostVariant(id, frequency, concretisedACD);
            costVariant.setDriverDistributions(driverDistributions);
            costVariantList.add(costVariant);
        }

        if (frequencyCount != 1) {
            throw new ScyllaValidationException("The sum of all cost variants' frequency is not equal to 1");
        }


        CostVariantConfiguration costVariantConfiguration = new CostVariantConfiguration(
                count,
                costVariantList,
                simulationInput.getRandomSeed()
        );
        extensionAttributes.put("CostVariant", costVariantConfiguration);

        /**
         * Parse Concretised abstract cost drivers in tasks
         */
        Map<Integer, List<String>> costDrivers = new HashMap<>();
        // get all cost drivers
        List<Element> elements = sim.getChildren().stream().filter(c -> c.getChild("costDrivers", bsimNamespace) != null).toList();

        for (Element el : elements) {
            String identifier = el.getAttributeValue("id");
            if (identifier == null) {
                DebugLogger.log("Warning: Simulation configuration definition element '" + identifier
                        + "' does not have an identifier, skip.");
                continue; // no matching element in process, so skip definition
            }

            Integer nodeId = simulationInput.getProcessModel().getIdentifiersToNodeIds().get(identifier);
            if (nodeId == null) {
                DebugLogger.log("Simulation configuration definition for process element '" + identifier
                        + "', but not available in process, skip.");
                continue; // no matching element in process, so skip definition
            }

            List<String> costDriver = new ArrayList<>();
            for (Element element : el.getChild("costDrivers", bsimNamespace).getChildren()) {
                if (element.getName().equals("costDriver")) costDriver.add(element.getAttributeValue("id"));
            }
            costDrivers.put(nodeId, costDriver);

        }

        extensionAttributes.put("costDrivers", costDrivers);

        return extensionAttributes;
    }

}
