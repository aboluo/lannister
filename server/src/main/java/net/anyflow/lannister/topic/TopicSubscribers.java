package net.anyflow.lannister.topic;

import java.util.List;
import java.util.concurrent.locks.Lock;

import com.google.common.collect.Lists;

import net.anyflow.lannister.cluster.ClusterDataFactory;
import net.anyflow.lannister.cluster.Map;
import net.anyflow.lannister.cluster.SerializableStringList;

public class TopicSubscribers {
	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TopicSubscribers.class);

	private final Map<String, TopicSubscriber> data;
	private final Map<String, SerializableStringList> topicnameIndex;
	private final Map<String, SerializableStringList> clientidIndex;

	private final Lock putLock;
	private final Lock removeLock;

	protected TopicSubscribers() {
		this.data = ClusterDataFactory.INSTANCE.createMap("TopicSubscribers");
		this.topicnameIndex = ClusterDataFactory.INSTANCE.createMap("TopicSubscribers_topicnameIndex");
		this.clientidIndex = ClusterDataFactory.INSTANCE.createMap("TopicSubscribers_clientidIndex");

		this.putLock = ClusterDataFactory.INSTANCE.createLock("TopicSubscribers_put");
		this.removeLock = ClusterDataFactory.INSTANCE.createLock("TopicSubscribers_remove");
	}

	public static String key(String topicName, String clientId) {
		return topicName + "_" + clientId;
	}

	public boolean constainsKey(String topicName, String clientId) {
		return data.containsKey(key(topicName, clientId));
	}

	public void put(TopicSubscriber topicSubscriber) {
		if (topicSubscriber == null) { return; }

		putLock.lock();
		try {
			this.data.put(topicSubscriber.key(), topicSubscriber);

			SerializableStringList clientIds = this.topicnameIndex.get(topicSubscriber.topicName());
			if (clientIds == null) {
				clientIds = new SerializableStringList();
				this.topicnameIndex.put(topicSubscriber.topicName(), clientIds);
			}
			clientIds.add(topicSubscriber.clientId());

			SerializableStringList topicNames = this.clientidIndex.get(topicSubscriber.clientId());
			if (topicNames == null) {
				topicNames = new SerializableStringList();
				this.clientidIndex.put(topicSubscriber.clientId(), topicNames);
			}
			topicNames.add(topicSubscriber.topicName());
		}
		finally {
			putLock.unlock();
		}
	}

	public TopicSubscriber getBy(String topicName, String clientId) {
		return data.get(key(topicName, clientId));
	}

	public List<String> getClientIdsOf(String topicName) {
		List<String> ret = topicnameIndex.get(topicName);

		return ret == null ? Lists.newArrayList() : ret;
	}

	public List<String> getTopicNamesOf(String clientId) {
		List<String> ret = clientidIndex.get(clientId);

		return ret == null ? Lists.newArrayList() : ret;
	}

	public TopicSubscriber removeByKey(String topicName, String clientId) {
		return removeByKey(key(topicName, clientId));
	}

	private TopicSubscriber removeByKey(String key) {
		removeLock.lock();

		try {
			TopicSubscriber removed = this.data.remove(key);
			if (removed == null) { return null; }

			this.topicnameIndex.remove(removed.topicName());
			this.clientidIndex.remove(removed.clientId());

			return removed;
		}
		finally {
			removeLock.unlock();
		}
	}

	public List<String> removeByClientId(String clientId) {
		removeLock.lock();

		try {
			SerializableStringList topicNames = this.clientidIndex.remove(clientId);
			if (topicNames == null) { return Lists.newArrayList(); }

			topicNames.forEach(topicName -> this.topicnameIndex.get(topicName).remove(clientId));
			topicNames.stream().map(topicName -> key(topicName, clientId)).forEach(key -> data.remove(key));

			return topicNames;
		}
		finally {
			removeLock.unlock();
		}
	}

	public boolean containsClientId(String clientId) {
		return this.clientidIndex.containsKey(clientId);
	}

	public void updateByTopicName(String topicName) {
		TopicSubscription.NEXUS.topicFilters().stream()
				.filter(topicFilter -> TopicMatcher.match(topicFilter, topicName))
				.forEach(topicFilter -> TopicSubscription.NEXUS.getClientIdsOf(topicFilter)
						.forEach(clientId -> TopicSubscriber.NEXUS.put(new TopicSubscriber(clientId, topicName))));
	}

	public void removeByTopicFilter(String clientId, String topicFilter) {
		List<String> topicFilters = TopicSubscription.NEXUS.getTopicFiltersOf(clientId);
		topicFilters.remove(topicFilter);

		this.getTopicNamesOf(clientId).stream().filter(topicName -> TopicMatcher.match(topicFilter, topicName))
				.filter(topicName -> !topicFilters.stream().anyMatch(item -> TopicMatcher.match(item, topicName)))
				.forEach(topicName -> TopicSubscriber.NEXUS.removeByKey(topicName, clientId));
	}
}