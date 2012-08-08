package org.sagebionetworks.stack;

import org.apache.log4j.Logger;
import org.sagebionetworks.stack.config.InputConfiguration;

import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.CreateDBInstanceRequest;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceNotFoundException;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;

import static org.sagebionetworks.stack.Constants.*;

/**
 * Setup the MySQL database
 * 
 * @author John
 *
 */
public class MySqlDatabaseSetup {
	
	private static Logger log = Logger.getLogger(MySqlDatabaseSetup.class.getName());
	
	private AmazonRDSClient client;
	private InputConfiguration config;
	private GeneratedResources resources;
	/**
	 * The IoC constructor.
	 * @param client
	 * @param config
	 */
	public MySqlDatabaseSetup(AmazonRDSClient client, InputConfiguration config, GeneratedResources resources) {
		if(client == null) throw new IllegalArgumentException("AmazonRDSClient cannot be null");
		if(config == null) throw new IllegalArgumentException("Config cannot be null");
		if(resources == null) throw new IllegalArgumentException("GeneratedResources cannot be null");
		this.client = client;
		this.config = config;
		this.resources = resources;
	}


	/**
	 * Create all database instances if they do not already exist.
	 * 
	 * @param client
	 * @param config
	 */
	public void setupAllDatabaseInstances(){
		// Build the request to create the ID generator database.
		CreateDBInstanceRequest request = buildIdGeneratorCreateDBInstanceRequest();
		
		// Get the instances
		DBInstance idGenInstance = createOrGetDatabaseInstance(request);
		log.debug("Database instance: ");
		log.debug(idGenInstance);
		resources.setIdGeneratorDatabase(idGenInstance);
		// Now create the stack instance database
		request = buildStackInstancesCreateDBInstanceRequest();
		
		// Get the instances
		DBInstance stackInstance = createOrGetDatabaseInstance(request);
		log.debug("Database instance: ");
		log.debug(stackInstance);
		resources.setStackInstancesDatabase(stackInstance);
	}

	/**
	 * Build up the CreateDBInstanceRequest used to create the ID Generator database.
	 * 
	 * @return
	 */
	CreateDBInstanceRequest buildIdGeneratorCreateDBInstanceRequest() {
		CreateDBInstanceRequest request = getDefaultCreateDBInstanceRequest();
		// Is this a production stack?
		if(config.isProductionStack()){
			// Production database need to have a backup replicate ready to go in another
			// zone at all times!
			request.setMultiAZ(true);
		}else{
			// This is not a production stack so we do not want to incur the extra cost of
			// a backup replicate database in another zone.
			request.setMultiAZ(false);
		}
		// This will be the schema name.
		request.setDBName(config.getIdGeneratorDatabaseSchemaName());
		request.setDBInstanceIdentifier(config.getIdGeneratorDatabaseIdentifier());
		// This database only needs a small amount of disk space so this is set the minimum of 5 GB
		request.setAllocatedStorage(new Integer(5));
		request.setMasterUsername(config.getIdGeneratorDatabaseMasterUsername());
		request.setMasterUserPassword(config.getIdGeneratorDatabaseMasterPasswordPlaintext());
		// The security group
		request.withDBSecurityGroups(config.getIdGeneratorDatabaseSecurityGroupName());
		// The parameters.
		request.setDBParameterGroupName(config.getDatabaseParameterGroupName());
		return request;
	}
	
	

	/**
	 * Build up the CreateDBInstanceRequest used for the stack-instance database.
	 * 
	 * @return
	 */
	CreateDBInstanceRequest buildStackInstancesCreateDBInstanceRequest() {
		CreateDBInstanceRequest request = getDefaultCreateDBInstanceRequest();
		// Production stacks have have different properties
		if(config.isProductionStack()){
			// Production database need to have a backup replicate ready to go in another
			// zone at all times!
			request.setMultiAZ(true);
			// The production database should be a large
			request.setDBInstanceClass(DATABASE_INSTANCE_CLASS_LARGE);
		}else{
			// This is not a production stack so we do not want to incur the extra cost of
			// a backup replicate database in another zone.
			request.setMultiAZ(false);
			// All non-production databases should be small
			request.setDBInstanceClass(DATABASE_INSTANCE_CLASS_SMALL);
		}
		// This will be the schema name.
		request.setDBName(config.getStackInstanceDatabaseSchema());
		request.setDBInstanceIdentifier(config.getStackInstanceDatabaseIdentifier());
		// This is the main database for the stack so it should have 50 GB
		request.setAllocatedStorage(new Integer(50));
		request.setMasterUsername(config.getStackInstanceDatabaseMasterUser());
		request.setMasterUserPassword(config.getStackInstanceDatabaseMasterPasswordPlaintext());
		// The security group
		request.withDBSecurityGroups(config.getStackDatabaseSecurityGroupName());
		// The parameters.
		request.setDBParameterGroupName(config.getDatabaseParameterGroupName());
		// if this is a production stack
		return request;
	}
	
	/**
	 * If this database instances does not already exist it will be created, otherwise the instance information will be returned.
	 * 
	 * @param request
	 * @return
	 */
	DBInstance createOrGetDatabaseInstance(CreateDBInstanceRequest request){
		// First query for the instance
		try{
			DescribeDBInstancesResult result = client.describeDBInstances(new DescribeDBInstancesRequest().withDBInstanceIdentifier(request.getDBInstanceIdentifier()));
			if(result.getDBInstances() == null || result.getDBInstances().size() != 1) throw new IllegalStateException("Did not find exactly one database instances with the identifier: "+request.getDBInstanceIdentifier());
			log.debug("Database: "+request.getDBInstanceIdentifier()+" already exists");
			return result.getDBInstances().get(0);
		}catch(DBInstanceNotFoundException e){
			// This database does not exist to create it
			// Create the database.
			log.debug("Creating database...");
			log.debug(request);
			return client.createDBInstance(request);
		}
	}
	
	/**
	 * Fill out a CreateDBInstanceRequest will all of the default values.
	 * 
	 * @return
	 */
	public static CreateDBInstanceRequest getDefaultCreateDBInstanceRequest(){
		CreateDBInstanceRequest request = new CreateDBInstanceRequest();
		request.setAllocatedStorage(new Integer(5));
		request.setDBInstanceClass(DATABASE_INSTANCE_CLASS_SMALL);
		request.setEngine(DATABASE_ENGINE_MYSQL);
		request.setAvailabilityZone(EC2_AVAILABILITY_ZONE_US_EAST_1D);
		request.setPreferredMaintenanceWindow(PREFERRED_DATABASE_MAINTENANCE_WINDOW_SUNDAY_NIGHT_PDT);
		request.setBackupRetentionPeriod(new Integer(7));
		request.setPreferredBackupWindow(PREFERRED_DATABASE_BACKUP_WINDOW_MIDNIGHT);
		request.setMultiAZ(false);
		request.setEngineVersion(DATABASE_ENGINE_MYSQL_VERSION);
		request.setAutoMinorVersionUpgrade(true);
		request.setLicenseModel(LICENSE_MODEL_GENERAL_PUBLIC);
		return request;
	}

}