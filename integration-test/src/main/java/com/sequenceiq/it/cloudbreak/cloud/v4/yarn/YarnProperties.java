package com.sequenceiq.it.cloudbreak.cloud.v4.yarn;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "integrationtest.yarn")
public class YarnProperties {

    private String defaultBlueprintName;

    private String availabilityZone;

    private String region;

    private String location;

    private String queue;

    private String blueprintCdhVersion;

    private final Credential credential = new Credential();

    private final Instance instance = new Instance();

    private final Baseimage baseimage = new Baseimage();

    public String getDefaultBlueprintName() {
        return defaultBlueprintName;
    }

    public void setDefaultBlueprintName(String defaultBlueprintName) {
        this.defaultBlueprintName = defaultBlueprintName;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public Credential getCredential() {
        return credential;
    }

    public Instance getInstance() {
        return instance;
    }

    public String getBlueprintCdhVersion() {
        return blueprintCdhVersion;
    }

    public void setBlueprintCdhVersion(String blueprintCdhVersion) {
        this.blueprintCdhVersion = blueprintCdhVersion;
    }

    public Baseimage getBaseimage() {
        return baseimage;
    }

    public static class Credential {
        private String endpoint;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    public static class Instance {
        private Integer cpuCount;

        private Integer memory;

        private Integer volumeSize;

        private Integer volumeCount;

        public Integer getCpuCount() {
            return cpuCount;
        }

        public void setCpuCount(Integer cpuCount) {
            this.cpuCount = cpuCount;
        }

        public Integer getMemory() {
            return memory;
        }

        public void setMemory(Integer memory) {
            this.memory = memory;
        }

        public Integer getVolumeSize() {
            return volumeSize;
        }

        public void setVolumeSize(Integer volumeSize) {
            this.volumeSize = volumeSize;
        }

        public Integer getVolumeCount() {
            return volumeCount;
        }

        public void setVolumeCount(Integer volumeCount) {
            this.volumeCount = volumeCount;
        }
    }

    public static class Baseimage {
        private String imageId;

        public String getImageId() {
            return imageId;
        }

        public void setImageId(String imageId) {
            this.imageId = imageId;
        }
    }
}
