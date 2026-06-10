package com.pfe.gateway.Config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                contact = @Contact(
                        name = "mariem",
                        email = "mariemchahem@gmail.com",
                        url = ""
                ),
                description = "OpenApi documentation ",
                title = "OpenApi ",
                version = "1.0",
                license = @License(
                        name = "Licence name",
                        url = "https://some-url.com"
                ),
                termsOfService = "Terms of service"
        ),
        servers = {
                @Server(
                        description = "Local ENV",
                        url = "http://localhost:8222"
                ),
                @Server(
                        description = "PROD ENV",
                        url = ""
                )
        }

)
public class OpenApiConfig {
}

