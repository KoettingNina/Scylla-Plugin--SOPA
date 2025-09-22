package de.tum.insm.scylla.plugin.sopa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;
import org.jdom2.Namespace;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import de.hpi.bpt.scylla.plugin_type.parser.GlobalConfigurationParserPluggable;

//This is an example Global Configuration Parser of "hiring_process_global"

public class CostDriverGCParserPlugin extends GlobalConfigurationParserPluggable {
    @Override
    public String getName() {
        return CostDriverPluginUtils.PLUGIN_NAME;
    }

    @Override
    public Map<String, Object> parse(GlobalConfiguration simulationInput, Element sim)
            throws ScyllaValidationException {

        Namespace bsimNamespace = sim.getNamespace();
        Element costDrivers = sim.getChildren("costDriver", bsimNamespace).get(0);

        // Read impact method info
        Element impactMethodInfo = sim.getChild("impactMethodInfo", bsimNamespace);
        String selectedImpactMethod = null;
        String selectedNormalizationSet = null;
        
        if (impactMethodInfo != null) {
            selectedImpactMethod = impactMethodInfo.getAttributeValue("selectedImpactMethod");
            selectedNormalizationSet = impactMethodInfo.getAttributeValue("selectedNormalizationSet");
                        
            // Set global settings
            CostDriverSettings settings = CostDriverSettings.getInstance();
            settings.setImpactMethod(selectedImpactMethod);
            settings.setNormalizationSet(selectedNormalizationSet);
        } else {
            System.err.println("  WARNING: No impactMethodInfo element found in XML!");
        }

        List<CostDriver> abstractCostDrivers = new ArrayList<>();
        for (Element el: costDrivers.getChildren()) { //parse abstract cost drivers
            String id = el.getAttributeValue("id");

            AbstractCostDriver abstractCostDriver = new AbstractCostDriver(id, new ArrayList<>());

            for (Element child: el.getChildren()) { //parse concrete cost drivers
                String chileId = child.getAttributeValue("id");
                Double LCAScore = Double.valueOf(child.getAttributeValue("cost"));
                ConcreteCostDriver costDriver = new ConcreteCostDriver(chileId, abstractCostDriver, LCAScore);                
                abstractCostDriver.addChild(costDriver);
            }
            abstractCostDrivers.add(abstractCostDriver);
        }
        HashMap<String, Object> extensionAttributes = new HashMap<>();
        extensionAttributes.put("costDrivers", abstractCostDrivers);
        
        return extensionAttributes;
    }

}
