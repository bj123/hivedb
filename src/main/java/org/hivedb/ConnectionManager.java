package org.hivedb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.hivedb.meta.AccessType;
import org.hivedb.meta.KeySemaphore;
import org.hivedb.meta.Node;
import org.hivedb.meta.Resource;
import org.hivedb.meta.SecondaryIndex;
import org.hivedb.meta.directory.Directory;
import org.hivedb.meta.persistence.DataSourceProvider;
import org.hivedb.util.Preconditions;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Unary;

public class ConnectionManager {
	private Directory directory;
	private DataSourceProvider dataSourceProvider;
	private Map<Integer, DataSource> nodeDataSources;
	private JdbcDaoSupportCacheImpl cache;
	private Hive hive;
	
	public ConnectionManager(Directory directory, Hive hive, DataSourceProvider provider) {
		this.hive = hive;
		this.directory = directory;
		this.dataSourceProvider = provider;
		this.cache = new JdbcDaoSupportCacheImpl(directory, hive, provider);
		this.nodeDataSources = getDataSourceMap(hive.getNodes(), provider);
	}
	
	public static Map<Integer, DataSource> getDataSourceMap(Collection<Node> nodes, DataSourceProvider dataSourceProvider) {
		Map<Integer, DataSource> dataSources = new ConcurrentHashMap<Integer, DataSource>();
		for(Node node :  nodes) 
			dataSources.put(node.getId(), dataSourceProvider.getDataSource(node.getUri()));
		return dataSources;
	}
	
	private Connection getConnection(KeySemaphore semaphore, AccessType intention) throws HiveLockableException,SQLException {
		if(intention == AccessType.ReadWrite)
			Preconditions.isWritable(hive, semaphore, hive.getNode(semaphore.getId()));
		return nodeDataSources.get(semaphore.getId()).getConnection();
	}
	
	public Collection<Connection> getByPartitionKey(Object primaryIndexKey, AccessType intent) throws SQLException, HiveLockableException {
		Collection<Connection> connections = new ArrayList<Connection>();
		for(KeySemaphore semaphore : directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey))
			connections.add(getConnection(semaphore, intent));
		return connections;
	}

	public Collection<Connection> getByResourceId(String resourceName, Object resourceId, AccessType intent) throws HiveLockableException, SQLException {
		Collection<Connection> connections = new ArrayList<Connection>();
		for(KeySemaphore semaphore : directory.getKeySemaphoresOfResourceId(getResource(resourceName), resourceId))
			connections.add(getConnection(semaphore, intent));
		return connections;
	}
	
	private Resource getResource(String resourceName) {
		return this.directory.getPartitionDimension().getResource(resourceName);
	}

	public Collection<Connection> getBySecondaryIndexKey(String secondaryIndexName, String resourceName, Object secondaryIndexKey, AccessType intent) throws HiveLockableException, SQLException {
		if(AccessType.ReadWrite == intent)
			throw new UnsupportedOperationException("Writes must be performed using the primary index key.");
		
		SecondaryIndex secondaryIndex = directory.getPartitionDimension().getResource(resourceName).getSecondaryIndex(secondaryIndexName);
		Collection<Connection> connections = new ArrayList<Connection>();
		Collection<KeySemaphore> keySemaphores = directory.getKeySemaphoresOfSecondaryIndexKey(secondaryIndex, secondaryIndexKey);
		keySemaphores = Filter.getUnique(keySemaphores, new Unary<KeySemaphore, Integer>(){
			public Integer f(KeySemaphore item) {
				return item.getId();
			}});
		for(KeySemaphore semaphore : keySemaphores)
			connections.add(getConnection(semaphore, intent));
		return connections;
	}
	
	public JdbcDaoSupportCache daoSupport() {
		return cache;
	}
	
	public DataSource addNode(Node node) {
		nodeDataSources.put(node.getId(), dataSourceProvider.getDataSource(node));
		cache.addNode(node);
		return nodeDataSources.get(node.getId());
	}
	
	public DataSource removeNode(Node node) {
		cache.removeNode(node);
		return nodeDataSources.remove(node.getId());
	}
}
