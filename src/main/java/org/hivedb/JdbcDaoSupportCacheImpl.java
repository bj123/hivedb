package org.hivedb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.hivedb.meta.AccessType;
import org.hivedb.meta.KeySemaphore;
import org.hivedb.meta.Node;
import org.hivedb.meta.PartitionDimension;
import org.hivedb.meta.Resource;
import org.hivedb.meta.SecondaryIndex;
import org.hivedb.meta.directory.Directory;
import org.hivedb.meta.persistence.DataSourceProvider;
import org.hivedb.util.Preconditions;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Unary;
import org.springframework.jdbc.core.simple.SimpleJdbcDaoSupport;

/**
 * @author Britt Crawford (bcrawford@cafepress.com)
 *
 */
public class JdbcDaoSupportCacheImpl implements JdbcDaoSupportCache{
	private Map<Integer, SimpleJdbcDaoSupport> jdbcDaoSupports;
	private Directory directory;
	private DataSourceProvider dataSourceProvider;
	private Hive hive;
	
	public JdbcDaoSupportCacheImpl(Directory directory, Hive hive, DataSourceProvider dataSourceProvider) {
		this.hive = hive;
		this.directory = directory;
		this.dataSourceProvider = dataSourceProvider;
		this.jdbcDaoSupports = getDataSourceMap(hive.getNodes(), dataSourceProvider);
	}

	public static Map<Integer, SimpleJdbcDaoSupport> getDataSourceMap(Collection<Node> nodes, DataSourceProvider dataSourceProvider) {
		Map<Integer, SimpleJdbcDaoSupport> jdbcDaoSupports = new ConcurrentHashMap<Integer, SimpleJdbcDaoSupport>();
		for(Node node : nodes) 
			jdbcDaoSupports.put(node.getId(), makeDaoSupport(node, dataSourceProvider));
		return jdbcDaoSupports;
	}

	public static SimpleJdbcDaoSupport makeDaoSupport(Node node, DataSourceProvider provider) {
		return new DataNodeJdbcDaoSupport(provider.getDataSource(node.getUri()));
	}
	
	public SimpleJdbcDaoSupport addNode(Node node) {
		jdbcDaoSupports.put(node.getId(), makeDaoSupport(node, dataSourceProvider));
		return jdbcDaoSupports.get(node.getId());
	}
	
	public SimpleJdbcDaoSupport removeNode(Node node) {
		return jdbcDaoSupports.remove(node.getId());
	}
	
	private SimpleJdbcDaoSupport get(KeySemaphore semaphore, AccessType intention) throws HiveReadOnlyException { 
		Node node = null;
		node = hive.getNode(semaphore.getId());
		
		if(intention == AccessType.ReadWrite)
			Preconditions.isWritable(node, semaphore);
		
		if( jdbcDaoSupports.containsKey(semaphore.getId()))
			return jdbcDaoSupports.get(semaphore.getId());
		
		throw new HiveKeyNotFoundException("Could not find dataSource for ", semaphore);
	}

	/**
	 * Get a SimpleJdbcDaoSupport by primary partition key.
	 * @param partitionDimension The partition dimension
	 * @param primaryIndexKey The partition key
	 * @param intention The permissions with which you wish to acquire the conenction
	 * @return
	 * @throws HiveReadOnlyException
	 */
	public Collection<SimpleJdbcDaoSupport> get(Object primaryIndexKey, final AccessType intention) throws HiveReadOnlyException {
		Collection<KeySemaphore> semaphores = directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey);
		Collection<SimpleJdbcDaoSupport> supports = new ArrayList<SimpleJdbcDaoSupport>();
		for(KeySemaphore semaphore : semaphores)
			supports.add(get(semaphore, intention));
		return supports;
	}

	/**
	 * Get a SimpleJdbcDaoSupport by secondary index key.
	 * @param secondaryIndex The secondary index to search on
	 * @param secondaryIndexKey The secondary key
	 * @param intention The permissions with which you wish to acquire the conenction
	 * @return
	 * @throws HiveReadOnlyException
	 */
	public Collection<SimpleJdbcDaoSupport> get(String resource, String secondaryIndex, Object secondaryIndexKey, final AccessType intention) throws HiveReadOnlyException {
		Collection<KeySemaphore> keySemaphores = directory.getKeySemaphoresOfSecondaryIndexKey(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey);
		keySemaphores = Filter.getUnique(keySemaphores, new Unary<KeySemaphore, Integer>(){
			public Integer f(KeySemaphore item) {
				return item.getId();
		}});
		
		Collection<SimpleJdbcDaoSupport> supports = new ArrayList<SimpleJdbcDaoSupport>();
		for(KeySemaphore semaphore : keySemaphores)
			supports.add(get(semaphore, intention));
		return supports;
	}
	
	private static class DataNodeJdbcDaoSupport extends SimpleJdbcDaoSupport
	{
		public DataNodeJdbcDaoSupport(DataSource dataSource)
		{
			this.setDataSource(dataSource);
		}
	}

	public SimpleJdbcDaoSupport getUnsafe(String nodeName) {
		try {
			Node node = hive.getNode(nodeName);
			KeySemaphore semaphore = new KeySemaphore(node.getId(), node.isReadOnly());
			return get(semaphore, AccessType.ReadWrite);
		} catch (HiveException e) {
			throw new RuntimeException(e);
		}
	}

	public Collection<SimpleJdbcDaoSupport> get(String resource, Object resourceId, AccessType intention) throws HiveReadOnlyException {
		Collection<KeySemaphore> semaphores = directory.getKeySemaphoresOfResourceId(getResource(resource), resourceId);
		Collection<SimpleJdbcDaoSupport> supports = new ArrayList<SimpleJdbcDaoSupport>();
		for(KeySemaphore semaphore : semaphores)
			supports.add(get(semaphore, intention));
		return supports;
	}

	public Collection<SimpleJdbcDaoSupport> getAllUnsafe() {
		Collection<SimpleJdbcDaoSupport> daos = new ArrayList<SimpleJdbcDaoSupport>();
		for(Node node : hive.getNodes())
			daos.add(jdbcDaoSupports.get(node.getId()));
		return daos;
	}
	
	private Resource getResource(String name) {
		return directory.getPartitionDimension().getResource(name);
	}
	
	private SecondaryIndex getSecondaryIndex(String resource, String name) {
		return directory.getPartitionDimension().getResource(resource).getSecondaryIndex(name);
	}
}
