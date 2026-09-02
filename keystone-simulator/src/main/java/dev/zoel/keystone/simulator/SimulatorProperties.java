package dev.zoel.keystone.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private String baseUrl = "http://localhost:8080";

    private String operatorUsername = "operator";

    private String operatorPassword;

    /** How many devices to bring through the full lifecycle concurrently. */
    private int deviceCount = 50;

    private String modelName = "SIM-ESP32-S3";

    /** Run the adversarial scenarios after the happy path. */
    private boolean runAttackScenarios = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getOperatorUsername() { return operatorUsername; }
    public void setOperatorUsername(String operatorUsername) { this.operatorUsername = operatorUsername; }

    public String getOperatorPassword() { return operatorPassword; }
    public void setOperatorPassword(String operatorPassword) { this.operatorPassword = operatorPassword; }

    public int getDeviceCount() { return deviceCount; }
    public void setDeviceCount(int deviceCount) { this.deviceCount = deviceCount; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    private String brokerUrl = "ssl://localhost:18883";

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

    /** Requires the broker to be up with mTLS configured. */
    private boolean runMtlsScenarios = true;

    public boolean isRunMtlsScenarios() { return runMtlsScenarios; }
    public void setRunMtlsScenarios(boolean runMtlsScenarios) { this.runMtlsScenarios = runMtlsScenarios; }

    public boolean isRunAttackScenarios() { return runAttackScenarios; }
    public void setRunAttackScenarios(boolean runAttackScenarios) { this.runAttackScenarios = runAttackScenarios; }
}
