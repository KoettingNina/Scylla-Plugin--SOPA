# Scylla Plugin for the SOPA Framework
This repository contains a plugin for the business process simulation engine [Scylla](https://github.com/bptlab/scylla), extending it by the [SOPA framework](http://arxiv.org/abs/2405.01176) for sustainability-oriented process analysis and re-design.

## ℹ️ Key Concepts
The plugin extends Scylla with sustainability information of business processes. For details on the framework, have a look at the [respective research paper](http://arxiv.org/abs/2405.01176).
The resulting simulations provide insights into the environmental impact of business processes and their activities.

### What are Cost Drivers?
Cost Drivers (CD) describe factors of environmental impact for activities. Abstract cost drivers (ACD) constitute the general categories of cost driver and activity has. Concrete cost drivers (CCD) are the concretized cases of the ACDs. As en example, in an activity "Delivery packages", the method of delivery can be considered as an ACD while delivery by a bicycle can be one of its concretizations.

### What are Cost Variants?
Cost variants govern what specific combinations of concretizations can occur during individual process instances based on the environmental cost driver hierarchy. In other words, what sets of concrete environmental cost drivers can, during process execution, take the place of the abstract environmental cost drivers during activity execution?

### What are LCA Scores?
A quantified score of environmental impacts associated with the life cycle of a commercial product.



## 🛠️ How to run it?
### [For Developers]
1. git clone the repository
2. [Download Scylla](https://github.com/bptlab/scylla/releases) and add Scylla.jar and scylla-tests.jar into the libs folder
3. Navigate to src/main/java/cost_driver/Main and run.
4. Select the desired configuration files in the samples UI and check "cost_driver" as a plugin.
<img width="1822" alt="Screenshot 2024-01-17 at 23 10 06" src="https://github.com/mhunter02/BearCrow-private/assets/85895529/83200e2f-5fce-4098-8c8e-0b2224d9d91e">
5. The logged data files will be found in a folder with the format: "output_yy_mm_dd...."

#### NOTE
*1. Please remember to put the latest scylla.jar & scylla-tests.jar files in the ./lib folder</br>
*2. Another way of managing the plugin is by replacing the current Scylla dependencies, with the following. Please ensure you are using the [latest](https://github.com/orgs/bptlab/packages?repo_name=scylla) Scylla package
```xml
      <dependency>
            <groupId>de.hpi.bpt</groupId>
            <artifactId>scylla</artifactId>
            <version>0.0.1-SNAPSHOT</version>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>de.hpi.bpt</groupId>
            <artifactId>scylla</artifactId>
            <version>0.0.1-SNAPSHOT</version>
            <scope>compile</scope>
        </dependency>
```

### [For users]
1. Download and unzip the latest version of [scylla.zip](https://github.com/bptlab/scylla/releases)
2. Download our plugin package jarfile [here](https://github.com/INSM-TUM/Scylla-Plugin--SOPA/releases/latest) and put it into scylla's plugin folder
3. Start Scylla
   
A demo video can be found [here](https://youtu.be/ag2_OvQh5vY).

## 🧱 Components
Threep plugin classes are cooperating to achieve this.

### Global Configuration Parser Plugin
Parses the global config file which describes the abstract CDs and their children, concreteCDs with details that it consists of:
```xml
<bsim:costDriver>
    <bsim:abstractCostDriver id="[Abstract Cost Driver ID]">
      <bsim:concreteCostDeiver id="[Concrete Cost Driver ID]" cost="[LCA Score]"/>
      <bsim:concreteCostDeiver id="[Concrete Cost Driver ID]" cost="[LCA Score]"/>
	...
    </bsim:abstractCostDriver>
	...

```
### Simulation Configuration Parser Plugin
Parses the simulation config file which describes the cost variant by ID, frequency of occurrence, and cost:
```xml
<bsim:costVariantConfig>
      <bsim:variant id="[Cost Variant ID]" frequency="[double]">
        <bsim:driver absractId="[Abstract Cost Driver ID]" concreteId="[Concrete Cost Driver ID]"/>
      </bsim:variant>
```
### Logger Plugin
Logs the extended simulation data as an XES and XML file.  

## Results
The results will be shown in two files, *.xes and *_statistic.xml. 
### Event log (enclosed with .xes)
The event log is composed of a sequence of activity instances. We put the activity cost, process cost reference Abstract Cost Driver, and Concrete Cost Driver inside so that the utilization of resources used is clear.
```xml
<trace>
	<string key="concept:name" value="cost[Process_Instance_ID]"/>
	<string key="cost:Process_Instance" value="[Total Cost]"/>
	<string key="cost:variant" value=[Cost Variant A]/>
	<event>
		<string key="cost:driver" value=[Abstract Cost Driver(Concrete Cost Driver): [cost]]/>
		<string key="cost:driver" value=[Abstract Cost Driver(Concrete Cost Driver): [cost]]/>
		<string key="concept:name" value=[Activity]]/>
		<string key="lifecycle:transition" value="[state]"/>
		<date key="time:timestamp" value="2023-12-25T09:00:00+01:00"/>
		<string key="cost:activity" value=[activity cost]/>
	</event>
	...
</trace>
```
### Aggregated sustainability information (enclosed with _statistic.xml)
The outputted file shows a complete detailed breakdown of sustainability info.
Explanation of nodes moving downwards:
```xml
<Sustainability_Info>
    <Average_Cost_Variant_Cost id="[id]">[cost]</Average_Cost_Variant_Cost>
    ...
    <Average_Process_Instance_Cost>[cost]</Average_Process_Instance_Cost>
    <Activity_Cost>
        <Activity id="[id]">
            <Activity_Average_Cost_Variant_Cost id="[id]">[cost]</Activity_Average_Cost_Variant_Cost>
            ...
            <Activity_Average_Cost>[cost]</Activity_Average_Cost>
        </Activity>
        ...
    </Activity_Cost>

    <Activity_Instance_Cost>
        <Activity id="[id]">
            <Cost_Variant id="[id]">
                <activity_instance_cost ProcessInstance_IDs="[IDs]" count="[counts]">[cost]</activity_instance_cost>
            </Cost_Variant>
            ...
        </Activity>
        ...
    </Activity_Instance_Cost>
</Sustainability_Info>
```

## References: <br>
Pufahl, L., & Weske, M. (January 2018). Design of an Extensible BPMN Process Simulator. Retrieved from https://www.researchgate.net/publication/322524759_Design_of_an_Extensible_BPMN_Process_Simulator?enrichId=rgreq-55dc4561329b473ce8f8871f05e56dba-XXX&enrichSource=Y292ZXJQYWdlOzMyMjUyNDc1OTtBUzo1OTU2ODQ2MDQ1MjY1OTJAMTUxOTAzMzY4NTAwNg%3D%3D&el=1_x_3&_esc=publicationCoverPdf  <br>
Ng, K. Y. (1996). An Algorithm for Acyclic State Machines. *Acta Informatica*, 33(4), 223–228. https://doi.org/10.1007/BF02986351
