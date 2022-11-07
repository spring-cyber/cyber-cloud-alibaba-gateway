package com.cyber.infrastructure.config;

import com.cyber.domain.constant.HttpResultCode;
import com.cyber.domain.constant.ResultCode;
import com.cyber.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class Config {

    @Primary
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ErrorWebExceptionHandler errorWebExceptionHandler(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                                                             ServerCodecConfigurer serverCodecConfigurer) {
        GatewayErrorWebExceptionHandler errorWebExceptionHandler = new GatewayErrorWebExceptionHandler();
        errorWebExceptionHandler.setViewResolvers(viewResolversProvider.getIfAvailable(Collections::emptyList));
        errorWebExceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        errorWebExceptionHandler.setMessageReaders(serverCodecConfigurer.getReaders());
        return errorWebExceptionHandler;
    }

    public static class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

        private List<HttpMessageReader<?>> messageReaders = Collections.emptyList();
        private List<HttpMessageWriter<?>> messageWriters = Collections.emptyList();
        private List<ViewResolver> viewResolvers = Collections.emptyList();
        private ThreadLocal<Map<String, Object>> exceptionHandlerResult = new ThreadLocal<>();

        public void setMessageReaders(List<HttpMessageReader<?>> messageReaders) {
            Assert.notNull(messageReaders, "'messageReaders' must not be null");
            this.messageReaders = messageReaders;
        }

        public void setViewResolvers(List<ViewResolver> viewResolvers) {
            this.viewResolvers = viewResolvers;
        }

        public void setMessageWriters(List<HttpMessageWriter<?>> messageWriters) {
            Assert.notNull(messageWriters, "'messageWriters' must not be null");
            this.messageWriters = messageWriters;
        }

        @Override
        public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
            ServerHttpRequest request = exchange.getRequest();
            LOGGER.error("getway exception , request path {} error message {}...", request.getPath(), exception.getMessage());

            HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            String body = "Internal Server Error";
            int resultCode = HttpResultCode.SERVER_ERROR.getCode();
            if (exception instanceof NotFoundException) {
                httpStatus = HttpStatus.NOT_FOUND;
                resultCode = 404;
                body = "Service Not Found";
            } else if (exception instanceof ResponseStatusException) {
                ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                httpStatus = responseStatusException.getStatus();
                resultCode = 500;
                body = responseStatusException.getMessage();
            } else if (exception instanceof BusinessException) {
                httpStatus = HttpStatus.OK;
                resultCode = ((BusinessException) exception).getCode();
                body = ((BusinessException) exception).getMessage();
            }

            Map<String, Object> result = new HashMap<>(2, 1);
            result.put("httpStatus", httpStatus);

            String msg = "{\"code\":" + resultCode + ",\"message\": \"" + body + "\"}";
            result.put("body", msg);

            if (exchange.getResponse().isCommitted()) {
                return Mono.error(exception);
            }
            exceptionHandlerResult.set(result);
            ServerRequest newRequest = ServerRequest.create(exchange, this.messageReaders);
            return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse).route(newRequest)
                    .switchIfEmpty(Mono.error(exception))
                    .flatMap((handler) -> handler.handle(newRequest))
                    .flatMap((response) -> write(exchange, response));

        }

        protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
            Map<String, Object> result = exceptionHandlerResult.get();
            return ServerResponse.status((HttpStatus) result.get("httpStatus"))
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .body(BodyInserters.fromObject(result.get("body")));
        }

        private Mono<? extends Void> write(ServerWebExchange exchange, ServerResponse response) {
            exchange.getResponse().getHeaders().setContentType(response.headers().getContentType());
            return response.writeTo(exchange, new ResponseContext());
        }


        private class ResponseContext implements ServerResponse.Context {

            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return GatewayErrorWebExceptionHandler.this.messageWriters;
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return GatewayErrorWebExceptionHandler.this.viewResolvers;
            }
        }
    }
}
