package cost_driver;

import de.hpi.bpt.scylla.SimulationTest;
import de.hpi.bpt.scylla.exception.ScyllaRuntimeException;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static cost_driver.Utils.*;
import static de.hpi.bpt.scylla.Scylla.normalizePath;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


class ExecutionLoggingPluginTest extends SimulationTest {

    private final CostDriverExecutionLoggingPlugin EXECUTION_LOGGING_PLUGIN = new CostDriverExecutionLoggingPlugin();
    private List<AbstractCostDriver> TEST_ABSTRACT_DRIVER_LIST = new LinkedList<>();
    private GlobalConfiguration TEST_GLOBAL_CONFIG;
    private CostVariant TEST_COST_VARIANT;

    @Test
    void testWriteToLog() throws IOException {
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());
        setGlobalSeed(-7870005462812540457L);
        runSimpleSimulation(
                GLOBAL_CONFIGURATION_FILE,
                SIMULATION_MODEL_FILE,
                SIMULATION_CONFIGURATION_FILE);

        String expectedFileNameXML = normalizePath("./" + outputPath + "sustainability_global_information_statistic.xml");
        String expectedFileNameXES = normalizePath("./" + outputPath + SIMULATION_MODEL_FILE);
        Path filePathXML = Paths.get(expectedFileNameXML);
        Path filePathXES = Paths.get(expectedFileNameXES.substring(0, expectedFileNameXES.lastIndexOf('.')).concat(".xes"));

        // Read the content of both XML files as strings
        String actualXML = new String(Files.readAllBytes(filePathXML));
        String expectedXML = new String(Files.readAllBytes(Paths.get(normalizePath("./" + TEST_PATH + "/cost_driver_output/" + "sustainability_global_information_statistic.xml"))));

        // Read the content of both XES files as strings
        String actualXES = new String(Files.readAllBytes(filePathXES));
        String expectedXES = new String(Files.readAllBytes(Paths.get(normalizePath("./" + TEST_PATH + "/cost_driver_output/" + "logistics_model_no_drivers.xes"))));

        // Compare the XML files
        Diff diffXML = DiffBuilder.compare(actualXML).withTest(expectedXML)
                .ignoreWhitespace() // Ignores white spaces
                .checkForSimilar() // Use similar to compare not identical (handles cases like attribute order)
                .build();

        // Compare the XES files
        Diff diffXES = DiffBuilder.compare(actualXES).withTest(expectedXES)
                .ignoreWhitespace() // Ignores white spaces
                .checkForSimilar() // Use similar to compare not identical (handles cases like attribute order)
                .build();

        // Check if there are differences
        if (diffXML.hasDifferences()) {
            fail("Difference found: " + diffXML.getDifferences());
        }
        if (diffXES.hasDifferences()) {
            fail("Difference found: " + diffXES.getDifferences());
        }

    }

    @Test
    @DisplayName("File extension with gzipOn = false")
    void testCorrectFileExtensionXES() throws IOException {
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        runSimpleSimulation(
                GLOBAL_CONFIGURATION_FILE,
                SIMULATION_MODEL_FILE,
                SIMULATION_CONFIGURATION_FILE);
        // By default is false
        EXECUTION_LOGGING_PLUGIN.gzipOn = false;

        String expectedFileName = normalizePath("./" + outputPath + SIMULATION_MODEL_FILE);
        Path filePath = Paths.get(expectedFileName.substring(0, expectedFileName.lastIndexOf('.')).concat(".xes"));
        assertTrue(Files.exists(filePath), "XES File does not exist: " + filePath);
        assertTrue(filePath.toString().endsWith(".xes"), "File extension is not .xes: " + filePath);
    }

    @Test
    @DisplayName("Find Concrete Cost Driver")
    void testFindConcreteCaseByCost_Integration() throws IOException {
        ScyllaScripts.runMoockModels();
        CostDriverExecutionLoggingPlugin testSubject = new CostDriverExecutionLoggingPlugin();

        // Integrate the Configurations
        TEST_GLOBAL_CONFIG = ScyllaScripts.manager.getGlobalConfiguration();

        // Integrate the ACDs
        TEST_ABSTRACT_DRIVER_LIST = (List<AbstractCostDriver>) TEST_GLOBAL_CONFIG.getExtensionAttributes().get("cost_driver_costDrivers");
        prepareCostVariant();

        String firstACD = TEST_ABSTRACT_DRIVER_LIST.get(0).getId();
        String secondACD = TEST_ABSTRACT_DRIVER_LIST.get(1).getId();

        try {
            Method findConcreteCaseByCost = testSubject.getClass().getDeclaredMethod("findConcreteCaseByCost", new Class[]{GlobalConfiguration.class, CostVariant.class, String.class});
            findConcreteCaseByCost.setAccessible(true);
            ConcreteCostDriver actualCCD = (ConcreteCostDriver) findConcreteCaseByCost.invoke(
                    testSubject, TEST_GLOBAL_CONFIG, TEST_COST_VARIANT, firstACD);

            if (!Objects.equals(actualCCD.getLCAScore(), TEST_ABSTRACT_DRIVER_LIST.get(0).getChildren().get(2).getLCAScore())) {
                fail("The returned Concrete Cost Driver is incorrect, expected the CCD object with the LCAScore - 0.00002843");
            }
        } catch (ScyllaRuntimeException | NoSuchMethodException | InvocationTargetException |
                 IllegalAccessException e) {
//            var pause = e.getCause().getClass().getSimpleName();
            fail("No exception should be thrown for the valid CCD and cost.");
        }

        try {
            Method findConcreteCaseByCost = testSubject.getClass().getDeclaredMethod("findConcreteCaseByCost", new Class[]{GlobalConfiguration.class, CostVariant.class, String.class});
            findConcreteCaseByCost.setAccessible(true);
            ConcreteCostDriver actualCCD = (ConcreteCostDriver) findConcreteCaseByCost.invoke(
                    testSubject, TEST_GLOBAL_CONFIG, TEST_COST_VARIANT, secondACD);
            fail("The ScyllaRuntimeException must be thrown for the invalid CCD.");
        } catch (ScyllaRuntimeException | NoSuchMethodException | InvocationTargetException |
                 IllegalAccessException e) {
            if (!(e.getCause() instanceof ScyllaRuntimeException)) {
                fail("ScyllaRuntimeException is thrown for a valid CCD.");
            }
        }
    }

    private void prepareCostVariant() {
        // Create CostVariant
        Map<String, Double> testConcretisedACD = new HashMap<>();

        // Valid LCAScore
        testConcretisedACD.put("Delivery", 0.00002843);

        // Invalid LCAScore
        testConcretisedACD.put(TEST_ABSTRACT_DRIVER_LIST.get(1).getId(), Math.pow(new Random().nextDouble(), Math.pow(10, -5)));


        // Instantiate new CostVariant
        TEST_COST_VARIANT = new CostVariant("testID", 0.2, testConcretisedACD);
    }

    @Override
    protected String getFolderName() {
        return "Shipping";
    }
}
