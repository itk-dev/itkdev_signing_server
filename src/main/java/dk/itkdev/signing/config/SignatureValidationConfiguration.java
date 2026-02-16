package dk.itkdev.signing.config;

import dk.gov.nemlogin.signing.validation.model.SignatureValidationContext;
import dk.gov.nemlogin.signing.validation.service.SignatureValidationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureValidationConfiguration {

    private final String validationServiceUrl;

    public SignatureValidationConfiguration(@Qualifier("validationServiceUrl") String validationServiceUrl) {
        this.validationServiceUrl = validationServiceUrl;
    }

    @Bean
    public SignatureValidationService signatureValidationService() {
        return new SignatureValidationService();
    }

    @Bean
    public SignatureValidationContext.Builder signatureValidationContextBuilder() {
        return SignatureValidationContext.builder()
                .setValidationServiceUrl(validationServiceUrl)
                .setIgnoreSsl(true);
    }
}
