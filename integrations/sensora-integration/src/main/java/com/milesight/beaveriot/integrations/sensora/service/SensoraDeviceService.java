package com.milesight.beaveriot.integrations.sensora.service;

import com.milesight.beaveriot.context.api.DeviceServiceProvider;
import com.milesight.beaveriot.context.api.EntityValueServiceProvider;
import com.milesight.beaveriot.context.integration.model.*;
import com.milesight.beaveriot.context.integration.model.event.ExchangeEvent;
import com.milesight.beaveriot.context.integration.wrapper.AnnotatedEntityWrapper;
import com.milesight.beaveriot.context.integration.wrapper.AnnotatedTemplateEntityWrapper;
import com.milesight.beaveriot.eventbus.annotations.EventSubscribe;
import com.milesight.beaveriot.eventbus.api.Event;
import com.milesight.beaveriot.integrations.sensora.entity.DeviceEntity;
import com.milesight.beaveriot.integrations.sensora.entity.SensoraIntegrationEntities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SensoraDeviceService {
    @Autowired
    private DeviceServiceProvider deviceServiceProvider;

    @Autowired
    private EntityValueServiceProvider entityValueServiceProvider;

    public static final String INTEGRATION_ID = "sensora-integration";

    @Getter
    private static SensoraIntegrationEntities.DetectReport latestReport;

    @EventSubscribe(payloadKeyExpression = INTEGRATION_ID + ".integration.add_device.*", eventType = ExchangeEvent.EventType.CALL_SERVICE)
    public void onAddDevice(Event<SensoraIntegrationEntities.AddDevice> event) {
        SensoraIntegrationEntities.AddDevice addDevice = event.getPayload();
        String deviceName = addDevice.getAddDeviceName();
        String ip = event.getPayload().getIp();
        String sn = addDevice.getSn();


        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("ip",ip.replace(".", "_"));

        Device device = new DeviceBuilder(INTEGRATION_ID)
                .name(deviceName)
                .identifier(sn)
                .additional(additionalProperties)
                .entities(() -> new AnnotatedTemplateEntityBuilder(INTEGRATION_ID, sn).build(DeviceEntity.class))
                .build();

        try {
            deviceServiceProvider.save(device);
            log.info("Added device: {} with identifier: {}", deviceName, sn);
        } catch (Exception e) {
            log.error("Failed to save device: {}", e.getMessage());
            throw e;
        }
    }

    public Device saveDevice(SensoraIntegrationEntities.AddDevice addDevice) {
        String deviceName = addDevice.getName();
        String ip = addDevice.getIp();
        String sn = addDevice.getSn();

        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("ip",ip.replace(".", "_"));

        Device device = new DeviceBuilder(INTEGRATION_ID)
                .name(deviceName)
                .identifier(sn)
                .additional(additionalProperties)
                .entities(() -> new AnnotatedTemplateEntityBuilder(INTEGRATION_ID, sn).build(DeviceEntity.class))
                .build();

        try {
            deviceServiceProvider.save(device);
            log.info("Added device: {} with identifier: {}", deviceName, sn);
            return device;
        } catch (Exception e) {
            log.error("Failed to save device: {}", e.getMessage());
            throw e;
        }
    }

    public List<Device> searchDevices(String name, String identifier) {
        List<Device> allDevices = deviceServiceProvider.findAll(INTEGRATION_ID);
        return allDevices.stream()
                .filter(device -> {
                    boolean matchesName = name == null || device.getName().toLowerCase().contains(name.toLowerCase());
                    boolean matchesIdentifier = identifier == null || device.getIdentifier().toLowerCase().contains(identifier.toLowerCase());
                    return matchesName && matchesIdentifier;
                })
                .collect(Collectors.toList());
    }

    public void setDeviceStatusOnline(String deviceIdentifier) {

       Map<String, Object> statusInfo = getDeviceStatus(deviceIdentifier);
       String currentStatus = (String) statusInfo.get("status") ;
        String entityKey = statusInfo.containsKey("entityKey") ? (String) statusInfo.get("entityKey") : "N/A";
       log.debug("Current status for device {} is: {}. Entity key: {}", deviceIdentifier, currentStatus, statusInfo.get("identifier"));

        if ("ONLINE".equalsIgnoreCase(currentStatus)) {
            log.info("Device with identifier {} is already ONLINE", deviceIdentifier);
            return;
        }

        try {
            AnnotatedTemplateEntityWrapper<DeviceEntity> wrapper = new AnnotatedTemplateEntityWrapper<>(deviceIdentifier);
            log.debug("Attempting to update status for device {}. Using identifier: {}", deviceIdentifier, deviceIdentifier);
            wrapper.saveValues(Map.of(
                    DeviceEntity::getStatus, (long) DeviceEntity.DeviceStatus.ONLINE.ordinal()
            ));
            // Verify the update by re-fetching the status
            Map<String, Object> updatedStatusInfo = getDeviceStatus(deviceIdentifier);
            String updatedStatus = updatedStatusInfo != null && updatedStatusInfo.containsKey("status") ? (String) updatedStatusInfo.get("status") : "UNKNOWN";
            String updatedEntityKey = updatedStatusInfo != null && updatedStatusInfo.containsKey("entityKey") ? (String) updatedStatusInfo.get("entityKey") : "N/A";
            log.debug("Verified status for device {} after update: {}. Entity key: {}", deviceIdentifier, updatedStatus, updatedEntityKey);
            if ("ONLINE".equalsIgnoreCase(updatedStatus)) {
                log.info("Successfully set device with identifier {} to ONLINE status", deviceIdentifier);
            } else {
                log.warn("Failed to verify ONLINE status update for device {}: status is {}. Entity key: {}", deviceIdentifier, updatedStatus, updatedEntityKey);
            }
        } catch (Exception e) {
            log.error("Failed to set device {} status to ONLINE: {}. Check identifier and entity setup.", deviceIdentifier, e.getMessage());
            throw e;
        }
    }


    public Map<String, Object> getDeviceStatus(String deviceIdentifier) {
        Device device = deviceServiceProvider.findByIdentifier(deviceIdentifier, INTEGRATION_ID);
        if (device == null) {
            log.warn("Device with identifier {} not found", deviceIdentifier);
            return null;
        }

        if (device.getEntities().isEmpty()) {
            log.warn("No entities found for device with identifier {}", deviceIdentifier);
            return Map.of("identifier", deviceIdentifier, "status", "UNKNOWN");
        }

        Entity entity = device.getEntities().get(0);
        String entityKey = entity.getKey();
        log.debug("Fetching status for entity key: {}", entityKey);
        Long statusValue = (Long) entityValueServiceProvider.findValueByKey(entityKey);
        String status = statusValue != null ? DeviceEntity.DeviceStatus.values()[(int) (long) statusValue].name() : "UNKNOWN";
        return Map.of("identifier", deviceIdentifier, "status", status);
    }

    public Device deleteDeviceByIdentifier(String identifier) {
        Device device = deviceServiceProvider.findByIdentifier(identifier, INTEGRATION_ID);
        if (device == null || device.getId() == null) {
            log.warn("Device with identifier {} not found or has no ID", identifier);
            return device;
        }

        deviceServiceProvider.deleteById(device.getId());
        log.info("Successfully deleted device with ID: {} and identifier: {}", device.getId(), identifier);
        return device;
    }

    @EventSubscribe(payloadKeyExpression = INTEGRATION_ID + ".integration.delete_device", eventType = ExchangeEvent.EventType.CALL_SERVICE)
    public void onDeleteDevice(Event<SensoraIntegrationEntities.DeleteDevice> event) {
        Device device = event.getPayload().getDeletedDevice();
        deviceServiceProvider.deleteById(device.getId());
    }

    @EventSubscribe(payloadKeyExpression = INTEGRATION_ID + ".integration.benchmark", eventType = ExchangeEvent.EventType.CALL_SERVICE)
    public void doBenchmark(Event<SensoraIntegrationEntities> event) {
        // Mark benchmark starting
        new AnnotatedEntityWrapper<SensoraIntegrationEntities>()
                .saveValue(SensoraIntegrationEntities::getDetectStatus, (long) SensoraIntegrationEntities.DetectStatus.DETECTING.ordinal())
                .publishSync();

        // Start pinging
        final int timeout = 5000;
        List<Device> devices = deviceServiceProvider.findAll(INTEGRATION_ID);
        AtomicReference<Long> activeCount = new AtomicReference<>(0L);
        AtomicReference<Long> inactiveCount = new AtomicReference<>(0L);
        Long startTimestamp = System.currentTimeMillis();
        devices.forEach(device -> {
            boolean isSuccess = false;
            String ip = (String) device.getAdditional().get("ip");

            try {
                InetAddress inet = InetAddress.getByName(ip);
                if (inet.isReachable(timeout)) {
                    isSuccess = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            int deviceStatus = DeviceEntity.DeviceStatus.OFFLINE.ordinal();
            if (isSuccess) {
                activeCount.updateAndGet(v -> v + 1);
                deviceStatus = DeviceEntity.DeviceStatus.ONLINE.ordinal();
            } else {
                inactiveCount.updateAndGet(v -> v + 1);
            }

            // Update device status and SN
            String deviceIdentifier = device.getIdentifier(); // Assuming identifier includes SN
            new AnnotatedTemplateEntityWrapper<DeviceEntity>(deviceIdentifier)
                    .saveValues(Map.of(
                            DeviceEntity::getStatus, (long) deviceStatus
                    ));
        });
        Long endTimestamp = System.currentTimeMillis();

        // Mark benchmark done
        new AnnotatedEntityWrapper<SensoraIntegrationEntities>()
                .saveValue(SensoraIntegrationEntities::getDetectStatus, (long) SensoraIntegrationEntities.DetectStatus.STANDBY.ordinal())
                .publishSync();

        // Send report event
        new AnnotatedEntityWrapper<SensoraIntegrationEntities.DetectReport>().saveValues(Map.of(
                SensoraIntegrationEntities.DetectReport::getConsumedTime, endTimestamp - startTimestamp,
                SensoraIntegrationEntities.DetectReport::getOnlineCount, activeCount.get(),
                SensoraIntegrationEntities.DetectReport::getOfflineCount, inactiveCount.get()
        )).publishSync();
    }

    @EventSubscribe(payloadKeyExpression = INTEGRATION_ID + ".integration.detect_report.*", eventType = ExchangeEvent.EventType.REPORT_EVENT)
    public void listenDetectReport(Event<SensoraIntegrationEntities.DetectReport> event) {
        latestReport = event.getPayload();
        System.out.println("[Get-Report] " + event.getPayload()); // TODO: remove in production
        log.info("[Get-Report] Report - Consumed Time: {}ms, Online: {}, Offline: {}",
                latestReport.getConsumedTime(), latestReport.getOnlineCount(), latestReport.getOfflineCount());
    }

}
