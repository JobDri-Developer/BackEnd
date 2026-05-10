package com.jobdri.jobdri_api.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI swagger() {
        Info info = new Info()
                .title("CEOS 23rd JobDri API")
                .description("ceos-23rd jobdri swagger")
                .version("1.0.0");

        String jwtSchemeName = "JWT TOKEN";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        Paths paths = new Paths()
                .addPathItem("/oauth2/authorization/google", new PathItem().get(
                        new Operation()
                                .tags(java.util.List.of("Auth"))
                                .summary("Google OAuth2 로그인 시작")
                                .description("프론트엔드는 이 주소로 이동만 하면 됩니다. 이후 구글 로그인 화면 이동과 인가 코드 요청은 Spring Security가 자동으로 처리합니다.")
                                .responses(new ApiResponses()
                                        .addApiResponse("302", new ApiResponse()
                                                .description("구글 OAuth2 인증 화면으로 리다이렉트")
                                        ))
                ))
                .addPathItem("/login/oauth2/code/google", new PathItem().get(
                        new Operation()
                                .tags(java.util.List.of("Auth"))
                                .summary("Google OAuth2 콜백")
                                .description("구글 인증 후 Spring Security가 처리하는 콜백 엔드포인트입니다. 성공 시 프론트엔드 리다이렉트 URI로 accessToken, refreshToken을 붙여 리다이렉트하고, 실패 시 error, message를 붙여 리다이렉트합니다.")
                                .responses(new ApiResponses()
                                        .addApiResponse("302", new ApiResponse()
                                                .description("프론트엔드로 리다이렉트")
                                                .content(new Content().addMediaType("text/plain", new MediaType()
                                                        .schema(new StringSchema())
                                                        .addExamples("success", new Example().value("http://localhost:3000/oauth2/redirect?accessToken=...&refreshToken=..."))
                                                        .addExamples("failure", new Example().value("http://localhost:3000/oauth2/redirect?error=oauth2_login_failed&message=..."))
                                                ))
                                        )
                                        .addApiResponse("400", new ApiResponse()
                                                .description("구글 사용자 정보 조회 실패 또는 잘못된 요청")
                                                .content(new Content().addMediaType("application/json", new MediaType()
                                                        .schema(new ObjectSchema())
                                                ))
                                        )
                                )
                ));

        return new OpenAPI()
                .addServersItem(new Server().url("/"))
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(components)
                .paths(paths);
    }
}
