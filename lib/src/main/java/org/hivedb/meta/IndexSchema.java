/**
 * HiveDB is an Open Source (LGPL) system for creating large, high-transaction-volume
 * data storage systems.
 */
package org.hivedb.meta;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;

import org.hivedb.HiveDbDialect;
import org.hivedb.Schema;
import org.hivedb.util.JdbcTypeMapper;

/**
 * IndexSchema contains tables of primary and secondary indexes in
 * accordance with the rows existing in the Global Hive meta tables.
 * Each IndexSchema instance references a particular jdbc URI where it will
 * create index tables. All primary and secondary indexes of a partition
 * index must be stored at the same URI, hence you should always construct
 * and IndexSchema with the URI of a partition dimension's index node.
 * <p>
 * 
 * @author Andy Likuski (alikuski@cafepress.com)
 * @author Britt Crawford (bcrawford@cafepress.com)
 */
public class IndexSchema extends Schema{
	private PartitionDimension partitionDimension;
	/**
	 * IndexSchema is constructed against a JDBC URI, which will be the destination
	 * for the schema tables.
	 * 
	 * @param dbURI Empty target database connect string, including username, password & catalog
	 * @param dialect Data definition language dialect
	 */
	public IndexSchema(PartitionDimension partitionDimension) {
		super(partitionDimension.getIndexUri());
		this.partitionDimension = partitionDimension;
	}
	
	/**
	 * 
	 * @return
	 */
	protected String getCreatePrimaryIndex() {
		return 
			"CREATE TABLE " + getPrimaryIndexTableName(partitionDimension)
			+ " ( " 
			+ " id " + addLengthForVarchar(JdbcTypeMapper.jdbcTypeToString(partitionDimension.getColumnType())) + " primary key not null, "
			+ " node SMALLINT not null, "
			+ " secondary_index_count INTEGER not null, "
			+ " last_updated "+ JdbcTypeMapper.jdbcTypeToString(Types.DATE) +" not null, "
			+ " read_only " +  GlobalSchema.getBooleanTypeForDialect(dialect) + " default 0"			
			+ ifMySql(", INDEX node_id (node),")
			+ ifMySql(" INDEX last_updated (last_updated)")
			+ " ) "
			+ ifMySql("ENGINE=InnoDB");
	}
	
	/**
	 * 
	 * @param secondaryIndex
	 */
	protected String getCreateSecondaryIndex(SecondaryIndex secondaryIndex) {
		return 
			"CREATE TABLE " + getSecondaryIndexTableName(partitionDimension,secondaryIndex) 
			+ " ( "
			+ " id " +  addLengthForVarchar(JdbcTypeMapper.jdbcTypeToString(secondaryIndex.getColumnInfo().getColumnType())) + " not null, "
			+ " pkey " + addLengthForVarchar(JdbcTypeMapper.jdbcTypeToString(partitionDimension.getColumnType())) + " not null"
			+ ifMySql(", INDEX secondary_index_value (id),")
			+ ifMySql(" INDEX secondary_index_to_primary_index (pkey)")
			+ " ) " 
			+ ifMySql(" ENGINE=InnoDB");
	}
	
	/**
	 * Constructs the name of the table for the primary index.
	 * @return
	 */	
	public static String getPrimaryIndexTableName(PartitionDimension partitionDimension) {
		return "hive_primary_" + partitionDimension.getName().toLowerCase();
	}
	/**
	 * Constructs the name of the table for the secondary index.
	 * @return
	 */
	public static String getSecondaryIndexTableName(PartitionDimension partitionDimension, SecondaryIndex secondaryIndex) {
		return "hive_secondary_" + secondaryIndex.getResource().getName().toLowerCase() + "_" + secondaryIndex.getColumnInfo().getName();	
	}
	
	@Override
	public Collection<TableInfo> getTables() {
		Collection<TableInfo> TableInfos = new ArrayList<TableInfo>();
		TableInfos.add(new TableInfo(getPrimaryIndexTableName(partitionDimension), getCreatePrimaryIndex()));
		for (Resource resource : partitionDimension.getResources())
			for (SecondaryIndex secondaryIndex : resource.getSecondaryIndexes())
				TableInfos.add(new TableInfo(
						getSecondaryIndexTableName(partitionDimension,secondaryIndex), 
						getCreateSecondaryIndex(secondaryIndex)));
		return TableInfos;
	}
	
	private String ifMySql(String sql) {
		return (dialect.equals(HiveDbDialect.MySql) ? sql : "");
	}
	
}