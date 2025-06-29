package com.milesight.beaveriot.integrations.sensora.controller;

import com.milesight.beaveriot.base.response.ResponseBody;
import com.milesight.beaveriot.base.response.ResponseBuilder;
import com.milesight.beaveriot.context.api.DeviceServiceProvider;
import com.milesight.beaveriot.context.api.EntityValueServiceProvider;
import com.milesight.beaveriot.context.integration.model.AnnotatedTemplateEntityBuilder;
import com.milesight.beaveriot.context.integration.model.Device;
import com.milesight.beaveriot.context.integration.model.DeviceBuilder;
import com.milesight.beaveriot.context.integration.model.Entity;
import com.milesight.beaveriot.context.integration.model.event.ExchangeEvent;
import com.milesight.beaveriot.eventbus.EventBus;
import com.milesight.beaveriot.eventbus.api.Event;
import com.milesight.beaveriot.eventbus.api.IdentityKey;
import com.milesight.beaveriot.integrations.sensora.entity.DeviceEntity;
import com.milesight.beaveriot.integrations.sensora.entity.SensoraIntegrationEntities;
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

        // Create AddDevice instance from payload
        SensoraIntegrationEntities.AddDevice addDevice = new SensoraIntegrationEntities.AddDevice(payload);

        // Use service to save device and get the result
        Device device = sensoraDeviceService.saveDevice(addDevice);

        // Optional: Publish event for additional processing (if needed)
        ExchangeEvent event = new ExchangeEvent(CALL_SERVICE, addDevice);
        eventBus.publish(event);

        log.info("Returning device: {}", device);
        return ResponseEntity.ok(device);
    }

    @GetMapping("/devices")
    public ResponseEntity<List<Device>> searchDevices(
            @RequestParam(required = false, name = "name") String name,
            @RequestParam(required = false, name = "identifier") String identifier) {
        log.info("Searching devices with name: {}, identifier: {}", name, identifier);
        List<Device> devices = sensoraDeviceService.searchDevices(name, identifier);
        log.info("Found {} devices", devices.size());
        return ResponseEntity.ok(devices);
    }

    @Data
    public  class CountResponse {
        private Long count;
    }
}
