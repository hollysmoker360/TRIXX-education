package com.schoolsphere.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI schoolSphereOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trixx Education API")
                        .version("v1")
                        .description("Hybrid multi-school management and learning platform. "
                                + "Send the access token as Bearer and select a school with the X-School-Id header."))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    @Bean
    public OpenApiCustomizer schoolHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getParameters() == null
                            || operation.getParameters().stream().noneMatch(p -> "X-School-Id".equals(p.getName()))) {
                        operation.addParametersItem(new HeaderParameter()
                                .name("X-School-Id")
                                .description("Selected school id (a membership of the current user)")
                                .required(false));
                    }
                }));
    }
}
