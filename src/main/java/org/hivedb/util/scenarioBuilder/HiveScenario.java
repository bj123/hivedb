package org.hivedb.util.scenarioBuilder;

import java.util.Collection;
import java.util.Map;

import org.hivedb.HiveException;
import org.hivedb.meta.Hive;
import org.hivedb.meta.PartitionDimension;
import org.hivedb.meta.Resource;
import org.hivedb.meta.SecondaryIndex;
import org.hivedb.util.GenerateHiveIndexKeys;
import org.hivedb.util.InstallHiveIndexSchema;

/**
 *
 * @author andylikuski Andy Likuski
 * 
 *  Given a hive URI, index URIs, and data nodes, this class contructs 
 *  partition dimensions, node groups, primary indexes, resources, and secondary index,
 *  and also creates sample object instances and inserts their ids into the index tables.
 *  The meta data is set to the hive, so that the result of constructing this class
 *  is a full hive and populated index tables. The class instance also offers a number
 *  of methods that return the meta data and instances created in order for tests to
 *  validate the persisted data.
 *  
 *  There are serveral configuration parameters listed as constants at the top of the class
 *  which are used to determine what classes are modeled and how many instances are created.
 *  You may alter these or sublcass HiveScenarioConfig to test your own classes. HiveScenario
 *  expects the classes to conform to its Identifiable interface. To test your classes subclass
 *  them and implement the Identifiable interface. The Identifiable interface allows a class
 *  to function as a primary index key and as a single secondary index key. If you want a class
 *  to only function as a primary index class you can implement PrimaryIndexIdentifiable and
 *  to only function as a resource/secondary index class implement SecondaryIndexIdentifiable.
 *  
 *  Primary Index classes must have a no argument contructor (for use by reflection)
 *  Secondary Index classes must have a 1 argument contructor (for use by reflection) whose
 *  arguement is the instance to be used as the primary index key (see SecondaryIndexidentifiable)
 */
public class HiveScenario {
	
	public static HiveScenario run(HiveScenarioConfig hiveScenarioConfig) throws HiveException {
		HiveScenario hiveScenario = new HiveScenario(hiveScenarioConfig);
		hiveScenarioConfig.getHive().sync(); //Get the data that we've inserted so we can compare HiveScenario's data to the Hive's data
		return hiveScenario;
	}

	protected HiveScenario(final HiveScenarioConfig hiveScenarioConfig) throws HiveException
	{
		final Hive hive = hiveScenarioConfig.getHive();
		fill(hiveScenarioConfig, hive);
	}
	private void fill(final HiveScenarioConfig hiveScenarioConfig, final Hive hive) throws HiveException {
		Map<Class, PartitionDimension> partitionDimensionMap = InstallHiveIndexSchema.install(hiveScenarioConfig, hive);
		populateData(hiveScenarioConfig, hive, partitionDimensionMap);
	}
	private void populateData(final HiveScenarioConfig hiveScenarioConfig, final Hive hive, Map<Class, PartitionDimension> partitionDimensionMap) {
		GenerateHiveIndexKeys generateIndexKeys = new GenerateHiveIndexKeys();
		// Create a number of instances for each of the primary PartitionIndex classes, distributing them among the nodes
		Map<Class, Collection<PrimaryIndexIdentifiable>> primaryIndexInstanceMap = generateIndexKeys.createPrimaryIndexInstances(hive, partitionDimensionMap, hiveScenarioConfig.getInstanceCountPerPrimaryIndex());
		// Create a number of instances for each of the secondary PartitionIndex classes.
		// Each secondary instances references a primary instance, with classes according to secondaryToPrimaryMap
		Map<Class, Map<Resource, Map<SecondaryIndex, Collection<SecondaryIndexIdentifiable>>>> secondaryIndexInstances = generateIndexKeys.createSecondaryIndexInstances(hiveScenarioConfig, hive, partitionDimensionMap, primaryIndexInstanceMap);
	
		this.hive = hive;
		this.partitionDimensionMap = partitionDimensionMap;
		this.primaryIndexInstanceMap = primaryIndexInstanceMap;
		this.secondaryIndexInstances = secondaryIndexInstances;
	}
	
	Hive hive;
	Map<Class, PartitionDimension> partitionDimensionMap;
	Map<Class, Collection<PrimaryIndexIdentifiable>> primaryIndexInstanceMap;
	Map<Class, Map<Resource, Map<SecondaryIndex, Collection<SecondaryIndexIdentifiable>>>> secondaryIndexInstances;
	public Hive getHive() {
		return hive;
	}	
	public Collection<PartitionDimension> getCreatedPartitionDimensions()
	{
		return partitionDimensionMap.values();
	}
	public Collection<PrimaryIndexIdentifiable> getPrimaryIndexInstancesCreatedByThisPartitionDimension(PartitionDimension partitionDimension)
	{
		return primaryIndexInstanceMap.get(Transform.reverseMap(partitionDimensionMap).get(partitionDimension));
	}
	public Collection<Resource> getResourcesOfThisPartitionDimension(PartitionDimension partitionDimension)
	{
		return secondaryIndexInstances.get(Transform.reverseMap(partitionDimensionMap).get(partitionDimension)).keySet();
	}
	public Collection<SecondaryIndexIdentifiable> getSecondaryIndexInstancesForThisPartitionDimensionAndResource(PartitionDimension partitionDimension, Resource resource, SecondaryIndex secondaryIndex)
	{
		return secondaryIndexInstances.get(Transform.reverseMap(partitionDimensionMap).get(partitionDimension)).get(resource).get(secondaryIndex);
	}
}
