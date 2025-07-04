package com.milesight.beaveriot.integrations.sensora.entity;
import com.milesight.beaveriot.context.integration.context.AddDeviceAware;
import com.milesight.beaveriot.context.integration.context.DeleteDeviceAware;
import com.milesight.beaveriot.context.integration.entity.annotation.Attribute;
import com.milesight.beaveriot.context.integration.entity.annotation.Entities;
import com.milesight.beaveriot.context.integration.entity.annotation.Entity;
import com.milesight.beaveriot.context.integration.entity.annotation.IntegrationEntities;
import com.milesight.beaveriot.context.integration.enums.AccessMod;
import com.milesight.beaveriot.context.integration.enums.EntityType;
import com.milesight.beaveriot.context.integration.model.Device;
import com.milesight.beaveriot.context.integration.model.ExchangePayload;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

import static com.milesight.beaveriot.integrations.sensora.service.SensoraDeviceService.INTEGRATION_ID;

@Data
@EqualsAndHashCode(callSuper = true)
@IntegrationEntities
public class SensoraIntegrationEntities extends  ExchangePayload {
    @Entity(type = EntityType.SERVICE, name = "Device Connection Benchmark", identifier = "benchmark")
    private Benchmark benchmark;

    @Entity(type = EntityType.PROPERTY, name = "Detect Status", identifier = "detect_status", attributes = @Attribute(enumClass = DetectStatus.class), accessMod = AccessMod.R)
    private Long detectStatus;

    @Entity(type = EntityType.EVENT, name = "Detect Report", identifier = "detect_report")
    private DetectReport detectReport;

    @Entity(type = EntityType.SERVICE, identifier = "add_device", visible = false)
    private AddDevice addDevice;

    @Entity(type = EntityType.SERVICE, identifier = "delete_device", visible = false)
    private DeleteDevice deleteDevice;


    @Data
    @EqualsAndHashCode(callSuper = true)
    @Entities
    public static class DetectReport extends ExchangePayload {
        @Entity
        private Long consumedTime;

        @Entity
        private Long onlineCount;

        @Entity
        private Long offlineCount;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Entities
    public static class AddDevice extends ExchangePayload implements AddDeviceAware {

        @Entity(type = EntityType.PROPERTY, name = "Device Name", accessMod = AccessMod.RW)
        private String name;

        @Entity(type = EntityType.PROPERTY, name = "IP Address", accessMod = AccessMod.RW, attributes = @Attribute(lengthRange = "7,15"))
        private String ip;

        @Entity(type = EntityType.PROPERTY, name = "Serial Number", accessMod = AccessMod.RW, attributes = @Attribute(lengthRange = "12,16"))
        private String sn;

        public AddDevice(Map<String, Object> payload) {
            this.name = (String) payload.get("name");
            if (payload.get("param_entities") instanceof Map) {
                Map<String, Object> paramEntities = (Map<String, Object>) payload.get("param_entities");
                this.ip = (String) paramEntities.get(INTEGRATION_ID + ".integration.add_device.ip");
                this.sn = (String) paramEntities.get(INTEGRATION_ID + ".integration.add_device.sn");
            }

        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Entities
    public static class DeleteDevice extends ExchangePayload implements DeleteDeviceAware {
        private Device deletedDevice;

        public void setDeletedDevice(Device device) {
            this.deletedDevice = device;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Entities
    public static class Benchmark extends ExchangePayload {
    }

    public enum DetectStatus {
        STANDBY, DETECTING;
    }
}
