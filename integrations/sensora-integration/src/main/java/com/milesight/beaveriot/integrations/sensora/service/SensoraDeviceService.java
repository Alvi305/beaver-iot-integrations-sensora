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

        String deviceIdentifier = ip.replace(".", "_") + "-" + sn.replace(".", "_");

        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("sn",sn);

        Device device = new DeviceBuilder(INTEGRATION_ID)
                .name(deviceName)
                .identifier(deviceIdentifier)
                .additional(additionalProperties)
                .entities(() -> new AnnotatedTemplateEntityBuilder(INTEGRATION_ID, deviceIdentifier).build(DeviceEntity.class))
                .build();

        try {
            deviceServiceProvider.save(device);
            log.info("Added device: {} with identifier: {}", deviceName, deviceIdentifier);
        } catch (Exception e) {
            log.error("Failed to save device: {}", e.getMessage());
            throw e;
        }
    }

    public Device saveDevice(SensoraIntegrationEntities.AddDevice addDevice) {
        String deviceName = addDevice.getName();
        String ip = addDevice.getIp();
        String sn = addDevice.getSn();

        String deviceIdentifier = ip.replace(".", "_") + "-" + sn.replace(".", "_");

        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("sn", sn);

        Device device = new DeviceBuilder(INTEGRATION_ID)
                .name(deviceName)
                .identifier(deviceIdentifier)
                .additional(additionalProperties)
                .entities(() -> new AnnotatedTemplateEntityBuilder(INTEGRATION_ID, deviceIdentifier).build(DeviceEntity.class))
                .build();

        try {
            deviceServiceProvider.save(device);
            log.info("Added device: {} with identifier: {}", deviceName, deviceIdentifier);
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
            String sn = (String) device.getAdditional().get("sn");
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
                            DeviceEntity::getStatus, (long) deviceStatus,
                            DeviceEntity::getSn, sn
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
