package net.anyflow.lannister.messagehandler;

import java.util.Date;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.session.LiveSessions;
import net.anyflow.lannister.session.Session;

public class GenericMqttMessageHandler extends SimpleChannelInboundHandler<MqttMessage> {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GenericMqttMessageHandler.class);

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
		if (msg.decoderResult().isSuccess() == false) {
			logger.error("decoding MQTT message failed : {}", msg.decoderResult().cause().getMessage());
		}
		else {
			logger.debug("MQTT message incoming : {}", msg.toString());

			Session session = LiveSessions.SELF.getByChannelId(ctx.channel().id());
			if (session == null) {
				logger.error("None exist session message : {}", msg.toString());
				return;
			}

			session.setLastIncomingTime(new Date());

			switch (msg.fixedHeader().messageType()) {
			case DISCONNECT:
				LiveSessions.SELF.dispose(session);
				break;

			case PINGREQ:
				MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false,
						MqttQoS.AT_LEAST_ONCE, false, 0);

				session.send(new MqttMessage(fixedHeader));
				break;

			// PUBREC(5),
			// PUBREL(6),
			// PUBCOMP(7),
			// PINGRESP(13)// never incoming

			default:
				throw new IllegalArgumentException(msg.toString());
			}
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Session session = LiveSessions.SELF.getByChannelId(ctx.channel().id());
		if (session == null) {
			logger.error("session does not exist. [channelId={}]", ctx.channel().id());
			return;
		}

		LiveSessions.SELF.dispose(session, true);
	}
}