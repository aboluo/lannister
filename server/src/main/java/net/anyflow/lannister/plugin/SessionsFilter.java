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

package net.anyflow.lannister.plugin;

import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.message.IMessage;
import net.anyflow.lannister.plugin.MessageFilter;
import net.anyflow.lannister.plugin.Plugin;
import net.anyflow.lannister.session.Session;
import net.anyflow.menton.http.HttpRequestHandler;

@HttpRequestHandler.Handles(paths = { "sessions" }, httpMethods = { "GET" })
public class SessionsFilter extends HttpRequestHandler implements MessageFilter {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SessionsFilter.class);

	private byte[] liveBinary() {
		try {
			return (new ObjectMapper()).writeValueAsBytes(Session.NEXUS.map().values().stream()
					.filter(s -> s.isConnected()).collect(Collectors.toMap(Session::clientId, Function.identity())));
		}
		catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	private byte[] allBinary() {
		try {
			return (new ObjectMapper()).writeValueAsBytes(Session.NEXUS.map());
		}
		catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	private String liveString() {
		try {
			return (new ObjectMapper()).writeValueAsString(Session.NEXUS.map().values().stream()
					.filter(s -> s.isConnected()).collect(Collectors.toMap(Session::clientId, Function.identity())));
		}
		catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	private String allString() {
		try {
			return (new ObjectMapper()).writeValueAsString(Session.NEXUS.map());
		}
		catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void execute(IMessage message) {
		if (message == null || message.topicName().startsWith("$COMMAND/GET/sessions") == false) { return; }

		message.setQos(MqttQoS.AT_MOST_ONCE);

		if (message.topicName().equals("$COMMAND/GET/sessions")) {
			message.setMessage(allBinary());
		}
		else if (message.topicName().equals("$COMMAND/GET/sessions?filter=live")) {
			message.setMessage(liveBinary());
		}
		else if (message.topicName().equals("$COMMAND/GET/sessions?filter=all")) {
			message.setMessage(allBinary());
		}
	}

	@Override
	public String service() {
		String filter = Strings.nullToEmpty(httpRequest().parameter("filter"));

		switch (filter) {
		case "":
		case "live":
			return liveString();

		case "all":
			return allString();

		default:
			return null;
		}
	}

	@Override
	public Plugin clone() {
		return new SessionsFilter();
	}
}