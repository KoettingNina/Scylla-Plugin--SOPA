package cost_driver;

import de.hpi.bpt.scylla.SimulationManager;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.hpi.bpt.scylla.Scylla.normalizePath;

/**
 * Utilities class providing methods to prepare and parse simulation configurations
 * and cost drivers for a shipping logistics simulation using Scylla.
 *
 * @author Mihail Atanasov - Top G-v2 from Bulgaria
 */
public class Utils {

    /**
     * Folder path where resource files are located.
     */
    public static final String RESOURCE_FOLDER = normalizePath("src\\test\\resources");

    /**
     * Name of the test folder within the resource folder to use for simulation.
     */
    public static final String TEST_FOLDER = "Shipping";

    /**
     * Full path to the test folder where simulation files are located.
     */
    public static final String TEST_PATH = normalizePath(RESOURCE_FOLDER + "\\" + TEST_FOLDER + "\\");

    /**
     * Name of the BPMN model file for the simulation.
     */
    public static final String SIMULATION_MODEL_FILE = "logistics_model_no_drivers.bpmn";

    /**
     * Name of the file containing simulation configurations.
     */
    public static final String SIMULATION_CONFIGURATION_FILE = "logistics_sim.xml";

    /**
     * Name of the file containing global configurations for the simulation.
     */
    public static final String GLOBAL_CONFIGURATION_FILE = "logistics_global.xml";

    /**
     * Current active simulation manager instance.
     */
    public static SimulationManager CURRENT_SIMULATION_MANAGER;

    /**
     * Current global configuration for the simulation.
     */
    public static GlobalConfiguration CURRENT_GLOBAL_CONFIGURATION;

    /**
     * List of current simulation configurations loaded.
     */
    public static List<SimulationConfiguration> CURRENT_SIMULATION_CONFIGURATIONS = new ArrayList<>();


    /**
     * Prepares the simulation environment by initializing the simulation manager,
     * loading global configurations, and loading simulation configurations.
     *
     * @throws IOException If there is an issue accessing the simulation files.
     */
    public static void prepareSimulation() throws IOException {
        ScyllaScripts.runMoockModels();

        if (ScyllaScripts.manager != null) {
            CURRENT_SIMULATION_MANAGER = ScyllaScripts.manager;
            CURRENT_GLOBAL_CONFIGURATION = ScyllaScripts.manager.getGlobalConfiguration();

            //Check if there are any Configurations
            if (!ScyllaScripts.manager.getSimulationConfigurations().isEmpty()) {
                var simConfigs = ScyllaScripts.manager.getSimulationConfigurations();
                var keys = simConfigs.keySet();
                for (var key : keys) {
                    CURRENT_SIMULATION_CONFIGURATIONS.add(simConfigs.get(key));
                }
            } else {
                throw new NullPointerException("Not a proper Simulation Configuration in your file.");
            }

            if (CURRENT_SIMULATION_CONFIGURATIONS.isEmpty()) {
                throw new IndexOutOfBoundsException("There were not any Simulation Configurations parsed.");
            }
        } else {
            throw new NullPointerException("Scylla scripts did not run a simulation.");
        }
    }


    /**
     * Parses the global configuration file to extract cost driver information.
     *
     * @return A list of {@link AbstractCostDriver} instances representing the cost drivers defined in the global configuration.
     * @throws IOException               If there is an issue reading the global configuration file.
     * @throws JDOMException             If there is an issue parsing the XML content of the global configuration file.
     * @throws ScyllaValidationException If instantiating ConcreteCostDriver violate any expected constraints or validations.
     */
    public static ArrayList<AbstractCostDriver> parseGC() throws IOException, JDOMException, ScyllaValidationException {
        SAXBuilder builder = new SAXBuilder();
        //Specify your Global Configuration file
        Document gcDoc = builder.build(normalizePath(TEST_PATH + GLOBAL_CONFIGURATION_FILE));
        Element simulation = gcDoc.getRootElement().getChildren(
                        "costDriver", gcDoc.getRootElement().getNamespace()
                )
                .get(0);

        ArrayList<AbstractCostDriver> abstractCostDriverArrayList = new ArrayList<>();

        for (Element el : simulation.getChildren()) { //parse abstract cost drivers
            String id = el.getAttributeValue("id");

            AbstractCostDriver abstractCostDriver = new AbstractCostDriver(id, new ArrayList<>());

            for (Element child : el.getChildren()) { //parse concrete cost drivers
                String chileId = child.getAttributeValue("id");
                Double LCAScore = Double.valueOf(child.getAttributeValue("cost"));

                ConcreteCostDriver costDriver = new ConcreteCostDriver(chileId, abstractCostDriver, LCAScore);
                abstractCostDriver.addChild(costDriver);
            }
            abstractCostDriverArrayList.add(abstractCostDriver);
        }

        return abstractCostDriverArrayList;
    }


    /**
     * Parses simulation configurations from an XML file, extracting cost variants and cost drivers.
     * Validates the total frequency of cost variants equals 1 and associates cost drivers with their
     * node IDs in the process model. Constructs and returns a map with detailed configuration for
     * simulation execution.
     *
     * @return A map containing "CostVariant" and "costDrivers" keys with their respective configurations.
     *         "CostVariant" maps to a CostVariantConfiguration object, and "costDrivers" maps to a map
     *         of node IDs to lists of cost driver IDs.
     * @throws IOException If an error occurs reading the simulation configuration file.
     * @throws JDOMException If parsing the XML content encounters errors.
     * @throws ScyllaValidationException If the cost variants' frequencies do not total to 1, or other
     *         validation issues occur.
     */
    public static Map<String, Object> parseSC() throws IOException, JDOMException, ScyllaValidationException {
        if (!CURRENT_SIMULATION_CONFIGURATIONS.isEmpty()) {
            for (var simulationInput : CURRENT_SIMULATION_CONFIGURATIONS) {
                SAXBuilder builder = new SAXBuilder();
                //Specify your Global Configuration file
                Document gcDoc = builder.build(normalizePath(TEST_PATH + SIMULATION_CONFIGURATION_FILE));

                Element costVariantConfigParent = gcDoc.getRootElement().getChildren(
                        "simulationConfiguration", gcDoc.getRootElement().getNamespace()
                ).get(0);

                Element costVariantConfig = costVariantConfigParent.getChildren(
                        "costVariantConfig", gcDoc.getRootElement().getNamespace()
                ).get(0);

                Integer count = Integer.valueOf(costVariantConfigParent.getAttributeValue("processInstances"));

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

                CostVariantConfiguration costVariantConfiguration = new CostVariantConfiguration(count, costVariantList, simulationInput.getRandomSeed());
                extensionAttributes.put("CostVariant", costVariantConfiguration);

                /**
                 * Parse Concretised abstract cost drivers in tasks
                 */
                Map<Integer, List<String>> costDrivers = new HashMap<>();
                // get all cost drivers
                List<Element> elements = costVariantConfigParent.getChildren().stream().filter(
                                c -> c.getChild("costDrivers", gcDoc.getRootElement().getNamespace()) != null
                        )
                        .toList();

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
                    for (Element element : el.getChild("costDrivers", gcDoc.getRootElement().getNamespace()).getChildren()) {
                        if (element.getName().equals("costDriver")) costDriver.add(element.getAttributeValue("id"));
                    }
                    costDrivers.put(nodeId, costDriver);

                }

                extensionAttributes.put("costDrivers", costDrivers);

                return extensionAttributes;
            }
        } else {
            throw new IndexOutOfBoundsException("There were not any Simulation Configurations parsed.");
        }
        return null;
    }

}
