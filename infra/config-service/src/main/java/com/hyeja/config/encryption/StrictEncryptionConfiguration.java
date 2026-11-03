package com.hyeja.config.encryption;

import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
public class StrictEncryptionConfiguration {
    @Bean
    EnvironmentEncryptor strictEnvironmentEncryptor(TextEncryptorLocator locator) {
        var delegate = new CipherEnvironmentEncryptor(locator);
        delegate.setPrefixInvalidProperties(true);
        return environment -> {
            var decrypted = delegate.decrypt(environment);
            boolean invalid = decrypted.getPropertySources().stream().anyMatch(source ->
                    source.getSource().keySet().stream().anyMatch(key -> key.toString().startsWith("invalid.")));
            if (invalid) {
                // Do not return partial settings that allow a lower-priority/default secret to be used.
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Configuration decryption failed");
            }
            return decrypted;
        };
    }
}
