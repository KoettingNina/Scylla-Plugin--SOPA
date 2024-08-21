package cost_driver;

import de.hpi.bpt.scylla.SimulationManager;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static cost_driver.Utils.*;

/**
 * This class contains various useful scripts for the programmatic usage of scylla.<br>
 * These are results from previous usages of the system
 *
 * @author Leon Bein
 */
public class ScyllaScripts {
    public static SimulationManager manager;

    public static void main(String[] args) throws IOException {
        runMoockModels();
    }

    public static void runMoockModels() throws IOException {
        if (manager != null) {
            throw new RuntimeException("The SimulationManager is already initialized once");
        }
        PluginLoader.getDefaultPluginLoader().activateNone().loadPackage(Main.class.getPackageName());

        int[] clerkCountsToTest = new int[]{4};
        int numInstances = 10;
        String globalConf = TEST_PATH + GLOBAL_CONFIGURATION_FILE;
        String model = TEST_PATH + SIMULATION_MODEL_FILE;
        String simConf = TEST_PATH + SIMULATION_CONFIGURATION_FILE;

        try {
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(simConf);
            Element root = doc.getRootElement();
            root.getDescendants(new ElementFilter("simulationConfiguration")).
                    forEach(node -> node.setAttribute("processInstances", "" + numInstances));
            FileOutputStream fos = new FileOutputStream(simConf);
            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat(Format.getPrettyFormat());
            xmlOutput.output(doc, fos);
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
        }

        for (int numClerks : clerkCountsToTest) {
            try {
                SAXBuilder builder = new SAXBuilder();
                Document doc = builder.build(globalConf);
                Element root = doc.getRootElement();
                List<Element> resources = new ArrayList<>();
                root.getDescendants(new ElementFilter("dynamicResource")).forEach(resources::add);
                resources.forEach(resource -> {
                    resource.setAttribute("defaultQuantity", "" + numClerks);
                });

                FileOutputStream fos = new FileOutputStream(globalConf);
                XMLOutputter xmlOutput = new XMLOutputter();
                xmlOutput.setFormat(Format.getPrettyFormat());
                xmlOutput.output(doc, fos);

            } catch (JDOMException | IOException e) {
                e.printStackTrace();
            }

                runSimulation(
                        globalConf,
                        model,
                        simConf,
                        TEST_PATH + numClerks + "_" + numInstances + "_" + new SimpleDateFormat("yy_MM_dd_HH_mm_ss_SSS").format(new Date()) + "/");

        }
    }

    public static void runSimulation(String global, String bpmn, String sim, String outputPath) {
        manager = new SimulationManager(null,
                new String[]{bpmn},
                new String[]{sim},
                global,
                true,
                false);
        if (Objects.nonNull(outputPath)) manager.setOutputPath(outputPath);
        manager.run();
    }


}
