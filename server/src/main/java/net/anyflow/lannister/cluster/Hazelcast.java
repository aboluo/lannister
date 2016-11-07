/*
 * Copyright 2016 The Lannister Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.anyflow.lannister.cluster;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ISet;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.IdGenerator;

import net.anyflow.lannister.Application;
import net.anyflow.lannister.Settings;
import net.anyflow.lannister.serialization.JsonSerializer;
import net.anyflow.lannister.serialization.SerializableFactory;

public class Hazelcast {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Hazelcast.class);

	public static final Hazelcast INSTANCE = new Hazelcast();
	private static final String CONFIG_NAME = "lannister.cluster.xml";

	private HazelcastInstance substance;

	private Hazelcast() {
		if (Settings.INSTANCE.clusteringMode() != Mode.HAZELCAST) { return; }

		substance = com.hazelcast.core.Hazelcast.newHazelcastInstance(createConfig());
	}

	private Config createConfig() {
		Config config;
		try {
			config = new XmlConfigBuilder(Application.class.getClassLoader().getResource(CONFIG_NAME)).build();
		}
		catch (IOException e) {
			logger.error(e.getMessage(), e);
			throw new Error(e);
		}

		config.getSerializationConfig().addDataSerializableFactory(SerializableFactory.ID, new SerializableFactory());

		config.getSerializationConfig().getSerializerConfigs().add(new SerializerConfig().setTypeClass(JsonNode.class)
				.setImplementation(JsonSerializer.makePlain(JsonNode.class)));
		config.getSerializationConfig().setUseNativeByteOrder(true);
		config.getSerializationConfig().setAllowUnsafe(true);

		return config;
	}

	public void shutdown() {
		if (Settings.INSTANCE.clusteringMode() != Mode.HAZELCAST) { return; }

		substance.shutdown();
	}

	protected ILock getLock(String key) {
		return substance.getLock(key);
	}

	protected <E> ITopic<E> getTopic(String name) {
		return substance.getTopic(name);
	}

	protected IdGenerator getIdGenerator(String name) {
		return substance.getIdGenerator(name);
	}

	protected <K, V> IMap<K, V> getMap(String name) {
		return substance.getMap(name);
	}

	protected String currentId() {
		return substance.getLocalEndpoint().getUuid();
	}

	public <V> ISet<V> getSet(String name) {
		return substance.getSet(name);
	}
}
