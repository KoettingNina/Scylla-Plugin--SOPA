package cost_driver;

import de.hpi.bpt.scylla.SimulationTest;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InvalidClassException;
import java.util.ArrayList;
import java.util.List;

import static cost_driver.Utils.*;
import static org.junit.jupiter.api.Assertions.fail;

//@SuppressWarnings("unchecked")
class GCParserTest extends SimulationTest {

    @Test
    @DisplayName("GC Parser")
    void testParse_GC() throws IOException, ScyllaValidationException, JDOMException {
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        runSimpleSimulation(
                GLOBAL_CONFIGURATION_FILE,
                SIMULATION_MODEL_FILE,
                SIMULATION_CONFIGURATION_FILE);

        // Integrate the ACDs
        Object obj = getGlobalConfiguration().getExtensionAttributes().get("cost_driver_costDrivers");

        if (obj instanceof ArrayList<?> list) {
            if (list.stream().allMatch(element -> element instanceof AbstractCostDriver)) {
                List<AbstractCostDriver> abstractCostDriverList = (ArrayList<AbstractCostDriver>) list;
                var expected = parseGC();
                for (int i = 0; i < abstractCostDriverList.size(); i++) {
                    if (!abstractCostDriverList.get(i).equals(expected.get(i))) {
                        fail("\nWrongly parsed ACD: " +
                                "\nActual: " + abstractCostDriverList.get(i).toString() +
                                "\nExpected: " + expected.get(i).toString()
                        );
                    }
                }
            } else {
                throw new InvalidClassException("Not all elements in the list are of type AbstractCostDriver.");
            }
        } else {
            throw new ClassCastException("The object is not a ArrayList. Cannot cast to ArrayList<AbstractCostDriver>." +
                    "The Object is: " + obj.getClass());
        }

    }

    @Override
    protected String getFolderName() {
        return "Shipping";
    }
}
