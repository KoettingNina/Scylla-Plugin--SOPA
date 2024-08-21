package de.tum.insm.scylla.plugin.sopa;

import de.hpi.bpt.scylla.SimulationTest;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SCParserTest extends SimulationTest {

    static CostVariantConfiguration expectedConfiguration = Utils.getDefaultCostVariantConfiguration();

    @Test
    @DisplayName("Testing Parsing CostDrivers per Activity")
    void testParsingActivityCostDrivers() throws IOException, ScyllaValidationException, JDOMException {
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        runSimpleSimulation(
                Utils.GLOBAL_CONFIGURATION_FILE,
                Utils.SIMULATION_MODEL_FILE,
                Utils.SIMULATION_CONFIGURATION_FILE);

        //TODO insert proper fixture
        Map<String, Integer>  identifiersToNodeIds = simulationManager.getProcessModels().get("Process_0vv8a1n").getIdentifiersToNodeIds();
        var costDriverMapByElementId = Map.of(
                identifiersToNodeIds.get("Activity_0e1w0fd"), List.of("Packaging Material", "Filling Material"),
                identifiersToNodeIds.get("Activity_1s4kdkl"), List.of("Shipment"),
                identifiersToNodeIds.get("Activity_0zhmejb"), List.of("Delivery"),
                identifiersToNodeIds.get("Activity_192mexg"), List.of("Delivery"),
                //identifiersToNodeIds.get("Activity_1gpudom"), List.of(),
                identifiersToNodeIds.get("Activity_0rem6vo"), List.of("Receipt"),
                identifiersToNodeIds.get("Activity_0y7dygl"), List.of("Re-Routing")
        );


        HashMap<Integer, ArrayList<String>> actualCostDriverMap = (HashMap<Integer, ArrayList<String>>) getSimulationConfiguration().getExtensionAttributes().get("cost_driver_costDrivers");

        assertEquals(costDriverMapByElementId, actualCostDriverMap);
    }

    @Test
    @DisplayName("Testing Parsing Variants")
    void testParsingVariants() throws IOException, ScyllaValidationException, JDOMException {
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        setGlobalSeed(Utils.DEFAULT_SEED);

        afterParsing(() -> { //Needs to be run directly after parsing, as stack of calculated variants is consumed during simulation
            SimulationConfiguration actualSimConfig = getSimulationConfiguration();
            CostVariantConfiguration actualConfiguration = (CostVariantConfiguration) actualSimConfig.
                    getExtensionAttributes().get("cost_driver_CostVariant");



            Stack<CostVariant> actualConfiguredVariants = actualConfiguration.getCostVariantListConfigured();
            Stack<CostVariant> expectedConfiguredVariants = expectedConfiguration.getCostVariantListConfigured();
            assertEquals(expectedConfiguredVariants.size(), actualConfiguredVariants.size(), "Configured amount of variants is not as expected");
            for (int k = 0; k < actualConfiguredVariants.size(); k++) {
                assertEquals(expectedConfiguredVariants.get(k).getConcretisedACD(), actualConfiguredVariants.get(k).getConcretisedACD());
            }
        });

        runSimpleSimulation(
                Utils.GLOBAL_CONFIGURATION_FILE,
                Utils.SIMULATION_MODEL_FILE,
                Utils.SIMULATION_CONFIGURATION_FILE);
    }

    @Override
    protected String getFolderName() {
        return "Shipping";
    }
}
