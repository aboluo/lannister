package net.anyflow.lannister.session;

import java.util.List;

import com.google.common.collect.Lists;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.admin.command.MessageFilter;
import net.anyflow.lannister.admin.command.SessionsFilter;
import net.anyflow.lannister.message.Message;
import net.anyflow.lannister.message.MessageFactory;
import net.anyflow.lannister.message.SenderTargetStatus;
import net.anyflow.lannister.topic.Topic;

public class MessageSender {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageSender.class);

	private final static List<MessageFilter> FILTERS = Lists.newArrayList(new SessionsFilter());

	private final Session session;
	private final ChannelHandlerContext ctx;

	protected MessageSender(Session session, ChannelHandlerContext ctx) {
		this.session = session;
		this.ctx = ctx;
	}

	protected static MqttQoS adjustQoS(MqttQoS topicQos, MqttQoS messageQos) {
		return topicQos.value() <= messageQos.value() ? topicQos : messageQos;
	}

	protected ChannelFuture send(MqttMessage message) {
		if (session.isConnected() == false) {
			logger.error("Message is not sent - Channel is inactive : {}", message);
			return null;
		}

		final String log = message.toString();
		return ctx.writeAndFlush(message).addListener(f -> {
			logger.debug("packet outgoing : {}", log);
		});
	}

	protected ChannelFuture sendPublish(Topic topic, Message message, boolean isRetain) {
		logger.debug("event arrived : [clientId:{}/message:{}]", session.clientId(), message.toString());

		// TODO what if returned topicSubscriptions are multiple?

		long tsCount = session.matches(message.topicName()).count();

		if (tsCount <= 0) {
			logger.error("Topic Subscription should exist but none! [clientId={}, topicName={}]", session.clientId(),
					message.topicName());
			return null;
		}

		if (session.isConnected() == false) { return null; }

		executefilters(message);

		final int originalMessageId = message.id();

		message.setId(session.nextMessageId());
		message.setRetain(isRetain);// [MQTT-3.3.1-8],[MQTT-3.3.1-9]

		if (message.qos() != MqttQoS.AT_MOST_ONCE) {
			topic.subscribers().get(session.clientId()).addSentMessageStatus(message.id(), originalMessageId,
					SenderTargetStatus.TO_PUB);
		}

		return send(MessageFactory.publish(message, false)).addListener(f -> {
			switch (message.qos()) {
			case AT_MOST_ONCE:
				return;

			case AT_LEAST_ONCE:
				topic.subscribers().get(session.clientId()).setSentMessageStatus(message.id(),
						SenderTargetStatus.TO_BE_REMOVED);
				return;

			case EXACTLY_ONCE:
				topic.subscribers().get(session.clientId()).setSentMessageStatus(message.id(),
						SenderTargetStatus.TO_REL);
				return;

			default:
				logger.error("Invalid QoS [QoS={}]", message.qos());
				return;
			}
		});
	}

	private static void executefilters(Message message) {
		for (MessageFilter filter : FILTERS) {
			filter.execute(message);
		}
	}
}