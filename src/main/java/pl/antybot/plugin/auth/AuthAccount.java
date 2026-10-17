package pl.antybot.plugin.auth;

public class AuthAccount {

    private final String username;
    private boolean premium;
    private String mojangUuid;
    private String passwordHash;
    private String salt;
    private String registeredIp;
    private String lastIp;
    private long lastLogin;
    private long registeredAt;

    public AuthAccount(String username) {
        this.username = username.toLowerCase();
    }

    public String getUsername() {
        return username;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public String getMojangUuid() {
        return mojangUuid;
    }

    public void setMojangUuid(String mojangUuid) {
        this.mojangUuid = mojangUuid;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getRegisteredIp() {
        return registeredIp;
    }

    public void setRegisteredIp(String registeredIp) {
        this.registeredIp = registeredIp;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }

    public boolean isRegistered() {
        return (passwordHash != null && !passwordHash.isEmpty()) || premium;
    }
}
