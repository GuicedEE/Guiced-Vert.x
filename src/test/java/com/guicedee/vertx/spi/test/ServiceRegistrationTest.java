package com.guicedee.vertx.spi.test;

import com.guicedee.vertx.VertXModule;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class ServiceRegistrationTest {
    @Test void classpathProvidersMatchModuleDescriptor() throws Exception {
        Module module = VertXModule.class.getModule();
        for (var service : module.getDescriptor().provides()) {
            String resource = "META-INF/services/" + service.service();
            try (var stream = module.getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var actual = reader.lines().map(line -> line.split("#", 2)[0].trim())
                        .filter(line -> !line.isEmpty()).collect(Collectors.toSet());
                assertEquals(Set.copyOf(service.providers()), actual, resource);
            }
        }
    }
}
