package cost_driver;

import de.hpi.bpt.scylla.logger.ProcessNodeInfo;
import de.hpi.bpt.scylla.logger.ProcessNodeTransitionType;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import de.hpi.bpt.scylla.plugin_type.logger.OutputLoggerPluggable;
import de.hpi.bpt.scylla.simulation.ProcessSimulationComponents;
import de.hpi.bpt.scylla.simulation.SimulationModel;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;
import org.deckfour.xes.classification.XEventAttributeClassifier;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.extension.XExtension;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XOrganizationalExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.*;
import org.deckfour.xes.out.XesXmlGZIPSerializer;
import org.deckfour.xes.out.XesXmlSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CostDriverExecutionLoggingPlugin extends OutputLoggerPluggable {

    boolean gzipOn = false;

    @Override
    public String getName() {
        return CostDriverPluginUtils.PLUGIN_NAME;
    }

    @Override
    public void writeToLog(SimulationModel model, String outputPathWithoutExtension) throws IOException {
        Map<String, ProcessSimulationComponents> desmojObjectsMap = model.getDesmojObjectsMap();
        for (String processId : desmojObjectsMap.keySet()) {
            String fileNameWithoutExtension = model.getDesmojObjectsMap().get(processId).getCommonProcessElements()
                    .getBpmnFileNameWithoutExtension();
            ZonedDateTime baseDateTime = model.getStartDateTime();
            Map<Integer, List<ProcessNodeInfo>> nodeInfos = model.getProcessNodeInfos().get(processId);

            XFactory factory = XFactoryRegistry.instance().currentDefault();
            XLog log = factory.createLog();

            List<XExtension> extensions = new ArrayList<>();
            XLifecycleExtension lifecycleExt = XLifecycleExtension.instance();
            extensions.add(lifecycleExt);
            XOrganizationalExtension organizationalExt = XOrganizationalExtension.instance();
            extensions.add(organizationalExt);
            XTimeExtension timeExt = XTimeExtension.instance();
            extensions.add(timeExt);
            XConceptExtension conceptExt = XConceptExtension.instance();
            extensions.add(conceptExt);
            log.getExtensions().addAll(extensions);

            List<XAttribute> globalTraceAttributes = new ArrayList<>();
            globalTraceAttributes.add(XConceptExtension.ATTR_NAME);
            log.getGlobalTraceAttributes().addAll(globalTraceAttributes);

            List<XAttribute> globalEventAttributes = new ArrayList<>();
            globalEventAttributes.add(XConceptExtension.ATTR_NAME);
            globalEventAttributes.add(XLifecycleExtension.ATTR_TRANSITION);
            log.getGlobalEventAttributes().addAll(globalEventAttributes);

            List<XEventClassifier> classifiers = new ArrayList<>();
            classifiers.add(new XEventAttributeClassifier("MXML Legacy Classifier", XConceptExtension.KEY_NAME,
                    XLifecycleExtension.KEY_TRANSITION));
            classifiers.add(new XEventAttributeClassifier("Event Name", XConceptExtension.KEY_NAME));
            classifiers.add(new XEventAttributeClassifier("Resource", XOrganizationalExtension.KEY_RESOURCE));
            classifiers.add(new XEventAttributeClassifier("Event Name AND Resource", XConceptExtension.KEY_NAME,
                    XOrganizationalExtension.KEY_RESOURCE));
            classifiers.add(new XEventAttributeClassifier("Cost Driver", "cost:driver"));
            classifiers.add(new XEventAttributeClassifier("Cost Variant", "cost:variant"));
            classifiers.add(new XEventAttributeClassifier("Activity Cost", "cost:activity"));
            classifiers.add(new XEventAttributeClassifier("Process Instance Cost", "cost:Process_Instance"));
            log.getClassifiers().addAll(classifiers);

            log.getAttributes().put("source", factory.createAttributeLiteral("source", "Scylla", null));
            log.getAttributes().put(XConceptExtension.KEY_NAME,
                    factory.createAttributeLiteral(XConceptExtension.KEY_NAME, processId, conceptExt));
            log.getAttributes().put("description",
                    factory.createAttributeLiteral("description", "Log file created in Scylla", null));
            log.getAttributes().put(XLifecycleExtension.KEY_MODEL, XLifecycleExtension.ATTR_MODEL);

            //Preparation for adding <string key=”cost:variant” value=”standard procedure”/>
            SimulationConfiguration simulationConfiguration = desmojObjectsMap.get(processId).getSimulationConfiguration();
            CostVariantConfiguration costVariants = (CostVariantConfiguration) simulationConfiguration.getExtensionAttributes().get("cost_driver_CostVariant");
            Stack<CostVariant> costVariantStack = costVariants.getCostVariantListConfigured();

            /**
             * Preparation for Average Cost Calculation
             * Scenario -> List of total costs
             */
            Map<String, List<AtomicReference<Double>>> instancesCostVariant2TotalCostMap = new HashMap<>();


            /**
             * Preparation for average cost for each activity
             * task name -> scenario -> costs
             * */
            Map<String, Map<String, List<Double>>> averageCostEachActivityMap = new HashMap<>();

            /**
             * activity -> cost variant -> CCD
             * activity -> ACD
             * */
            Map<String, Map<String, List<String>>> activityCostVariantACDMap = new HashMap<>();
            Map<String, List<String>> activity2ACD = new HashMap<>();

            /**
             * Activity -> ProcessInstance ID
             */
            Map<String, Map<String, List<Integer>>> activityCostVariantProcessIDMap = new HashMap<>();

            /**
             * Preparation of writing to xml
             * */
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;
            try {
                docBuilder = docFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
            // root elements
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("Sustainability_Info");
            doc.appendChild(rootElement);

            for (Integer processInstanceId : nodeInfos.keySet()) {
                XTrace trace = factory.createTrace();
                trace.getAttributes().put(XConceptExtension.KEY_NAME, factory
                        .createAttributeLiteral(XConceptExtension.KEY_NAME, processInstanceId.toString(), conceptExt));
                /**
                 * add <string key=”cost:variant” value=”standard procedure”/>
                 * */
                CostVariant costVariant = costVariantStack.pop();
                trace.getAttributes().put("cost:variant", factory
                        .createAttributeLiteral("cost:variant", costVariant.getId(), conceptExt));

                List<ProcessNodeInfo> nodeInfoList = nodeInfos.get(processInstanceId);
                Map<String, Object> nodeID2costDriversMap = (Map<String, Object>) simulationConfiguration.getExtensionAttributes().get("cost_driver_costDrivers");

                AtomicReference<Double> totalCostPerInstance = new AtomicReference<>(0.0);

                for (ProcessNodeInfo info : nodeInfoList) {

                    XAttributeMap attributeMap = factory.createAttributeMap();

                    Set<String> resources = info.getResources();
                    for (String res : resources) {
                        attributeMap.put(res, factory.createAttributeLiteral(XOrganizationalExtension.KEY_RESOURCE, res,
                                organizationalExt));
                    }

                    ZonedDateTime zonedDateTime = baseDateTime.plus(info.getTimestamp(),
                            DateTimeUtils.getReferenceChronoUnit());
                    Date timestamp = new Date(zonedDateTime.toInstant().toEpochMilli());
                    attributeMap.put(XTimeExtension.KEY_TIMESTAMP,
                            factory.createAttributeTimestamp(XTimeExtension.KEY_TIMESTAMP, timestamp, timeExt));

                    String taskName = info.getTaskName();

                    attributeMap.put(XConceptExtension.KEY_NAME,
                            factory.createAttributeLiteral(XConceptExtension.KEY_NAME, taskName, conceptExt));

                    /**
                     * set processNodeInfo dataObjectField & add them into attributeMap
                     **/
                    Map<String, ConcreteCostDriver> concreteCostId2ObjectMap = new HashMap<>();

                    //Preparation for taskCost
                    Double taskCost = 0.0;

                    List<String> listOfCCDID = (List<String>) nodeID2costDriversMap.get(info.getId());
                    List<String> CCDId = new ArrayList<>();

                    if (listOfCCDID != null) {
                        listOfCCDID.forEach(i -> getConcreteCostDriver(model.getGlobalConfiguration(), costVariant, i).ifPresent(concretizationForDriverId -> concreteCostId2ObjectMap.put(i, concretizationForDriverId)));

                        info.SetDataObjectField(Collections.unmodifiableMap(concreteCostId2ObjectMap));

                        Map<String, Object> dataObjects = info.getDataObjectField();
                        for (String d0: dataObjects.keySet()) {
                            ConcreteCostDriver ccd = (ConcreteCostDriver) dataObjects.get(d0);
                            CCDId.add(ccd.getId());
                            taskCost += ccd.getLCAScore();

                            attributeMap.put(d0, factory.createAttributeLiteral("cost:driver", d0 + "(" + ccd.getId() + "): " + ccd.getLCAScore(),
                                    organizationalExt));
                        }
                    }

                    if (taskCost != 0.0) attributeMap.put("cost:activity", factory.createAttributeLiteral("cost:activity", String.valueOf(taskCost), organizationalExt));

                    //Add task CCD to activityCostVariantACDMap
                    if (!activityCostVariantACDMap.containsKey(taskName)) activityCostVariantACDMap.put(taskName, new HashMap<>());

                    if (!activityCostVariantACDMap.get(taskName).containsKey(costVariant.getId())) activityCostVariantACDMap.get(taskName).put(costVariant.getId(), CCDId);


                    //Add process instance id to activityCostVariantProcessInstanceID
                    if (!activityCostVariantProcessIDMap.containsKey(taskName)) activityCostVariantProcessIDMap.put(taskName, new HashMap<>());
                    if (!activityCostVariantProcessIDMap.get(taskName).containsKey(costVariant.getId())) activityCostVariantProcessIDMap.get(taskName).put(costVariant.getId(), new ArrayList<>(processInstanceId));
                    else activityCostVariantProcessIDMap.get(taskName).get(costVariant.getId()).add(processInstanceId);

                    if (!activity2ACD.containsKey(taskName)) activity2ACD.put(taskName, listOfCCDID);

                    ProcessNodeTransitionType transition = info.getTransition();
                    if (transition == ProcessNodeTransitionType.BEGIN
                            || transition == ProcessNodeTransitionType.EVENT_BEGIN) {
                        attributeMap.put(XLifecycleExtension.KEY_TRANSITION, factory
                                .createAttributeLiteral(XLifecycleExtension.KEY_TRANSITION, "start", lifecycleExt));
                    }
                    else if (transition == ProcessNodeTransitionType.TERMINATE
                            || transition == ProcessNodeTransitionType.EVENT_TERMINATE) {
                        attributeMap.put(XLifecycleExtension.KEY_TRANSITION, factory
                                .createAttributeLiteral(XLifecycleExtension.KEY_TRANSITION, "complete", lifecycleExt));

                        //Only add the task's cost to total cost until it completed
                        List<ConcreteCostDriver> listOfCCD = new ArrayList<>(concreteCostId2ObjectMap.values());
                        listOfCCD.forEach(i -> totalCostPerInstance.updateAndGet(v -> v + i.getLCAScore()));

                        if (listOfCCDID != null) {
                            //Only add the task's cost to averageCostEachActivityMap until it completed
                            if (!averageCostEachActivityMap.containsKey(taskName)) {
                                averageCostEachActivityMap.put(taskName, new HashMap<>());
                            }
                            if (!averageCostEachActivityMap.get(taskName).containsKey(costVariant.getId())) {
                                averageCostEachActivityMap.get(taskName).put(costVariant.getId(), new ArrayList<>());
                            }
                            averageCostEachActivityMap.get(taskName).get(costVariant.getId()).add(taskCost);
                        }
                    }
                    else if (transition == ProcessNodeTransitionType.CANCEL) {
                        attributeMap.put(XLifecycleExtension.KEY_TRANSITION, factory
                                .createAttributeLiteral(XLifecycleExtension.KEY_TRANSITION, "ate_abort", lifecycleExt));

                    }
                    else if (transition == ProcessNodeTransitionType.ENABLE
                            || transition == ProcessNodeTransitionType.PAUSE
                            || transition == ProcessNodeTransitionType.RESUME) {
                        continue;
                    }
                    else {
                        System.out.println("Transition type " + transition + " not supported in XESLogger.");
                    }

                    XEvent event = factory.createEvent(attributeMap);
                    trace.add(event);
                }

                /**
                 * add <string key=”total cost” value=”<LCA score>”/>
                 * */
                trace.getAttributes().put("cost:Process_Instance", factory
                        .createAttributeLiteral("cost:Process_Instance", String.valueOf(totalCostPerInstance), conceptExt));

                /**
                 * add total cost of each instances to instancesCostVariant2TotalCostMap
                 * */
                if (!instancesCostVariant2TotalCostMap.containsKey(costVariant.getId())) {
                    instancesCostVariant2TotalCostMap.put(costVariant.getId(), new ArrayList<>());
                }
                instancesCostVariant2TotalCostMap.get(costVariant.getId()).add(totalCostPerInstance);

                log.add(trace);
            }

            XesXmlSerializer serializer;
            FileOutputStream fos;

            /**
             * calculate average value of instances' total cost
             * */
            Map<String, Double> averageTotalCostMap = new HashMap<>();
            List<Double> instanceCosts = new ArrayList<>();
            for (String costVariant:instancesCostVariant2TotalCostMap.keySet()) {
                averageTotalCostMap.put(costVariant, instancesCostVariant2TotalCostMap.get(costVariant).stream().mapToDouble(i -> i.get()).average().orElse(0.0));

                Element cv = doc.createElement("Average_Cost_Variant_Cost");
                cv.setAttribute("id", costVariant.replace(' ', '_'));
                cv.setTextContent(String.valueOf(averageTotalCostMap.get(costVariant)));
                rootElement.appendChild(cv);

                //Collect all traces average cost
                for (AtomicReference<Double> d: instancesCostVariant2TotalCostMap.get(costVariant)) instanceCosts.add(d.get());
            }

            //Calculate all traces average cost and put them into xml
            Element tcv = doc.createElement("Average_Process_Instance_Cost");
            tcv.setTextContent(String.valueOf(instanceCosts.stream().mapToDouble(i -> i).average().orElse(0.0)));
            rootElement.appendChild(tcv);

            //Calculate all traces average cost per activities and put them into xml
            Element acitivityAverageCost = doc.createElement("Activity_Cost");


            //Create other element for not aggregated data
            Element individualCostPerInstance = doc.createElement("Activity_Instance_Cost");

            for (String act:averageCostEachActivityMap.keySet()) {
                Element activity = doc.createElement("Activity");
                activity.setAttribute("id", act.replace(' ', '_'));

                //Create activity cost list
                Element activityCost = doc.createElement( "Activity_Average_Cost");
                activityCost.setAttribute("id", act.replace(' ', '_'));
                List<Double> costInDifferentCostVariantEachActivity = new ArrayList<>();

                //Create individual activity cost
                Element individualActivityCost = doc.createElement("Activity");
                individualActivityCost.setAttribute("id", act.replace(' ', '_'));

                for (String scen: averageCostEachActivityMap.get(act).keySet()) {
                    Element scenario = doc.createElement("Activity_Average_Cost_Variant_Cost");
                    scenario.setAttribute("id", scen.replace(' ', '_'));
                    scenario.setTextContent(String.valueOf(averageCostEachActivityMap.get(act).get(scen).stream().mapToDouble(i -> i).average().orElse(0.0)));
                    activity.appendChild(scenario);

                    //Add cost in different costVariant with different activity into a list
                    costInDifferentCostVariantEachActivity.addAll(averageCostEachActivityMap.get(act).get(scen));

                    //Add individual cost to different activity
                    Element individualCostWithDifferentCostVariant = doc.createElement("Cost_Variant");
                    individualCostWithDifferentCostVariant.setAttribute("id", scen.replace(' ', '_'));

                    if (activity2ACD.get(act) != null) individualActivityCost.setAttribute("ACD", activity2ACD.get(act).toString().replace("[","").replace("]", ""));
                    if (activityCostVariantACDMap.get(act).get(scen) != null && !activityCostVariantACDMap.get(act).get(scen).isEmpty())  individualCostWithDifferentCostVariant.setAttribute("CCD", activityCostVariantACDMap.get(act).get(scen).toString().replace("[", "").replace("]", ""));
                    individualActivityCost.appendChild(individualCostWithDifferentCostVariant);

                    Element individualInstanceCost = doc.createElement("activity_instance_cost");
                    individualInstanceCost.setTextContent(String.valueOf(averageCostEachActivityMap.get(act).get(scen).get(0)));
                    individualInstanceCost.setAttribute("count", String.valueOf(averageCostEachActivityMap.get(act).get(scen).stream().count()));
                    individualInstanceCost.setAttribute("ProcessInstance_IDs", activityCostVariantProcessIDMap.get(act).get(scen).stream().distinct().toList().toString().replace("[","").replace("]", ""));
                    individualCostWithDifferentCostVariant.appendChild(individualInstanceCost);

                    /**
                     for (Double cost : averageCostEachActivityMap.get(act).get(scen)) {
                     Element individualInstanceCost = doc.createElement("activity_instance_cost");
                     individualInstanceCost.setTextContent(String.valueOf(cost));
                     individualCostWithDifferentCostVariant.appendChild(individualInstanceCost);
                     }
                     **/
                }
                //Add activity average cost into log under "Activity_Average_Cost"
                activityCost.setTextContent(String.valueOf(costInDifferentCostVariantEachActivity.stream().mapToDouble(i -> i).average().orElse(0.0)));
                activity.appendChild(activityCost);

                //Add individual cost into log under "Individual_Cost_Per_Instance"
                acitivityAverageCost.appendChild(activity);
                individualCostPerInstance.appendChild(individualActivityCost);
            }
            rootElement.appendChild(acitivityAverageCost);
            rootElement.appendChild(individualCostPerInstance);


            if (gzipOn) {
                serializer = new XesXmlGZIPSerializer();
                fos = new FileOutputStream(outputPathWithoutExtension + fileNameWithoutExtension +  ".tar");
            }
            else {
                serializer = new XesXmlSerializer();
                fos = new FileOutputStream(outputPathWithoutExtension + fileNameWithoutExtension + ".xes");
            };
            serializer.serialize(log, fos);
            fos.close();


            /***
             * Write to "XML" output file
             */
            try (FileOutputStream output =
                         new FileOutputStream(outputPathWithoutExtension + "sustainability_global_information_statistic.xml")) {
                writeXml(doc, output);
            } catch (IOException | TransformerException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param globalConfiguration
     * @param costVariant
     * @param abstractCostDriverId
     * @return Concrete cost driver of the given id for the given variant, if configured in the variant; empty optional instead
     */
    private Optional<ConcreteCostDriver> getConcreteCostDriver(GlobalConfiguration globalConfiguration, CostVariant costVariant, String abstractCostDriverId){
        List<AbstractCostDriver> abstractCostDrivers = (List<AbstractCostDriver>) globalConfiguration.getExtensionAttributes().get("cost_driver_costDrivers");
        AbstractCostDriver abstractCostDriver = abstractCostDrivers.stream().filter(i -> i.getId().equals(abstractCostDriverId)).findFirst().get();

        String concreteDriverId = costVariant.getConcretisedACD().get(abstractCostDriverId);
        if (concreteDriverId != null) {
            return abstractCostDriver.getChildren().stream().filter(ccd -> ccd.getId().equals(concreteDriverId)).findFirst();
        } else {
            return Optional.empty();
        }
    }

    private static void writeXml(Document document, OutputStream output) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(output);

        transformer.transform(source, result);
    }
}
