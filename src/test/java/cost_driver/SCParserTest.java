package cost_driver;

import de.hpi.bpt.scylla.SimulationTest;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static cost_driver.Utils.*;
import static org.junit.jupiter.api.Assertions.fail;

class SCParserTest extends SimulationTest {

    @Test
    @DisplayName("Testing Parsing CostDriver List of String")
    void testParsing_CD() throws IOException, ScyllaValidationException, JDOMException {
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        runSimpleSimulation(
                GLOBAL_CONFIGURATION_FILE,
                SIMULATION_MODEL_FILE,
                SIMULATION_CONFIGURATION_FILE);

        prepareSimulation();
        HashMap<Integer, List<String>> costDriverMap = (HashMap<Integer, List<String>>) parseSC().get("costDrivers");
        List<String> expectedCostDriverListString = costDriverMap.values()
                .stream()
                .flatMap(List::stream)
                .toList();

        SimulationConfiguration simConfig = getSimulationConfiguration();
        try {
            HashMap<Integer, ArrayList<String>> actualCostDriverMap = (HashMap<Integer, ArrayList<String>>) simConfig.getExtensionAttributes().get("cost_driver_costDrivers");

            List<String> actual = actualCostDriverMap.values()
                    .stream()
                    .flatMap(ArrayList::stream)
                    .sorted()
                    .toList();

            List<String> expected = expectedCostDriverListString.stream()
                    .sorted()
                    .toList();

            if (!actual.equals(expected)) {
                int firstMismatchIndex = -1;
                for (int i = 0; i < Math.min(actual.size(), expected.size()); i++) {
                    if (!actual.get(i).equals(expected.get(i))) {
                        firstMismatchIndex = i;
                        break;
                    }
                }
                if (firstMismatchIndex != -1) {
                    fail("Mismatch found at index " + firstMismatchIndex +
                            ". \n\t\tActual: " + actual.get(firstMismatchIndex) +
                            ", \n\t\tExpected: " + expected.get(firstMismatchIndex));
                } else {
                    fail("Lists differ in size. " +
                            "\n\tActual size: " + actual.size() +
                            ", \t\tExpected size: " + expected.size());
                }
            }
        } catch (Exception e) {
            fail("Your simulation configuration extensionsAttributes is wrong.");
        }
    }

    @Test
    @DisplayName("Testing Parsing Configuration")
    void testParsing_CV() throws IOException {
        prepareSimulation();
        var seed = CURRENT_GLOBAL_CONFIGURATION.getRandomSeed();

        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        setGlobalSeed(seed);
        runSimpleSimulation(
                GLOBAL_CONFIGURATION_FILE,
                SIMULATION_MODEL_FILE,
                SIMULATION_CONFIGURATION_FILE);


        SimulationConfiguration actualSimConfig = getSimulationConfiguration();
        try {
            CostVariantConfiguration actualConfiguration = (CostVariantConfiguration) actualSimConfig.
                    getExtensionAttributes().get("cost_driver_CostVariant");

            CostVariantConfiguration expectedConfiguration = (CostVariantConfiguration) Objects.requireNonNull(
                    parseSC()
            ).get("CostVariant");

            Stack<CostVariant> i = actualConfiguration.getCostVariantListConfigured();
            var j = expectedConfiguration.getCostVariantListConfigured();
            for (int k = 0; k < i.size(); k++) {
                List<String> actualACDList = i.get(k).getConcretisedACD().keySet().stream().toList();
                List<Double> actualCostList = i.get(k).getConcretisedACD().values().stream().toList();
                List<String> expectedACDList = j.get(k).getConcretisedACD().keySet().stream().toList();
                List<Double> expectedCostList = j.get(k).getConcretisedACD().values().stream().toList();
                if (!(actualACDList.equals(expectedACDList) && actualCostList.equals(expectedCostList))) {
                    fail("Your configured cost variants do not match the provided simulation configuration. " +
                            "\n\tActual: " + j.get(k).getId() + "\n" + actualACDList + "\n" + actualCostList +
                            "\n\tExpected: " + j.get(k).getId() + "\n" + expectedACDList + "\n" + expectedCostList);
                }
            }
        } catch (Exception e) {

            fail("");
        }
    }

    @Override
    protected String getFolderName() {
        return "Shipping";
    }
}
