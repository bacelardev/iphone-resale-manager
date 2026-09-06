package io.github.bacelardev.iphoneresale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap")
public final class BootstrapProperties {

    private boolean enabled;
    private String name = "";
    private String username = "";
    private String password = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "BootstrapProperties{enabled=" + enabled + ", name='[redacted]', "
                + "username='[redacted]', password='[redacted]'}";
    }
}
