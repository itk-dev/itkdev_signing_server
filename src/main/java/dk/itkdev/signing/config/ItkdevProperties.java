package dk.itkdev.signing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "itkdev")
public class ItkdevProperties {

    private String hashSalt;
    private List<String> allowedDomains = new ArrayList<>();
    private String signedDocumentsDir = "./signed-documents/";
    private String sourceDocumentsDir = "./signers-documents/";
    private boolean debug = false;
    private boolean testPageEnabled = false;

    public String getHashSalt() {
        return hashSalt;
    }

    public void setHashSalt(String hashSalt) {
        this.hashSalt = hashSalt;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public String getSignedDocumentsDir() {
        return signedDocumentsDir;
    }

    public void setSignedDocumentsDir(String signedDocumentsDir) {
        this.signedDocumentsDir = signedDocumentsDir;
    }

    public String getSourceDocumentsDir() {
        return sourceDocumentsDir;
    }

    public void setSourceDocumentsDir(String sourceDocumentsDir) {
        this.sourceDocumentsDir = sourceDocumentsDir;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isTestPageEnabled() {
        return testPageEnabled;
    }

    public void setTestPageEnabled(boolean testPageEnabled) {
        this.testPageEnabled = testPageEnabled;
    }
}
