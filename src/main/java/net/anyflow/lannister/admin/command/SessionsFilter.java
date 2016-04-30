package net.anyflow.lannister.admin.command;

import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.message.Message;
import net.anyflow.lannister.session.Session;

public class SessionsFilter implements MessageFilter {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SessionsFilter.class);

	private byte[] live() {
		try {
			return (new ObjectMapper()).writeValueAsBytes(Session.NEXUS.map().values().stream()
					.filter(s -> s.isConnected()).collect(Collectors.toMap(Session::clientId, Function.identity())));
		}
		catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	private byte[] all() {
		try {
			return (new ObjectMapper()).writeValueAsBytes(Session.NEXUS.map());
		}
		catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void execute(Message message) {
		if (message == null || message.topicName().startsWith("$COMMAND/GET/sessions") == false) { return; }

		message.setQos(MqttQoS.AT_MOST_ONCE);

		if (message.topicName().equals("$COMMAND/GET/sessions")) {
			message.setMessage(all());
		}
		else if (message.topicName().equals("$COMMAND/GET/sessions?filter=live")) {
			message.setMessage(live());
		}
		else if (message.topicName().equals("$COMMAND/GET/sessions?filter=all")) {
			message.setMessage(all());
		}
	}
}