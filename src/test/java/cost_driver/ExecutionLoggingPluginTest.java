package cost_driver;

import de.hpi.bpt.scylla.SimulationTest;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.IOException;
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
        Diff diffXML = DiffBuilder.compare(expectedXML).withTest(actualXML)
                .ignoreWhitespace() // Ignores white spaces
                .checkForSimilar() // Use similar to compare not identical (handles cases like attribute order)
                .build();

        // Compare the XES files
        Diff diffXES = DiffBuilder.compare(expectedXES).withTest(actualXES)
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



    @Override
    protected String getFolderName() {
        return "Shipping";
    }
}
