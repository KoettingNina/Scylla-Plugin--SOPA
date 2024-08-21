package cost_driver;

import java.util.*;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.plugin_type.parser.SimulationConfigurationParserPluggable;
import org.jdom2.Element;
import org.jdom2.Namespace;

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

            Map<String, Double> concretisedACD = new HashMap<>();

            for (Element driver : variant.getChildren()) {
                String CID = driver.getAttributeValue("id");
                Double cost = Double.valueOf(driver.getAttributeValue("cost"));

                concretisedACD.put(CID, cost);
            }

            CostVariant costVariant = new CostVariant(id, frequency, concretisedACD);
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
