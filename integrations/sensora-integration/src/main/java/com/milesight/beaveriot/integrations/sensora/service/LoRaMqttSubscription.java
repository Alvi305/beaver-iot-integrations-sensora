package com.milesight.beaveriot.integrations.sensora.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milesight.beaveriot.context.api.MqttPubSubServiceProvider;
import com.milesight.beaveriot.context.mqtt.model.MqttMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class LoRaMqttSubscription {

    @Autowired
    private MqttPubSubServiceProvider mqttPubSubServiceProvider;

    @Value("${mqtt.broker.username:test}") // Inject username from config, default to 'test'
    private String username;

    @Value("${mqtt.broker.topic-subpath:em320th/data}") // Inject topic subpath, default to 'em320th/data'
    private String topicSubPath;

    private final Map<String, Map<String, Object>> latestSensorData = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        String adjustedTopicSubPath = topicSubPath;
        mqttPubSubServiceProvider.subscribe(username, adjustedTopicSubPath, this::onMessage, true); // Shared mode
        log.info("Subscribed to MQTT topic {} with username {}",
                mqttPubSubServiceProvider.getFullTopicName(username, adjustedTopicSubPath), username);


        log.debug("MQTT Broker Info: {}", mqttPubSubServiceProvider.getMqttBrokerInfo());
    }

    public void onMessage(MqttMessage message) {
        log.info("Received message: {}", message);
        String topic = message.getFullTopicName();
        byte[] payload = message.getPayload();
        String payloadStr = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        log.info("Received LoRa gateway message - Topic: {}, Payload: {}", topic, payloadStr);

        try {
            Map<String, Object> sensorData = objectMapper.readValue(payloadStr, Map.class);
            double humidity = (double) sensorData.getOrDefault("humidity", 0.0);
            double temperature = (double) sensorData.getOrDefault("temperature", 0.0);

            Map<String, Object> dataEntry = new HashMap<>();
            dataEntry.put("humidity", humidity);
            dataEntry.put("temperature", temperature);
            latestSensorData.put(topic, dataEntry);

            log.info("Extracted sensor data - Topic: {}, Humidity: {}, Temperature: {}", topic, humidity, temperature);
        } catch (Exception e) {
            log.error("Failed to parse LoRa gateway message payload: {}", e.getMessage());
        }
    }

    // Getter for controller access
    public Map<String, Map<String, Object>> getLatestSensorData() {
        return new HashMap<>(latestSensorData);
    }
}