package de.tum.insm.scylla.plugin.sopa;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.deckfour.xes.classification.XEventAttributeClassifier;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.extension.XExtension;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XOrganizationalExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.out.XesXmlGZIPSerializer;
import org.deckfour.xes.out.XesXmlSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.hpi.bpt.scylla.logger.ProcessNodeInfo;
import de.hpi.bpt.scylla.logger.ProcessNodeTransitionType;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import de.hpi.bpt.scylla.plugin_type.logger.OutputLoggerPluggable;
import de.hpi.bpt.scylla.simulation.ProcessSimulationComponents;
import de.hpi.bpt.scylla.simulation.SimulationModel;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;
import desmoj.core.dist.ContDistErlang;
import desmoj.core.dist.ContDistExponential;
import desmoj.core.dist.ContDistNormal;
import desmoj.core.dist.ContDistTriangular;
import desmoj.core.dist.ContDistUniform;
import desmoj.core.dist.DiscreteDistBinomial;
import desmoj.core.dist.DiscreteDistEmpirical;
import desmoj.core.simulator.Model;

public class CostDriverExecutionLoggingPlugin extends OutputLoggerPluggable {

    boolean gzipOn = false;

    
    private final Map<String, Map<String, Double>> processInstanceCostCache = new HashMap<>();
    
    private Stack<CostVariant> costVariantStack = new Stack<>();

    private final Map<String, Double> distributionValueCache = new HashMap<>();

    private final Random random = new Random();
    
    private Model desmojModel;

    @Override
    public String getName() {
        return CostDriverPluginUtils.PLUGIN_NAME;
    }

