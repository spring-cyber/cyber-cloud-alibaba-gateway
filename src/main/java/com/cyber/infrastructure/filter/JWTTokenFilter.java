package com.cyber.infrastructure.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class JWTTokenFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(JWTTokenFilter.class);


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest serverHttpRequest = exchange.getRequest();
        ServerHttpResponse serverHttpResponse = exchange.getResponse();
        String requestPath = serverHttpRequest.getPath().value();
        String httpMethod = serverHttpRequest.getMethodValue();
        requestPath = Arrays.stream(org.springframework.util.StringUtils.tokenizeToStringArray(requestPath, "/"))
                .skip(1).collect(Collectors.joining("/"));
        // 获取产品编码
        String productCode = requestPath.substring(0, requestPath.indexOf("/"));
        // 去掉默认一个前缀
        requestPath = requestPath.substring(requestPath.indexOf("/"));

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
