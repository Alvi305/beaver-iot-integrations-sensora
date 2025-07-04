package com.milesight.beaveriot.integrations.sensora.controller;

import com.milesight.beaveriot.base.response.ResponseBody;
import com.milesight.beaveriot.base.response.ResponseBuilder;
import com.milesight.beaveriot.context.api.DeviceServiceProvider;
import com.milesight.beaveriot.context.api.EntityValueServiceProvider;
import com.milesight.beaveriot.context.integration.model.Device;
import com.milesight.beaveriot.context.integration.model.event.ExchangeEvent;
import com.milesight.beaveriot.eventbus.EventBus;
import com.milesight.beaveriot.integrations.sensora.entity.DeviceEntity;
import com.milesight.beaveriot.integrations.sensora.entity.SensoraIntegrationEntities;
import com.milesight.beaveriot.integrations.sensora.service.LoRaMqttSubscription;
import com.milesight.beaveriot.integrations.sensora.service.SensoraDeviceService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.milesight.beaveriot.context.integration.model.event.ExchangeEvent.EventType.CALL_SERVICE;
import static com.milesight.beaveriot.integrations.sensora.service.SensoraDeviceService.INTEGRATION_ID;

@RestController
@RequestMapping("/" + INTEGRATION_ID)
@Slf4j
public class SensoraIntegrationController {
    @Autowired
    private DeviceServiceProvider deviceServiceProvider;

    @Autowired
    private EntityValueServiceProvider entityValueServiceProvider;

    @Autowired
    private SensoraDeviceService sensoraDeviceService;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private LoRaMqttSubscription loRaMqttSubscription;


    @GetMapping("/active-count")
    public ResponseBody<CountResponse> getActiveDeviceCount() {
        List<String> statusEntityKeys = new ArrayList<>();
        deviceServiceProvider.findAll(INTEGRATION_ID).forEach(device -> statusEntityKeys.add(device.getEntities().get(0).getKey()));
        Long count = entityValueServiceProvider
                .findValuesByKeys(statusEntityKeys)
                .values()
                .stream()
                .map(n -> (long) n)
                .filter(status -> status == DeviceEntity.DeviceStatus.ONLINE.ordinal())
                .count();
        CountResponse resp = new CountResponse();
        resp.setCount(count);
        return ResponseBuilder.success(resp);
    }

    @GetMapping("/report")
    public ResponseEntity<SensoraIntegrationEntities.DetectReport> getReport() {
        return ResponseEntity.ok(SensoraDeviceService.getLatestReport());
    }

    @PostMapping("/device")
    public ResponseEntity<Device> addDevice(@RequestBody Map<String, Object> payload) {
        log.info("Received payload for addDevice: {}", payload);

        SensoraIntegrationEntities.AddDevice addDevice = new SensoraIntegrationEntities.AddDevice(payload);

        Device device = sensoraDeviceService.saveDevice(addDevice);

        ExchangeEvent event = new ExchangeEvent(CALL_SERVICE, addDevice);
        eventBus.publish(event);

        log.info("Returning device: {}", device);
        return ResponseEntity.ok(device);
    }

    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> searchDevices(
            @RequestParam(required = false, name = "name") String name,
            @RequestParam(required = false, name = "identifier") String identifier) {
        log.info("Searching devices with name: {}, identifier: {}", name, identifier);
        List<Device> devices = sensoraDeviceService.searchDevices(name, identifier);
        log.info("Found {} devices", devices.size());
        List<Map<String, Object>> response = devices.stream().map(device -> {
            Map<String, Object> deviceInfo = new HashMap<>();
            deviceInfo.put("id", device.getId());
            deviceInfo.put("integrationId", device.getIntegrationId());
            deviceInfo.put("name", device.getName());
            deviceInfo.put("identifier", device.getIdentifier());
            deviceInfo.put("additional", device.getAdditional());
            // Fetch status using the service method
            Map<String, Object> statusInfo = sensoraDeviceService.getDeviceStatus(device.getIdentifier());
            deviceInfo.put("status", statusInfo.get("status"));
            return deviceInfo;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/devices/{identifier}/status/online")
    public ResponseEntity<String> setDeviceStatusOnline(@PathVariable String identifier) {
        try {
            sensoraDeviceService.setDeviceStatusOnline(identifier);
            log.info("Successfully set device status to ONLINE for identifier: {}", identifier);
            return ResponseEntity.ok("Device status set to ONLINE successfully");
        } catch (Exception e) {
            log.error("Failed to set device status to ONLINE for identifier {}: {}", identifier, e.getMessage());
            return ResponseEntity.status(500).body("Failed to set device status: " + e.getMessage());
        }
    }

    @DeleteMapping("/devices/{identifier}")
    public ResponseEntity<String> deleteDevice(@PathVariable String identifier) {
        Device device = deviceServiceProvider.findByIdentifier(identifier, INTEGRATION_ID);
        if (device == null || device.getId() == null) {
            log.warn("Device with identifier {} not found", identifier);
            return ResponseEntity.status(404).body("Device not found");
        }

        sensoraDeviceService.deleteDeviceByIdentifier(device.getIdentifier());

        SensoraIntegrationEntities.DeleteDevice deleteDevice = new SensoraIntegrationEntities.DeleteDevice();
        deleteDevice.setDeletedDevice(device);
        ExchangeEvent event = new ExchangeEvent(ExchangeEvent.EventType.CALL_SERVICE, deleteDevice);
        eventBus.publish(event);



        return ResponseEntity.ok("Deletion request for device with identifier " + identifier + " has been processed");
    }

    @GetMapping("/sensor-data")
    public Map<String, Map<String, Object>> getSensorData() {
        return loRaMqttSubscription.getLatestSensorData();
    }

    @Data
    public static class CountResponse {
        private Long count;
    }
}