    @Override
    public void writeToLog(SimulationModel model, String outputPathWithoutExtension) throws IOException {
        // Setze das DESMO-J Model für die Verteilungsfunktionen
        this.desmojModel = model;
        // Clear all caches 
        ConcreteCostDriver.clearCostCache();
        processInstanceCostCache.clear();
        distributionValueCache.clear();
        costVariantStack.clear();

        // Get impact method and normalization set 
        CostDriverSettings settings = CostDriverSettings.getInstance();
        String impactMethodId = settings.getImpactMethod();
        String normalizationSetId = settings.getNormalizationSet();

        if (impactMethodId == null) {
            System.err.println("Warning: No impact method set in CostDriverSettings. Using XML values for costs.");
        }

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
                // Initialize cost cache for this process instance
                processInstanceCostCache.put(processInstanceId.toString(), new HashMap<>());

                XTrace trace = factory.createTrace();
                trace.getAttributes().put(XConceptExtension.KEY_NAME, factory
                        .createAttributeLiteral(XConceptExtension.KEY_NAME, processInstanceId.toString(), conceptExt));
                /**
                 * add <string key=”cost:variant” value=”standard procedure”/>
                 * */
                CostVariant costVariant = costVariantStack.pop();
                // Update our costVariantStack with the current variant
                this.costVariantStack.push(costVariant);
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
                    AtomicReference<Double> taskCostRef = new AtomicReference<>(0.0);

                    List<String> listOfCCDID = (List<String>) nodeID2costDriversMap.get(info.getId());
                    List<String> CCDId = new ArrayList<>();

                    if (listOfCCDID != null) {
                        listOfCCDID.forEach(i -> {
                            getConcreteCostDriver(model.getGlobalConfiguration(), costVariant, i).ifPresent(concretizationForDriverId -> {
                                concreteCostId2ObjectMap.put(i, concretizationForDriverId);
                                
                                // Get cached cost for this driver in this process instance
                                String cacheKey = processInstanceId + "_" + i;
                                Double cachedCost = processInstanceCostCache.get(processInstanceId.toString()).get(cacheKey);
                                
                                if (cachedCost != null) {
                                    taskCostRef.updateAndGet(v -> v + cachedCost);
                                } else {
                                    double calculatedCost = calculateCostForDriver(
                                        concretizationForDriverId,
                                        impactMethodId,
                                        normalizationSetId,
                                        cacheKey,
                                        processInstanceId.toString()
                                    );
                                    taskCostRef.updateAndGet(v -> v + calculatedCost);
                                }
                                
                                CCDId.add(i);
                            });
                        });

                        info.SetDataObjectField(Collections.unmodifiableMap(concreteCostId2ObjectMap));

                        Map<String, Object> dataObjects = info.getDataObjectField();
                        for (String d0: dataObjects.keySet()) {
                            ConcreteCostDriver ccd = (ConcreteCostDriver) dataObjects.get(d0);
                            CCDId.add(ccd.getId());
                            
                            // Get the calculated cost for this driver
                            String cacheKey = processInstanceId + "_" + d0;
                            Double calculatedCost = processInstanceCostCache.get(processInstanceId.toString()).get(cacheKey);
                            
                            // If no calculated cost is found in cache, calculate it now
                            if (calculatedCost == null) {
                                calculatedCost = calculateCostForDriver(
                                    ccd,
                                    impactMethodId,
                                    normalizationSetId,
                                    cacheKey,
                                    processInstanceId.toString()
                                );
                            }
                            
                            // Use the calculated cost in the attribute
                            attributeMap.put(d0, factory.createAttributeLiteral("cost:driver", 
                                d0 + "(" + ccd.getId() + "): " + calculatedCost,
                                organizationalExt));
                        }
                    }

                    //if (taskCostRef.get() != 0.0) attributeMap.put("cost:activity", factory.createAttributeLiteral("cost:activity", String.valueOf(taskCostRef.get()), organizationalExt));

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

                                if (taskCostRef.get() != 0.0) attributeMap.put("cost:activity", factory.createAttributeLiteral("cost:activity", String.valueOf(taskCostRef.get()), organizationalExt));

                        //Only add the task's cost to total cost until it completed
                        List<ConcreteCostDriver> listOfCCD = new ArrayList<>(concreteCostId2ObjectMap.values());
                        // listOfCCD.forEach(i -> totalCostPerInstance.updateAndGet(v -> v + i.getLCAScore()));
                        listOfCCD.forEach(i -> {
                            String cacheKey = processInstanceId + "_" + i.getId();
                            AtomicReference<Double> calculatedCostRef = new AtomicReference<>(processInstanceCostCache.get(processInstanceId.toString()).get(cacheKey));
                            if (calculatedCostRef.get() == null) {
                                calculatedCostRef.set(calculateCostForDriver(
                                    i,
                                    impactMethodId,
                                    normalizationSetId,
                                    cacheKey,
                                    processInstanceId.toString()
                                ));
                            }
                            totalCostPerInstance.updateAndGet(v -> v + calculatedCostRef.get());
                        });

                        if (listOfCCDID != null) {
                            //Only add the task's cost to averageCostEachActivityMap until it completed
                            if (!averageCostEachActivityMap.containsKey(taskName)) {
                                averageCostEachActivityMap.put(taskName, new HashMap<>());
                            }
                            if (!averageCostEachActivityMap.get(taskName).containsKey(costVariant.getId())) {
                                averageCostEachActivityMap.get(taskName).put(costVariant.getId(), new ArrayList<>());
                            }
                            averageCostEachActivityMap.get(taskName).get(costVariant.getId()).add(taskCostRef.get());
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
                        .createAttributeLiteral("cost:Process_Instance", String.valueOf(totalCostPerInstance.get()), conceptExt));

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

    
    
   
    /**
     * DESMO-J based exponential distribution sampling
     */
    protected double generateExponentialValueDesmoj(double mean) {
        if (mean <= 0) {
            throw new IllegalArgumentException("Mean must be positive for exponential distribution");
        }
        
        if (desmojModel == null) {
            System.err.println("Warning: DESMO-J model not initialized");
            
        }
        
        try {
            ContDistExponential exponentialDist = new ContDistExponential(desmojModel, "exponential", mean, false, false);
            return exponentialDist.sample();
        } catch (Exception e) {
            throw new RuntimeException("Error using DESMO-J exponential distribution " + e.getMessage(), e);
            
        }
    }

    /**
     * DESMO-J based normal distribution sampling
     */
    protected double generateNormalValueDesmoj(double mean, double standardDeviation) {
        if (standardDeviation < 0) {
            throw new IllegalArgumentException("Standard deviation must be non-negative for normal distribution");
        }
        
        if (desmojModel == null) {
            System.err.println("Warning: DESMO-J model not initialized");
            
        }
        
        try {
            ContDistNormal normalDist = new ContDistNormal(desmojModel, "normal", mean, standardDeviation, false, false);
            return normalDist.sample();
        } catch (Exception e) {
            throw new RuntimeException("Error using DESMO-J normal distribution " + e.getMessage(), e);
            
        }
    }

    /**
     * DESMO-J based uniform distribution sampling
     */
    protected double generateUniformValueDesmoj(double lower, double upper) {
        if (lower > upper) {
            throw new IllegalArgumentException("Lower bound must be less than or equal to upper bound for uniform distribution");
        }
        if (lower == upper) {
            return lower;
        }
        
        if (desmojModel == null) {
            System.err.println("Warning: DESMO-J model not initialized, falling back to manual implementation");
            
        }
        
        try {
            ContDistUniform uniformDist = new ContDistUniform(desmojModel, "uniform", lower, upper, false, false);
            return uniformDist.sample();
        } catch (Exception e) {
            throw new RuntimeException("Error using DESMO-J uniform distribution " + e.getMessage(), e);
            
        }
    }

    /**
     * DESMO-J based Erlang distribution sampling
     */
    protected double generateErlangValueDesmoj(int order, double mean) {
        if (order <= 0) {
            throw new IllegalArgumentException("Order must be positive for Erlang distribution");
        }
        if (mean <= 0) {
            throw new IllegalArgumentException("Mean must be positive for Erlang distribution");
        }
        
        if (desmojModel == null) {
            System.err.println("Warning: DESMO-J model not initialized, falling back to manual implementation");
            
        }
        
        try {
            ContDistErlang erlangDist = new ContDistErlang(desmojModel, "erlang", order, mean, false, false);
            return erlangDist.sample();
        } catch (Exception e) {
            throw new RuntimeException("Error using DESMO-J Erlang distribution " + e.getMessage(), e);
            
        }
    }

    /**
     * DESMO-J based triangular distribution sampling
     */
    protected double generateTriangularValueDesmoj(double lower, double peak, double upper) {
        if (lower >= upper) {
            throw new IllegalArgumentException("Lower bound must be less than upper bound for triangular distribution");
        }
        if (peak < lower || peak > upper) {
            throw new IllegalArgumentException("Peak must be between lower and upper bounds for triangular distribution");
        }
        
        if (desmojModel == null) {
            System.err.println("Warning: DESMO-J model not initialized, falling back to manual implementation");
           
        }
        
        try {
            ContDistTriangular triangularDist = new ContDistTriangular(desmojModel, "triangular", lower, peak, upper, false, false);
            return triangularDist.sample();
        } catch (Exception e) {
            throw new RuntimeException("Error using DESMO-J triangular distribution " + e.getMessage(), e);
            
        }
    }

    /**
     * DESMO-J based binomial distribution sampling
     */
    protected double generateBinomialValueDesmoj(int amount, double probability) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative for binomial distribution");
        }
        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Probability must be between 0 and 1 for binomial distribution");
        }
        if (desmojModel == null) {
            throw new IllegalStateException("DESMO-J model not initialized");
        }
        DiscreteDistBinomial binomialDist = new DiscreteDistBinomial(desmojModel, "binomial", probability, amount, false, false);
        return (double) binomialDist.sample();
    }

    /**
     * DESMO-J based arbitrary finite (empirical) distribution sampling
     */
    protected double generateArbitraryFiniteValueDesmoj(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Values list must not be null or empty for arbitrary finite distribution");
        }
        if (desmojModel == null) {
            throw new IllegalStateException("DESMO-J model not initialized");
        }
        // Prepare arrays for empirical distribution
        Number[] empiricalValues = new Number[values.size()];
        double[] empiricalFrequencies = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Map<String, Object> entry = values.get(i);
            Object valueObj = entry.get("value");
            Object freqObj = entry.get("frequency");
            if (valueObj == null || freqObj == null) {
                throw new IllegalArgumentException("Each entry must have 'value' and 'frequency'");
            }
            empiricalValues[i] = (Number) valueObj;
            empiricalFrequencies[i] = ((Number) freqObj).doubleValue();
        }
        DiscreteDistEmpirical<Double> empiricalDist = new DiscreteDistEmpirical<>(desmojModel, "empirical", false, false);
        for (int i = 0; i < empiricalValues.length; i++) {
            empiricalDist.addEntry(empiricalValues[i].doubleValue(), empiricalFrequencies[i]);
        }
        return empiricalDist.sample();
    }


    

    // Helper method to get or generate a distribution value
    private double getDistributionValue(String driverId, String distributionType, Map<String, Object> distribution) {
        List<Map<String, Object>> values = (List<Map<String, Object>>) distribution.get("values");
        String cacheKey = getDistributionCacheKey(driverId, distributionType, values);

        Double cachedValue = distributionValueCache.get(cacheKey);
        if (cachedValue != null) {
            return cachedValue;
        }

        // Generate new value based on distribution type
        double value = 1.0; // Default value
        
        if (values != null && !values.isEmpty()) {
            if ("constantDistribution".equals(distributionType)) {
                for (Map<String, Object> val : values) {
                    if ("constantValue".equals(val.get("id"))) {
                        value = ((Number) val.get("value")).doubleValue();
                        break;
                    }
                }
            } else if ("exponentialDistribution".equals(distributionType)) {
                double mean = 1.0; // Default mean
                for (Map<String, Object> val : values) {
                    if ("mean".equals(val.get("id"))) {
                        mean = ((Number) val.get("value")).doubleValue();
                        break;
                    }
                }
                value = generateExponentialValueDesmoj(mean);
                System.out.println("  Generated new exponential value (DESMO-J) for driver " + driverId + " with mean " + mean + ": " + value);
            } else if ("normalDistribution".equals(distributionType)) {
                double mean = 1.0; // Default mean
                double standardDeviation = 0.0; // Default standard deviation
                for (Map<String, Object> val : values) {
                    if ("mean".equals(val.get("id"))) {
                        mean = ((Number) val.get("value")).doubleValue();
                    } else if ("standardDeviation".equals(val.get("id"))) {
                        standardDeviation = ((Number) val.get("value")).doubleValue();
                    }

                }
                value = generateNormalValueDesmoj(mean, standardDeviation);
                System.out.println("  Generated new normal value (DESMO-J) for driver " + driverId + 
                    " with mean " + mean + " and standard deviation " + standardDeviation + ": " + value);
            } else if ("uniformDistribution".equals(distributionType)) {
                double lower = 0.0; // Default lower bound
                double upper = 1.0; // Default upper bound
                for (Map<String, Object> val : values) {
                    if ("lower".equals(val.get("id"))) {
                        lower = ((Number) val.get("value")).doubleValue();
                    } else if ("upper".equals(val.get("id"))) {
                        upper = ((Number) val.get("value")).doubleValue();
                    }
                }
                value = generateUniformValueDesmoj(lower, upper);
                System.out.println("  Generated new uniform value (DESMO-J) for driver " + driverId + 
                    " between " + lower + " and " + upper + ": " + value);
            } else if ("erlangDistribution".equals(distributionType)) {
                int order = 1;
                double mean = 1.0;
                for (Map<String, Object> val : values) {
                    if ("order".equals(val.get("id"))) {
                        order = ((Number) val.get("value")).intValue();
                    } else if ("mean".equals(val.get("id"))) {
                        mean = ((Number) val.get("value")).doubleValue();
                    }
                }
                value = generateErlangValueDesmoj(order, mean);
                System.out.println("  Generated new erlang value (DESMO-J) for driver " + driverId + " with order " + order + " and mean " + mean + ": " + value);
            } else if ("triangularDistribution".equals(distributionType)) {
                double lower = 0.0, peak = 0.0, upper = 1.0;
                for (Map<String, Object> val : values) {
                    if ("lower".equals(val.get("id"))) lower = ((Number) val.get("value")).doubleValue();
                    else if ("peak".equals(val.get("id"))) peak = ((Number) val.get("value")).doubleValue();
                    else if ("upper".equals(val.get("id"))) upper = ((Number) val.get("value")).doubleValue();
                }
                value = generateTriangularValueDesmoj(lower, peak, upper);
                System.out.println("  Generated new triangular value (DESMO-J) for driver " + driverId + " with lower " + lower + ", peak " + peak + ", upper " + upper + ": " + value);
            } else if ("binomialDistribution".equals(distributionType)) {
                int amount = 1;
                double probability = 0.5;
                for (Map<String, Object> val : values) {
                    if ("amount".equals(val.get("id"))) amount = ((Number) val.get("value")).intValue();
                    else if ("probability".equals(val.get("id"))) probability = ((Number) val.get("value")).doubleValue();
                }
                value = generateBinomialValueDesmoj(amount, probability);
                System.out.println("  Generated new binomial value (DESMO-J) for driver " + driverId + " with amount " + amount + " and probability " + probability + ": " + value);
            } else if ("arbitraryFiniteProbabilityDistribution".equals(distributionType)) {
                value = generateArbitraryFiniteValueDesmoj(values);
                System.out.println("  Generated new arbitrary finite value (DESMO-J) for driver " + driverId + ": " + value);
            } else {
                // For other distributions, use mean value
                for (Map<String, Object> val : values) {
                    if ("mean".equals(val.get("id"))) {
                        value = ((Number) val.get("value")).doubleValue();
                        break;
                    }
                }
            }
        }

        // Cache the generated value
        distributionValueCache.put(cacheKey, value);
        return value;
    }

    private String getDistributionCacheKey(String driverId, String distributionType, List<Map<String, Object>> values) {
        StringBuilder sb = new StringBuilder();
        sb.append(driverId).append("_").append(distributionType);
        if (values != null) {
            for (Map<String, Object> val : values) {
                sb.append("_").append(val.get("id")).append("=").append(val.get("value"));
            }
        }
        return sb.toString();
    }

    // Helper method to calculate cost for a driver
    private double calculateCostForDriver(
            ConcreteCostDriver driver,
            String impactMethodId,
            String normalizationSetId,
            String cacheKey,
            String processInstanceId) {
        if (impactMethodId != null) {
            try {
                
                double amount = 1.0; 
                
                // Get the cost variant for this process instance
                CostVariant costVariant = costVariantStack.peek();
                System.out.println("\n[CostDriverExecutionLoggingPlugin] Calculating cost for driver: " + driver.getId());
                
                if (costVariant != null) {
                    Map<String, Map<String, Object>> driverDistributions = costVariant.getDriverDistributions();
                    
                    Map<String, Object> distribution = driverDistributions.get(driver.getId());
                    
                    if (distribution != null) {
                        String distributionType = (String) distribution.get("distributionType");
                                                                       
                       amount = getDistributionValue(driver.getId(), distributionType, distribution);
                    } else {
                        System.out.println("  No distribution found, using default amount: " + amount);
                    }
                } else {
                    System.out.println("  No cost variant found, using default amount: " + amount);
                }

                System.out.println("  Final amount used for calculation: " + amount);
                double cost = driver.calculateCost(impactMethodId, normalizationSetId, amount);
                // Cache the cost for this process instance
                processInstanceCostCache.get(processInstanceId).put(cacheKey, cost);
                System.out.println("  Calculated cost: " + cost);
                return cost;
            } catch (Exception e) {
                System.err.println("  Error calculating cost: " + e.getMessage());
                e.printStackTrace();
                return driver.getLCAScore();
            }
        } else {
            return driver.getLCAScore();
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

