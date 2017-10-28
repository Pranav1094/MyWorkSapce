package com.test.config;

import java.security.MessageDigest;
import java.util.ArrayList;
// Java
import java.util.List;
import java.util.Random;

import org.junit.Before;
// JUnit
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBAddress;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;


public class ReplicaSetSharding {

	@Before
	public void setupCluster() throws Exception {

		// Configure the replica sets.
		configureReplicaSet("shard0ReplicaSet", new int[] { 27018, 27019, 27023 });
		// configureReplicaSet("shard0ReplicaSet", new int[] { 27018 });

		Thread.sleep(61000);

		configureReplicaSet("shard1ReplicaSet", new int[] { 27020, 27021, 27024 });
		// configureReplicaSet("shard1ReplicaSet", new int[] { 27020 });

		configureReplicaSet("shard2ReplicaSet", new int[] { 27027, 27028, 27029 });
		// configureReplicaSet("shard2ReplicaSet", new int[] { 27027 });

		Thread.sleep(61000);

		// Connect to mongos
		final Mongo mongo = new Mongo(new DBAddress("localhost", 27017, "admin"));

		// Add the first replica set shard.
		CommandResult result = mongo.getDB("admin").command(
				new BasicDBObject("addshard", "shard0ReplicaSet/localhost:27018,localhost:27019,localhost:27023"));
		// = mongo.getDB("admin").command(new BasicDBObject("addshard",
		// "shard0ReplicaSet/localhost:27018"));

		System.out.println(result);

		// Add the second replica set shard.
		result = mongo.getDB("admin").command(
				new BasicDBObject("addshard", "shard1ReplicaSet/localhost:27020,localhost:27021,localhost:27024"));
		// = mongo.getDB("admin").command(new BasicDBObject("addshard",
		// "shard1ReplicaSet/localhost:27020"));

		// Add the third replica set shard.
		result = mongo.getDB("admin").command(
				new BasicDBObject("addshard", "shard2ReplicaSet/localhost:27027,localhost:27028,localhost:27029"));
		// = mongo.getDB("admin").command(new BasicDBObject("addshard",
		// "shard2ReplicaSet/localhost:27027"));

		System.out.println(result);

		// Sleep for a bit to wait for all the nodes to be intialized.
		Thread.sleep(10000);

		// Enable sharding on a collection.
		result = mongo.getDB("admin").command(new BasicDBObject("enablesharding", "testsharding"));
		System.out.println(result);

		final BasicDBObject shardKey = new BasicDBObject("date", 1);
		shardKey.put("hash", 1);

		final BasicDBObject cmd = new BasicDBObject("shardcollection", "testsharding.logs");
		cmd.put("key", shardKey);

		result = mongo.getDB("admin").command(cmd);

		System.out.println(result);

		// Sleep for a bit to make sure the cluster is initialized.
		Thread.sleep(10000);
	}

	/**
	 * Initialize a replica set for a shard.
	 */
	private void configureReplicaSet(final String pReplicaSetName, final int[] pPorts) throws Exception {
		// First we need to setup the replica sets.
		final BasicDBObject config = new BasicDBObject("_id", pReplicaSetName);

		final List<BasicDBObject> servers = new ArrayList<BasicDBObject>();

		int idx = 0;
		for (final int port : pPorts) {
			final BasicDBObject server = new BasicDBObject("_id", idx++);
			server.put("host", ("localhost:" + port));

			if (idx == 2)
				server.put("arbiterOnly", true);

			servers.add(server);
		}

		config.put("members", servers);

		final Mongo mongo = new Mongo(new DBAddress("localhost", pPorts[0], "admin"));

		final CommandResult result = mongo.getDB("admin").command(new BasicDBObject("replSetInitiate", config));

	}

	@Test
	public void testShards() throws Exception {

		final Mongo mongo = new Mongo(new DBAddress("localhost", 27017, "testsharding"));

		final DBCollection shardCollection = mongo.getDB("testsharding").getCollection("logs");

		final Random random = new Random(System.currentTimeMillis());

		// Write some data
		for (int idx = 0; idx < 10000; idx++) {

			final BasicDBObject entry = new BasicDBObject("date",
					("201101" + String.format("%02d", random.nextInt(30))));

			entry.put("hash", md5(("this is a value to hash-" + idx)));

			shardCollection.insert(entry);
		}
	}

	private byte[] md5(final String pValue) throws Exception {
		return MessageDigest.getInstance("MD5").digest(pValue.getBytes("UTF-8"));
	}
}