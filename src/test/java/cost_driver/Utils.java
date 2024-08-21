package cost_driver;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
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


    public static final long DEFAULT_SEED = 34634532123243L;



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


    public static CostVariantConfiguration getDefaultCostVariantConfiguration() {

        try {
             return new CostVariantConfiguration(
                    10,
                    List.of(
                            new CostVariant(
                                    "Shipment and delivery over distance A",
                                    0.2,
                                    Map.of(
                                            "Delivery", "Delivery_A_Lorry",
                                            "Filling Material", "Filling_A",
                                            "Packaging Material", "Packaging_Material_B",
                                            "Re-Routing", "Re-Routing_A_Lorry",
                                            "Receipt", "Receipt",
                                            "Shipment", "Shipment_A_Lorry"
                                    )
                            ),
                            new CostVariant(
                                    "Shipment and delivery over distance A_Electric",
                                    0.05,
                                    Map.of(
                                            "Delivery", "Delivery_A_Lorry",
                                            "Filling Material", "Filling_A",
                                            "Packaging Material", "Packaging_Material_B",
                                            "Re-Routing", "Re-Routing_A_Lorry",
                                            "Receipt", "Receipt",
                                            "Shipment", "Shipment_A_Rail_Electric"
                                    )
                            ),
                            new CostVariant(
                                    "Shipment and delivery over distance B",
                                    0.5,
                                    Map.of(
                                            "Delivery", "Delivery_B_Lorry",
                                            "Filling Material", "Filling_A",
                                            "Packaging Material", "Packaging_Material_B",
                                            "Re-Routing", "Re-Routing_A_Lorry",
                                            "Receipt", "Receipt",
                                            "Shipment", "Shipment_A_Lorry"
                                    )
                            ),
                            new CostVariant(
                                    "Shipment and delivery over distance B",
                                    0.25,
                                    Map.of(
                                            "Delivery", "Delivery_B_Lorry",
                                            "Filling Material", "Filling_A",
                                            "Packaging Material", "Packaging_Material_B",
                                            "Re-Routing", "Re-Routing_A_Lorry",
                                            "Receipt", "Receipt",
                                            "Shipment", "Shipment_B_Rail_Electric"

                                    )
                            )
                    ),
                     DEFAULT_SEED
            );
        } catch (ScyllaValidationException e) {
            throw new RuntimeException(e);
        }
    }
}
