package com.example.airegistration.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private boolean enabled = true;
    private String tokenHeader = "X-Auth-Token";
    private Duration tokenTtl = Duration.ofHours(12);
    private Wechat wechat = new Wechat();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public void setWechat(Wechat wechat) {
        this.wechat = wechat;
    }

    public static class Wechat {
        private String appId = "";
        private String appSecret = "";
        private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getCode2SessionUrl() {
            return code2SessionUrl;
        }

        public void setCode2SessionUrl(String code2SessionUrl) {
            this.code2SessionUrl = code2SessionUrl;
        }
    }
}
