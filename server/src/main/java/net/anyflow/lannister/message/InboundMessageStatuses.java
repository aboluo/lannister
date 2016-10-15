package net.anyflow.lannister.message;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.google.common.collect.Lists;

import net.anyflow.lannister.cluster.ClusterDataFactory;
import net.anyflow.lannister.cluster.Map;
import net.anyflow.lannister.cluster.SerializableIntegerList;
import net.anyflow.lannister.cluster.SerializableStringList;

public class InboundMessageStatuses {
	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InboundMessageStatuses.class);

	private final Map<String, InboundMessageStatus> data;
	private final Map<Integer, SerializableStringList> messageidIndex;
	private final Map<String, SerializableIntegerList> clientidIndex;

	private final Lock putLock;
	private final Lock removeLock;

	protected InboundMessageStatuses() {
		this.data = ClusterDataFactory.INSTANCE.createMap("InboundMessageStatuses_data");
		this.messageidIndex = ClusterDataFactory.INSTANCE.createMap("InboundMessageStatuses_messageidIndex");
		this.clientidIndex = ClusterDataFactory.INSTANCE.createMap("InboundMessageStatuses_clientidIndex");

		this.putLock = ClusterDataFactory.INSTANCE.createLock("InboundMessageStatuses_putLock");
		this.removeLock = ClusterDataFactory.INSTANCE.createLock("InboundMessageStatuses_removeLock");
	}

	public static String key(Integer messageId, String clientId) {
		return clientId + "_" + Integer.toString(messageId);
	}

	public Set<String> keySet() {
		return data.keySet();
	}

	public int size() {
		return data.size();
	}

	public void put(InboundMessageStatus inboundMessageStatus) {
		if (inboundMessageStatus == null) { return; }

		putLock.lock();
		try {
			this.data.put(inboundMessageStatus.key(), inboundMessageStatus);

			SerializableStringList clientIds = this.messageidIndex.get(inboundMessageStatus.messageId());
			if (clientIds == null) {
				clientIds = new SerializableStringList();
				this.messageidIndex.put(inboundMessageStatus.messageId(), clientIds);
			}
			clientIds.add(inboundMessageStatus.clientId());

			SerializableIntegerList messageIds = this.clientidIndex.get(inboundMessageStatus.clientId());
			if (messageIds == null) {
				messageIds = new SerializableIntegerList();
				this.clientidIndex.put(inboundMessageStatus.clientId(), messageIds);
			}
			messageIds.add(inboundMessageStatus.messageId());

			MessageReferenceCounts.INSTANCE.retain(inboundMessageStatus.messageKey());
		}
		finally {
			putLock.unlock();
		}
	}

	public Set<Integer> messageIds() {
		return messageidIndex.keySet();
	}

	public InboundMessageStatus getBy(Integer messageId, String clientId) {
		return data.get(key(messageId, clientId));
	}

	public InboundMessageStatus getByKey(String key) {
		return data.get(key);
	}

	public List<String> getClientIdsOf(Integer messageId) {
		List<String> ret = messageidIndex.get(messageId);

		return ret == null ? Lists.newArrayList() : ret;
	}

	public List<Integer> getMessageIdsOf(String clientId) {
		List<Integer> ret = clientidIndex.get(clientId);

		return ret == null ? Lists.newArrayList() : ret;
	}

	public InboundMessageStatus removeByKey(Integer messageId, String clientId) {
		return removeByKey(key(messageId, clientId));
	}

	private InboundMessageStatus removeByKey(String key) {
		removeLock.lock();

		try {
			InboundMessageStatus removed = this.data.remove(key);
			if (removed == null) { return null; }

			this.messageidIndex.remove(removed.messageId());
			this.clientidIndex.remove(removed.clientId());

			MessageReferenceCounts.INSTANCE.release(removed.messageKey());

			return removed;
		}
		finally {
			removeLock.unlock();
		}
	}

	public List<Integer> removeByClientId(String clientId) {
		removeLock.lock();

		try {
			SerializableIntegerList messageIds = this.clientidIndex.remove(clientId);
			if (messageIds == null) { return Lists.newArrayList(); }

			messageIds.forEach(messageId -> this.messageidIndex.get(messageId).remove(clientId));
			messageIds.stream().map(messageId -> key(messageId, clientId)).forEach(key -> {
				InboundMessageStatus removed = data.remove(key);
				MessageReferenceCounts.INSTANCE.release(removed.messageKey());
			});

			return messageIds;
		}
		finally {
			removeLock.unlock();
		}
	}

	public boolean containsClientId(String clientId) {
		return this.clientidIndex.containsKey(clientId);
	}

	public boolean containsKey(Integer messageId, String clientId) {
		return data.containsKey(key(messageId, clientId));
	}

	public void update(Integer messageId, String clientId, InboundMessageStatus.Status targetStatus) {
		String key = key(messageId, clientId);

		InboundMessageStatus status = data.get(key);
		if (status == null) { return; }

		status.status(targetStatus);

		data.put(key, status);
	}
}
