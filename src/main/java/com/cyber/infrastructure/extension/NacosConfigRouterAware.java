package com.cyber.infrastructure.extension;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
public class NacosConfigRouterAware implements ApplicationEventPublisherAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(NacosConfigRouterAware.class);

    private static final String dataId = "cyber-cloud-gateway-router";
    private static final String group = "CLOUD_GATEWAY";
    private static final Executor dynamicRouteExecutor =  Executors.newFixedThreadPool(1);

    private static final List<RouteDefinition> ROUTE_CACHE = new ArrayList<>();

    @Value("${spring.cloud.nacos.config.server-addr}")
    private String serverAddr;


    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;

    private ApplicationEventPublisher applicationEventPublisher;


    @PostConstruct
    public void dynamicRouteByNacosListener() {
        try {
            ConfigService configService = NacosFactory.createConfigService(serverAddr);
            String config = configService.getConfigAndSignListener(dataId, group, 5000,new Listener() {
                @Override
                public void receiveConfigInfo(String config) {
                    reloadRoute(config);
                }

                @Override
                public Executor getExecutor() {
                    return dynamicRouteExecutor;
                }
            });
            reloadRoute(config);
        } catch (NacosException nacosException) {
            LOGGER.error("dynamic refresh route by nacos, config service error ...", nacosException);
        }
    }

    private void reloadRoute(String config) {
        try {
            LOGGER.info("start clear gateway router ,router size : {} ...", ROUTE_CACHE.size());
            for(RouteDefinition definition : ROUTE_CACHE) {
                this.routeDefinitionWriter.delete(Mono.just(definition.getId())).subscribe();
            }
            ROUTE_CACHE.clear();

            if(!StringUtils.isNoneEmpty(config)) {
                List<RouteDefinition> gatewayRouteDefinitions = JSONObject.parseArray(config, RouteDefinition.class);

                for (RouteDefinition routeDefinition : gatewayRouteDefinitions) {
                    routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
                    ROUTE_CACHE.add(routeDefinition);
                }

                LOGGER.info("get nacos gateway route config {}, router {}", gatewayRouteDefinitions.size(),ROUTE_CACHE.size());
            } else {
                LOGGER.info("get nacos gateway route config empty ...");
            }

            this.applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this.routeDefinitionWriter));
        } catch (Exception exception) {
            LOGGER.error("add route refresh route by nacos listener error ...", exception);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
