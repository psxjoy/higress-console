/*
 * Copyright (c) 2022-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.alibaba.higress.sdk.service.kubernetes;

import com.alibaba.higress.sdk.constant.CommonKey;
import com.alibaba.higress.sdk.constant.KubernetesConstants;
import com.alibaba.higress.sdk.exception.ValidationException;
import com.alibaba.higress.sdk.model.Domain;
import com.alibaba.higress.sdk.model.Route;
import com.alibaba.higress.sdk.model.ServiceSource;
import com.alibaba.higress.sdk.model.TlsCertificate;
import com.alibaba.higress.sdk.model.WasmPlugin;
import com.alibaba.higress.sdk.model.WasmPluginInstance;
import com.alibaba.higress.sdk.model.WasmPluginInstanceScope;
import com.alibaba.higress.sdk.model.route.CorsConfig;
import com.alibaba.higress.sdk.model.route.ProxyNextUpstreamConfig;
import com.alibaba.higress.sdk.model.route.RewriteConfig;
import com.alibaba.higress.sdk.model.route.RoutePredicate;
import com.alibaba.higress.sdk.model.route.RoutePredicateTypeEnum;
import com.alibaba.higress.sdk.model.route.UpstreamService;
import com.alibaba.higress.sdk.service.kubernetes.crd.mcp.V1McpBridge;
import com.alibaba.higress.sdk.service.kubernetes.crd.mcp.V1McpBridgeSpec;
import com.alibaba.higress.sdk.service.kubernetes.crd.mcp.V1RegistryConfig;
import com.alibaba.higress.sdk.service.kubernetes.crd.wasm.MatchRule;
import com.alibaba.higress.sdk.service.kubernetes.crd.wasm.PluginPhase;
import com.alibaba.higress.sdk.service.kubernetes.crd.wasm.V1alpha1WasmPlugin;
import com.alibaba.higress.sdk.service.kubernetes.crd.wasm.V1alpha1WasmPluginSpec;
import com.alibaba.higress.sdk.util.TypeUtil;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1IngressSpec;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1TypedLocalObjectReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class KubernetesModelConverterTest {
    
    @InjectMocks
    private KubernetesModelConverter converter;
    
    @BeforeEach
    public void setUp() {
        KubernetesClientService service = mock(KubernetesClientService.class);
        converter = new KubernetesModelConverter(service);
        
    }
    
    @AfterEach
    public void tearDown() {
        converter = null;
    }
    
    
    @Test
    void domainName2ConfigMapName_NormalizeDomainName() {
        V1ConfigMap domainConfigMap = new V1ConfigMap();
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(converter.domainName2ConfigMapName("domain-name"));
        metadata.setResourceVersion("0.0.1");
        Map<String, String> configMap = new HashMap<>();
        configMap.put(CommonKey.DOMAIN, "domain-name");
        configMap.put(KubernetesConstants.K8S_CERT, "domain-cert");
        configMap.put(KubernetesConstants.K8S_ENABLE_HTTPS, "domain-https");
        domainConfigMap.metadata(metadata);
        domainConfigMap.data(configMap);
        Domain domain = new Domain();
        domain.setName("domain-name");
        domain.setVersion("0.0.1");
        domain.setCertIdentifier("domain-cert");
        domain.setEnableHttps("domain-https");
        V1ConfigMap target = converter.domain2ConfigMap(domain);
        Assertions.assertEquals(domainConfigMap, target);
    }
    
    @Test
    void initV1McpBridge_ShouldSetDefaultValues() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        
        converter.initV1McpBridge(v1McpBridge);
        
        Assertions.assertNotNull(v1McpBridge.getMetadata(), "Metadata should not be null");
        assertEquals(V1McpBridge.DEFAULT_NAME, v1McpBridge.getMetadata().getName(),
                "Metadata name should be set to default");
        Assertions.assertNotNull(v1McpBridge.getSpec(), "Spec should not be null");
        Assertions.assertNotNull(v1McpBridge.getSpec().getRegistries(), "Spec registries should not be null");
        assertEquals(0, v1McpBridge.getSpec().getRegistries().size(),
                "Spec registries should be initialized as empty list");
    }
    
    @Test
    public void generateAuthSecretName_ValidServiceSourceName_CorrectlyGenerated() {
        // Arrange
        String serviceSourceName = "test-service-source";
        String expectedPattern = serviceSourceName + "-auth-\\w{5}";
        
        // Act
        String authSecretName = converter.generateAuthSecretName(serviceSourceName);
        
        // Assert
        Assertions.assertTrue(authSecretName.matches(expectedPattern),
                "Auth secret name should match the expected pattern");
    }
    
    @Test
    public void generateAuthSecretName_EmptyServiceSourceName_CorrectlyGenerated() {
        // Arrange
        String serviceSourceName = "";
        String expectedPattern = serviceSourceName + "-auth-\\w{5}";
        
        // Act
        String authSecretName = converter.generateAuthSecretName(serviceSourceName);
        
        // Assert
        Assertions.assertTrue(authSecretName.matches(expectedPattern),
                "Auth secret name should match the expected pattern");
    }
    
    @Test
    public void generateAuthSecretName_NullServiceSourceName_CorrectlyGenerated() {
        // Arrange
        String serviceSourceName = null;
        String expectedPattern = "null-auth-\\w{5}";
        
        // Act
        String authSecretName = converter.generateAuthSecretName(serviceSourceName);
        
        // Assert
        Assertions.assertTrue(authSecretName.matches(expectedPattern),
                "Auth secret name should match the expected pattern");
    }
    
    
    @Test
    void route2Ingress_MultipleDomains_ThrowsException() {
        // Arrange
        Route route = new Route();
        route.setDomains(Collections.singletonList("example.com"));
        route.setPath(RoutePredicate.builder().matchType("exact").matchValue("/test").build());
        route.setServices(Collections.singletonList(new UpstreamService()));
        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> converter.route2Ingress(route));
    }
    
    @Test
    void route2Ingress_InvalidPathMatchType_ThrowsException() {
        // Arrange
        Route route = new Route();
        route.setDomains(Collections.singletonList("example.com"));
        route.setPath(RoutePredicate.builder().matchType("invalid").matchValue("/test").build());
        route.setServices(Collections.singletonList(new UpstreamService()));
        
        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> converter.route2Ingress(route));
    }
    
    
    @Test
    void route2Ingress_CustomAnnotations_ValidInput_Success() {
        // Arrange
        Route route = new Route();
        route.setName("test-route");
        route.setDomains(Collections.singletonList("example.com"));
        route.setCustomConfigs(Collections.singletonMap("custom.annotation.com", "value"));
        
        // Act
        V1Ingress ingress = converter.route2Ingress(route);
        
        // Assert
        Assertions.assertNotNull(ingress);
        assertEquals("test-route", ingress.getMetadata().getName());
        assertEquals("value", ingress.getMetadata().getAnnotations().get("custom.annotation.com"));
    }
    
    @Test
    void route2Ingress_CustomAnnotations_ThrowsValidationException() {
        // Arrange
        Route route = new Route();
        route.setDomains(Collections.singletonList("example.com"));
        route.setCustomConfigs(Collections.singletonMap("higress.io/enable-proxy-next-upstream", "value"));
        
        // Act & Assert
        Assertions.assertThrows(ValidationException.class, () -> converter.route2Ingress(route));
    }
    
    @Test
    void route2Ingress_CorsConfig_Success() {
        // Arrange
        Route route = new Route();
        route.setName("test-route");
        route.setDomains(Collections.singletonList("example.com"));
        route.setCors(new CorsConfig(true,                           // enabled (Boolean)
                Collections.singletonList("http://example.com"), // allowOrigins (List<String>)
                Collections.singletonList("GET"),                // allowMethods (List<String>)
                Collections.singletonList("Content-Type"),       // allowHeaders (List<String>)
                Collections.singletonList("Content-Length"), // exposeHeaders (List<String>)
                3600,                           // maxAge (Integer)
                true                           // allowCredentials (Boolean)
        ));
        
        // Act
        V1Ingress ingress = converter.route2Ingress(route);
        
        // Assert
        Assertions.assertNotNull(ingress);
        assertEquals("test-route", ingress.getMetadata().getName());
        Assertions.assertTrue(Boolean.parseBoolean(
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_ENABLED_KEY)));
        assertEquals("3600",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_MAX_AGE_KEY));
        Assertions.assertTrue(Boolean.parseBoolean(
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_ALLOW_CREDENTIALS_KEY)));
        assertEquals("http://example.com",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_ALLOW_ORIGIN_KEY));
        assertEquals("Content-Type",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_ALLOW_HEADERS_KEY));
        assertEquals("GET",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_ALLOW_METHODS_KEY));
        assertEquals("Content-Length",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.CORS_EXPOSE_HEADERS_KEY));
    }
    
    
    @Test
    void route2Ingress_Methods_Success() {
        // Arrange
        Route route = new Route();
        route.setName("test-route");
        route.setDomains(Collections.singletonList("example.com"));
        route.setMethods(Collections.singletonList("GET"));
        
        // Act
        V1Ingress ingress = converter.route2Ingress(route);
        
        // Assert
        Assertions.assertNotNull(ingress);
        assertEquals("test-route", ingress.getMetadata().getName());
        assertEquals("GET", ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.METHOD_KEY));
    }
    
    @Test
    void route2Ingress_RewriteConfig_Success() {
        // Arrange
        Route route = new Route();
        route.setName("test-route");
        route.setDomains(Collections.singletonList("example.com"));
        route.setRewrite(new RewriteConfig(true, "/new-path", "new-host"));
        
        // Act
        V1Ingress ingress = converter.route2Ingress(route);
        
        // Assert
        Assertions.assertNotNull(ingress);
        assertEquals("test-route", ingress.getMetadata().getName());
        Assertions.assertTrue(Boolean.parseBoolean(
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.REWRITE_ENABLED_KEY)));
        assertEquals("/new-path",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.REWRITE_PATH_KEY));
        assertEquals("new-host",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.UPSTREAM_VHOST_KEY));
    }
    
    @Test
    void route2Ingress_ProxyNextUpstreamConfig_Success() {
        // Arrange
        Route route = new Route();
        route.setName("test-route");
        route.setDomains(Collections.singletonList("example.com"));
        route.setProxyNextUpstream(new ProxyNextUpstreamConfig(true, 2, 10, new String[] {"$http_4xx"}));
        
        // Act
        V1Ingress ingress = converter.route2Ingress(route);
        
        // Assert
        Assertions.assertNotNull(ingress);
        assertEquals("test-route", ingress.getMetadata().getName());
        Assertions.assertTrue(Boolean.parseBoolean(ingress.getMetadata().getAnnotations()
                .get(KubernetesConstants.Annotation.PROXY_NEXT_UPSTREAM_ENABLED_KEY)));
        assertEquals("2", ingress.getMetadata().getAnnotations()
                .get(KubernetesConstants.Annotation.PROXY_NEXT_UPSTREAM_TRIES_KEY));
        assertEquals("10", ingress.getMetadata().getAnnotations()
                .get(KubernetesConstants.Annotation.PROXY_NEXT_UPSTREAM_TIMEOUT_KEY));
        assertEquals("$http_4xx",
                ingress.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.PROXY_NEXT_UPSTREAM_KEY));
    }
    
    
    @Test
    public void isIngressSupportedTestMissingMetadata() {
        V1Ingress ingress = buildBasicSupportedIngress();
        ingress.setMetadata(null);
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMissingSpec() {
        V1Ingress ingress = buildBasicSupportedIngress();
        ingress.setSpec(null);
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMissingRules() {
        V1Ingress ingress = buildBasicSupportedIngress();
        ingress.getSpec().setRules(null);
        Assertions.assertFalse(converter.isIngressSupported(ingress));
        
        ingress.getSpec().setRules(Collections.emptyList());
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMultipleRules() {
        V1Ingress ingress = buildBasicSupportedIngress();
        List<V1IngressRule> rules = ingress.getSpec().getRules();
        rules.add(rules.get(0));
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMissingHttpRule() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1IngressRule rule = ingress.getSpec().getRules().get(0);
        rule.setHttp(null);
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMissingPath() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1HTTPIngressRuleValue httpRule = ingress.getSpec().getRules().get(0).getHttp();
        httpRule.setPaths(null);
        Assertions.assertFalse(converter.isIngressSupported(ingress));
        
        httpRule.setPaths(Collections.emptyList());
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMultiplePaths() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1HTTPIngressRuleValue httpRule = ingress.getSpec().getRules().get(0).getHttp();
        List<V1HTTPIngressPath> paths = httpRule.getPaths();
        paths.add(paths.get(0));
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestUnsupportedPathType() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.IMPLEMENTATION_SPECIFIC);
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestMissingBackend() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setBackend(null);
        Assertions.assertTrue(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestServiceBackend() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1IngressBackend backend = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend();
        backend.setService(new V1IngressServiceBackend());
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestNonMcpBackend() {
        V1Ingress ingress = buildBasicSupportedIngress();
        V1IngressBackend backend = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend();
        V1TypedLocalObjectReference reference = backend.getResource();
        reference.setName("DummyKind");
        Assertions.assertFalse(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void isIngressSupportedTestAllGood() {
        V1Ingress ingress = buildBasicSupportedIngress();
        Assertions.assertTrue(converter.isIngressSupported(ingress));
    }
    
    @Test
    public void ingress2RouteTestPrefixPathSingleService() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local");
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", null, null, 100);
        expectedRoute.setServices(Collections.singletonList(service));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void ingress2RouteTestPrefixPathSingleServiceWithWeight() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "10% hello.default.svc.cluster.local");
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", null, null, 10);
        expectedRoute.setServices(Collections.singletonList(service));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void ingress2RouteTestPrefixPathSingleServiceWithPort() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local:8080");
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", 8080, null, 100);
        expectedRoute.setServices(Collections.singletonList(service));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void ingress2RouteTestPrefixPathSingleServiceWithPortAndVersion() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local:8080 v1");
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", 8080, "v1", 100);
        expectedRoute.setServices(Collections.singletonList(service));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void ingress2RouteTestPrefixPathMultipleServices() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "20% hello1.default.svc.cluster.local:8080\n"
                        + "30% hello2.default.svc.cluster.local:18080 v1\n50% hello3.default.svc.cluster.local v2");
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service1 = new UpstreamService("hello1.default.svc.cluster.local", 8080, null, 20);
        UpstreamService service2 = new UpstreamService("hello2.default.svc.cluster.local", 18080, "v1", 30);
        UpstreamService service3 = new UpstreamService("hello3.default.svc.cluster.local", null, "v2", 50);
        expectedRoute.setServices(Arrays.asList(service1, service2, service3));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void ingress2RouteTestExactPathSingleService() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local");
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.EXACT);
        path.setPath("/foo");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.EQUAL.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", null, null, 100);
        expectedRoute.setServices(Collections.singletonList(service));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void ingress2RouteTestRegularPathSingleService() {
        V1Ingress ingress = buildBasicSupportedIngress();
        
        V1ObjectMeta metadata = ingress.getMetadata();
        metadata.setName("test");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local");
        KubernetesUtil.setAnnotation(metadata, KubernetesConstants.Annotation.USE_REGEX_KEY,
                KubernetesConstants.Annotation.TRUE_VALUE);
        
        V1HTTPIngressPath path = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/route_\\d+");
        
        Route route = converter.ingress2Route(ingress);
        
        Route expectedRoute = buildBasicRoute();
        expectedRoute.setName(metadata.getName());
        RoutePredicate pathPredicate = expectedRoute.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.REGULAR.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue(path.getPath());
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", null, null, 100);
        expectedRoute.setServices(Collections.singletonList(service));
        assertEquals(expectedRoute, route);
    }
    
    @Test
    public void route2IngressTestPrefixPathSingleService() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue("/");
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", null, null, null);
        route.setServices(Collections.singletonList(service));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local");
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    @Test
    public void route2IngressTestPrefixPathSingleServiceWithWeight() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue("/");
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", null, null, 15);
        route.setServices(Collections.singletonList(service));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local");
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    @Test
    public void route2IngressTestPrefixPathSingleServiceWithPort() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue("/");
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", 8080, null, null);
        route.setServices(Collections.singletonList(service));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local:8080");
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    @Test
    public void route2IngressTestPrefixPathSingleServiceWithPortAndVersion() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue("/");
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", 8080, "v1", 100);
        route.setServices(Collections.singletonList(service));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local:8080");
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    @Test
    public void route2IngressTestPrefixPathMultipleServices() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.PRE.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue("/");
        UpstreamService service1 = new UpstreamService("hello1.default.svc.cluster.local", 8080, null, 20);
        UpstreamService service2 = new UpstreamService("hello2.default.svc.cluster.local", 18080, "v1", 30);
        UpstreamService service3 = new UpstreamService("hello3.default.svc.cluster.local", null, "v2", 50);
        route.setServices(Arrays.asList(service1, service2, service3));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "20% hello1.default.svc.cluster.local:8080\n"
                        + "30% hello2.default.svc.cluster.local:18080 v1\n50% hello3.default.svc.cluster.local v2");
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    @Test
    public void route2IngressTestExactPathSingleService() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.EQUAL.toString());
        pathPredicate.setCaseSensitive(null);
        pathPredicate.setMatchValue("/");
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", 8080, "v1", 100);
        route.setServices(Collections.singletonList(service));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local:8080");
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.EXACT);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    @Test
    public void route2IngressTestRegularPathSingleService() {
        Route route = buildBasicRoute();
        route.setName("test");
        RoutePredicate pathPredicate = route.getPath();
        pathPredicate.setMatchType(RoutePredicateTypeEnum.REGULAR.toString());
        pathPredicate.setMatchValue("/route_\\d+");
        pathPredicate.setCaseSensitive(null);
        UpstreamService service = new UpstreamService("hello.default.svc.cluster.local", 8080, "v1", 100);
        route.setServices(Collections.singletonList(service));
        
        V1Ingress ingress = converter.route2Ingress(route);
        
        V1Ingress expectedIngress = buildBasicSupportedIngress();
        
        V1ObjectMeta expectedMetadata = expectedIngress.getMetadata();
        expectedMetadata.setName(route.getName());
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.DESTINATION_KEY,
                "hello.default.svc.cluster.local:8080");
        KubernetesUtil.setAnnotation(expectedMetadata, KubernetesConstants.Annotation.USE_REGEX_KEY,
                KubernetesConstants.Annotation.TRUE_VALUE);
        
        V1HTTPIngressPath expectedPath = expectedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        expectedPath.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        expectedPath.setPath(pathPredicate.getMatchValue());
        
        assertEquals(expectedIngress, ingress);
    }
    
    
    @Test
    void ingress2RouteTestValidIngressWithSingleRule() {
        // Arrange
        V1IngressBackend backend = new V1IngressBackend();
        V1IngressSpec spec = new V1IngressSpec();
        spec.setDefaultBackend(backend);
        
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName("test-ingress");
        metadata.setResourceVersion("1");
        
        V1Ingress ingress = new V1Ingress();
        ingress.setMetadata(metadata);
        ingress.setSpec(spec);
        
        // Act
        Route route = converter.ingress2Route(ingress);
        
        // Assert
        Assertions.assertNotNull(route);
        assertEquals("test-ingress", route.getName());
        assertEquals("1", route.getVersion());
    }
    
    @Test
    void ingress2RouteTestValidIngressWithMultipleRules() {
        // Arrange
        V1IngressBackend backend = new V1IngressBackend();
        V1IngressSpec spec = new V1IngressSpec();
        spec.setDefaultBackend(backend);
        
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName("test-ingress");
        metadata.setResourceVersion("1");
        
        V1IngressRule rule1 = new V1IngressRule();
        rule1.setHost("example.com");
        
        V1IngressRule rule2 = new V1IngressRule();
        rule2.setHost("test.example.com");
        
        spec.setRules(Arrays.asList(rule1, rule2));
        
        V1Ingress ingress = new V1Ingress();
        ingress.setMetadata(metadata);
        ingress.setSpec(spec);
        
        // Act
        Route route = converter.ingress2Route(ingress);
        
        // Assert
        Assertions.assertNotNull(route);
        assertEquals("test-ingress", route.getName());
        assertEquals("1", route.getVersion());
        assertEquals(null, route.getDomains());
    }
    
    @Test
    void ingress2RouteTestValidIngressWithTLS() {
        // Arrange
        V1IngressBackend backend = new V1IngressBackend();
        V1IngressSpec spec = new V1IngressSpec();
        spec.setDefaultBackend(backend);
        
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName("test-ingress");
        metadata.setResourceVersion("1");
        
        V1IngressTLS tls = new V1IngressTLS();
        tls.setHosts(Arrays.asList("example.com", "test.example.com"));
        
        spec.setTls(Arrays.asList(tls));
        
        V1Ingress ingress = new V1Ingress();
        ingress.setMetadata(metadata);
        ingress.setSpec(spec);
        
        // Act
        Route route = converter.ingress2Route(ingress);
        
        // Assert
        Assertions.assertNotNull(route);
        assertEquals("test-ingress", route.getName());
        assertEquals("1", route.getVersion());
        assertEquals(null, route.getDomains());
    }
    
    @Test
    void ingress2Route_ValidIngressWithAnnotations_ReturnsValidRoute() {
        // Arrange
        V1IngressBackend backend = new V1IngressBackend();
        V1IngressSpec spec = new V1IngressSpec();
        spec.setDefaultBackend(backend);
        
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName("test-ingress");
        metadata.setResourceVersion("1");
        metadata.setAnnotations(new HashMap<String, String>() {{
            put("example.com", "annotation-value");
        }});
        
        V1Ingress ingress = new V1Ingress();
        ingress.setMetadata(metadata);
        ingress.setSpec(spec);
        
        // Act
        Route route = converter.ingress2Route(ingress);
        
        // Assert
        Assertions.assertNotNull(route);
        assertEquals("test-ingress", route.getName());
        assertEquals("1", route.getVersion());
        Assertions.assertNotNull(route.getCustomConfigs());
        assertEquals("annotation-value", route.getCustomConfigs().get("example.com"));
    }
    
    @Test
    void ingress2Route_NullMetadata_ReturnsRouteWithDefaults() {
        // Arrange
        V1IngressBackend backend = new V1IngressBackend();
        V1IngressSpec spec = new V1IngressSpec();
        spec.setDefaultBackend(backend);
        
        V1Ingress ingress = new V1Ingress();
        ingress.setSpec(spec);
        
        // Act
        Route route = converter.ingress2Route(ingress);
        
        // Assert
        Assertions.assertNotNull(route);
        assertEquals(null, route.getName());
        assertEquals(null, route.getVersion());
    }
    
    @Test
    void ingress2Route_NullSpec_ReturnsRouteWithDefaults() {
        // Arrange
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName("test-ingress");
        metadata.setResourceVersion("1");
        
        V1Ingress ingress = new V1Ingress();
        ingress.setMetadata(metadata);
        
        // Act
        Route route = converter.ingress2Route(ingress);
        
        // Assert
        Assertions.assertNotNull(route);
        assertEquals("test-ingress", route.getName());
        assertEquals("1", route.getVersion());
        Assertions.assertNull(route.getDomains());
    }
    
    
    @Test
    void configMap2Domain_ValidConfigMap_ShouldReturnDomain() {
        // Arrange
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setResourceVersion("1");
        
        Map<String, String> data = new HashMap<>();
        data.put(CommonKey.DOMAIN, "example.com");
        data.put(KubernetesConstants.K8S_CERT, "cert-identifier");
        data.put(KubernetesConstants.K8S_ENABLE_HTTPS, "true");
        
        V1ConfigMap configMap = new V1ConfigMap();
        configMap.setMetadata(metadata);
        configMap.setData(data);
        
        // Act
        Domain domain = converter.configMap2Domain(configMap);
        
        // Assert
        Assertions.assertNotNull(domain);
        assertEquals("example.com", domain.getName());
        assertEquals("1", domain.getVersion());
        assertEquals("cert-identifier", domain.getCertIdentifier());
        assertEquals("true", domain.getEnableHttps());
    }
    
    
    @Test
    void configMap2Domain_NullMetadata_ShouldReturnDomainWithNullVersion() {
        // Arrange
        Map<String, String> data = new HashMap<>();
        data.put(CommonKey.DOMAIN, "example.com");
        
        V1ConfigMap configMap = new V1ConfigMap();
        configMap.setData(data);
        
        // Act
        Domain domain = converter.configMap2Domain(configMap);
        
        // Assert
        Assertions.assertNotNull(domain);
        assertEquals("example.com", domain.getName());
        Assertions.assertNull(domain.getVersion());
        Assertions.assertNull(domain.getCertIdentifier());
        Assertions.assertNull(domain.getEnableHttps());
    }
    
    @Test
    void configMap2Domain_NullData_ShouldThrowIllegalArgumentException() {
        // Arrange
        V1ConfigMap configMap = new V1ConfigMap();
        configMap.setData(null);
        
        // Act & Assert
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            converter.configMap2Domain(configMap);
        });
        assertEquals("The ConfigMap data is illegal", exception.getMessage());
    }
    
    @Test
    void configMap2Domain_MissingFields_ShouldReturnDomainWithNullFields() {
        // Arrange
        Map<String, String> data = new HashMap<>();
        data.put(CommonKey.DOMAIN, "example.com");
        
        V1ConfigMap configMap = new V1ConfigMap();
        configMap.setData(data);
        
        // Act
        Domain domain = converter.configMap2Domain(configMap);
        
        // Assert
        Assertions.assertNotNull(domain);
        assertEquals("example.com", domain.getName());
        Assertions.assertNull(domain.getCertIdentifier());
        Assertions.assertNull(domain.getEnableHttps());
    }
    
    @Test
    public void testDomainName2ConfigMapName_NormalDomainName_ConfigMapNameCreated() {
        String domainName = "www.example.com";
        String expectedConfigMapName = "domain-www.example.com";
        String actualConfigMapName = converter.domainName2ConfigMapName(domainName);
        
        assertEquals(expectedConfigMapName, actualConfigMapName, "ConfigMap name should be 'domain-www.example.com'");
    }
    
    @Test
    public void testDomainName2ConfigMapName_WildcardDomainName_ConfigMapNameCreated() {
        String domainName = "*.example.com";
        String expectedConfigMapName = "domain-wildcard.example.com";
        String actualConfigMapName = converter.domainName2ConfigMapName(domainName);
        
        assertEquals(expectedConfigMapName, actualConfigMapName,
                "ConfigMap name should be 'domain-wildcard.example.com'");
    }
    
    
    @Test
    void tlsCertificate2Secret_ValidCertificateWithoutDomains_ShouldNotSetLabels() {
        TlsCertificate certificate = TlsCertificate.builder().cert("dummyCert").key("dummyKey").name("test-certificate")
                .version("1").domains(Collections.emptyList()).build();
        
        V1Secret secret = converter.tlsCertificate2Secret(certificate);
        
        Assertions.assertNotNull(secret);
        assertEquals(KubernetesConstants.SECRET_TYPE_TLS, secret.getType());
        assertEquals("test-certificate", secret.getMetadata().getName());
        assertEquals("1", secret.getMetadata().getResourceVersion());
        
        Map<String, byte[]> data = secret.getData();
        Assertions.assertNotNull(data);
        assertEquals(2, data.size());
        Assertions.assertArrayEquals(TypeUtil.string2Bytes("dummyCert"),
                data.get(KubernetesConstants.SECRET_TLS_CRT_FIELD));
        Assertions.assertArrayEquals(TypeUtil.string2Bytes("dummyKey"),
                data.get(KubernetesConstants.SECRET_TLS_KEY_FIELD));
        
        Map<String, String> labels = secret.getMetadata().getLabels();
        Assertions.assertNull(labels);
        //        Assertions.assertTrue(labels.isEmpty());
    }
    
    
    @Test
    void secret2TlsCertificate_ValidSecretWithoutCertAndKey_ShouldReturnTlsCertificateWithoutCertAndKey() {
        // Arrange
        V1Secret secret = new V1Secret();
        secret.setMetadata(new V1ObjectMeta() {{
            setName("test-secret");
            setResourceVersion("123");
        }});
        
        // Act
        TlsCertificate tlsCertificate = converter.secret2TlsCertificate(secret);
        
        // Assert
        Assertions.assertNotNull(tlsCertificate);
        assertEquals("test-secret", tlsCertificate.getName());
        assertEquals("123", tlsCertificate.getVersion());
        Assertions.assertNull(tlsCertificate.getCert());
        Assertions.assertNull(tlsCertificate.getKey());
        Assertions.assertNull(tlsCertificate.getValidityStart());
        Assertions.assertNull(tlsCertificate.getValidityEnd());
        Assertions.assertNull(tlsCertificate.getDomains());
    }
    
    @Test
    void secret2TlsCertificate_NullMetadata_ShouldReturnTlsCertificateWithoutNameAndVersion() {
        // Arrange
        V1Secret secret = new V1Secret();
        secret.setData(new HashMap<String, byte[]>() {{
            put(KubernetesConstants.SECRET_TLS_CRT_FIELD, "certData".getBytes());
            put(KubernetesConstants.SECRET_TLS_KEY_FIELD, "keyData".getBytes());
        }});
        
        // Act
        TlsCertificate tlsCertificate = converter.secret2TlsCertificate(secret);
        
        // Assert
        Assertions.assertNotNull(tlsCertificate);
        Assertions.assertNull(tlsCertificate.getName());
        Assertions.assertNull(tlsCertificate.getVersion());
        assertEquals("certData", tlsCertificate.getCert());
        assertEquals("keyData", tlsCertificate.getKey());
    }
    
    @Test
    void secret2TlsCertificate_EmptyData_ShouldReturnTlsCertificateWithoutCertAndKey() {
        // Arrange
        V1Secret secret = new V1Secret();
        secret.setMetadata(new V1ObjectMeta() {{
            setName("test-secret");
            setResourceVersion("123");
        }});
        secret.setData(Collections.emptyMap());
        
        // Act
        TlsCertificate tlsCertificate = converter.secret2TlsCertificate(secret);
        
        // Assert
        Assertions.assertNotNull(tlsCertificate);
        assertEquals("test-secret", tlsCertificate.getName());
        assertEquals("123", tlsCertificate.getVersion());
        Assertions.assertNull(tlsCertificate.getCert());
        Assertions.assertNull(tlsCertificate.getKey());
        Assertions.assertNull(tlsCertificate.getValidityStart());
        Assertions.assertNull(tlsCertificate.getValidityEnd());
        Assertions.assertNull(tlsCertificate.getDomains());
    }
    
    @Test
    void wasmPluginFromCr_WithValidInput_ShouldReturnWasmPlugin() {
        // Arrange
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        cr.setMetadata(createMetadata());
        cr.setSpec(createSpec());
        
        // Act
        WasmPlugin plugin = converter.wasmPluginFromCr(cr);
        
        // Assert
        Assertions.assertNotNull(plugin);
        assertEquals("test-plugin", plugin.getName());
        assertEquals("v1", plugin.getPluginVersion());
        assertEquals("test-category", plugin.getCategory());
        assertEquals(Boolean.TRUE, plugin.getBuiltIn());
        assertEquals("Test Plugin", plugin.getTitle());
        assertEquals("A test plugin", plugin.getDescription());
        assertEquals("icon.png", plugin.getIcon());
        assertEquals("test/image", plugin.getImageRepository());
        assertEquals("v1", plugin.getImageVersion());
        assertEquals(PluginPhase.UNSPECIFIED.getName(), plugin.getPhase());
        assertEquals(10, plugin.getPriority());
    }
    
    @Test
    void wasmPluginFromCr_WithMissingFields_ShouldHandleGracefully() {
        // Arrange
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        cr.setMetadata(createMetadata());
        cr.setSpec(createSpec());
        
        // Remove some fields
        cr.getMetadata().getLabels().remove(KubernetesConstants.Label.WASM_PLUGIN_NAME_KEY);
        cr.getMetadata().getLabels().remove(KubernetesConstants.Label.WASM_PLUGIN_VERSION_KEY);
        cr.getSpec().setUrl("image");
        
        // Act
        WasmPlugin plugin = converter.wasmPluginFromCr(cr);
        
        // Assert
        Assertions.assertNotNull(plugin);
        assertEquals(null, plugin.getName());
        assertEquals(null, plugin.getPluginVersion());
        assertEquals("test-category", plugin.getCategory());
        assertEquals(Boolean.TRUE, plugin.getBuiltIn());
        assertEquals("Test Plugin", plugin.getTitle());
        assertEquals("A test plugin", plugin.getDescription());
        assertEquals("icon.png", plugin.getIcon());
        assertEquals("image", plugin.getImageRepository());
        assertEquals(null, plugin.getImageVersion());
        assertEquals(PluginPhase.UNSPECIFIED.getName(), plugin.getPhase());
        assertEquals(10, plugin.getPriority());
    }
    
    @Test
    void wasmPluginToCr_ValidInput_ShouldConvertCorrectly() {
        WasmPlugin plugin = WasmPlugin.builder().name("test-plugin").pluginVersion("1.0.0").version("1")
                .category("test-category").title("Test Plugin").description("A test plugin").icon("test-icon")
                .builtIn(true).imageRepository("test-repository").imageVersion("test-version").phase("test-phase")
                .priority(10).build();
        
        V1alpha1WasmPlugin cr = converter.wasmPluginToCr(plugin);
        
        Assertions.assertNotNull(cr);
        Assertions.assertNotNull(cr.getMetadata());
        assertEquals("test-plugin-1.0.0", cr.getMetadata().getName());
        assertEquals("1", cr.getMetadata().getResourceVersion());
        
        assertEquals("test-plugin", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_NAME_KEY));
        assertEquals("1.0.0", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_VERSION_KEY));
        assertEquals("test-category",
                cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_CATEGORY_KEY));
        assertEquals("true", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_BUILT_IN_KEY));
        
        assertEquals("Test Plugin",
                cr.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.WASM_PLUGIN_TITLE_KEY));
        assertEquals("A test plugin",
                cr.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.WASM_PLUGIN_DESCRIPTION_KEY));
        assertEquals("test-icon",
                cr.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.WASM_PLUGIN_ICON_KEY));
        
        Assertions.assertNotNull(cr.getSpec());
        assertEquals("test-phase", cr.getSpec().getPhase());
        assertEquals(10, cr.getSpec().getPriority().intValue());
        assertEquals("oci://test-repository:test-version", cr.getSpec().getUrl());
    }
    
    @Test
    void wasmPluginToCr_NullImageRepository_ShouldHandleCorrectly() {
        WasmPlugin plugin = WasmPlugin.builder().name("test-plugin").pluginVersion("1.0.0").version("1")
                .imageRepository(null).imageVersion("test-version").build();
        
        V1alpha1WasmPlugin cr = converter.wasmPluginToCr(plugin);
        
        Assertions.assertNotNull(cr);
        Assertions.assertNotNull(cr.getSpec());
        assertEquals(null, cr.getSpec().getUrl());
    }
    
    @Test
    void wasmPluginToCr_EmptyImageVersion_ShouldHandleCorrectly() {
        WasmPlugin plugin = WasmPlugin.builder().name("test-plugin").pluginVersion("1.0.0").version("1")
                .imageRepository("test-repository").imageVersion("").build();
        
        V1alpha1WasmPlugin cr = converter.wasmPluginToCr(plugin);
        
        Assertions.assertNotNull(cr);
        Assertions.assertNotNull(cr.getSpec());
        assertEquals("oci://test-repository", cr.getSpec().getUrl());
    }
    
    
    @Test
    void mergeWasmPluginSpec_SourceSpecNull_DestinationSpecUnchanged() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        srcPlugin.setSpec(null);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec dstSpec = new V1alpha1WasmPluginSpec();
        dstSpec.setDefaultConfig(new HashMap<>());
        dstPlugin.setSpec(dstSpec);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
    }
    
    @Test
    void mergeWasmPluginSpec_DestinationSpecNull_SetNewSpec() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec srcSpec = new V1alpha1WasmPluginSpec();
        srcSpec.setDefaultConfig(new HashMap<>());
        srcPlugin.setSpec(srcSpec);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        dstPlugin.setSpec(null);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
    }
    
    @Test
    void mergeWasmPluginSpec_MatchRulesMergedAndSorted() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec srcSpec = new V1alpha1WasmPluginSpec();
        srcSpec.setMatchRules(new ArrayList<>());
        srcSpec.getMatchRules().add(MatchRule.forDomain("example.com"));
        srcSpec.getMatchRules().add(MatchRule.forDomain("test.com"));
        srcPlugin.setSpec(srcSpec);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec dstSpec = new V1alpha1WasmPluginSpec();
        dstSpec.setMatchRules(new ArrayList<>());
        dstSpec.getMatchRules().add(MatchRule.forDomain("example.com"));
        dstSpec.getMatchRules().add(MatchRule.forDomain("test.com"));
        dstPlugin.setSpec(dstSpec);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        List<MatchRule> expected = new ArrayList<>();
        expected.add(MatchRule.forDomain("example.com"));
        expected.add(MatchRule.forDomain("test.com"));
        
        assertEquals(expected, dstPlugin.getSpec().getMatchRules());
    }
    
    @Test
    void mergeWasmPluginSpec_EmptyMatchRules_NoChange() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec srcSpec = new V1alpha1WasmPluginSpec();
        srcSpec.setMatchRules(new ArrayList<>());
        srcPlugin.setSpec(srcSpec);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec dstSpec = new V1alpha1WasmPluginSpec();
        dstSpec.setMatchRules(new ArrayList<>());
        dstSpec.getMatchRules().add(MatchRule.forDomain("example.com"));
        dstPlugin.setSpec(dstSpec);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        List<MatchRule> expected = new ArrayList<>();
        expected.add(MatchRule.forDomain("example.com"));
        
        assertEquals(expected, dstPlugin.getSpec().getMatchRules());
    }
    
    @Test
    void mergeWasmPluginSpec_SrcSpecNull_NoChangeToDstPlugin() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        srcPlugin.setSpec(null);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec dstSpec = new V1alpha1WasmPluginSpec();
        dstSpec.setDefaultConfig(new HashMap<>());
        dstPlugin.setSpec(dstSpec);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
    }
    
    @Test
    void mergeWasmPluginSpec_DstSpecNull_SetNewSpec() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec srcSpec = new V1alpha1WasmPluginSpec();
        srcSpec.setDefaultConfig(new HashMap<>());
        srcPlugin.setSpec(srcSpec);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        dstPlugin.setSpec(null);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
    }
    
    @Test
    void mergeWasmPluginSpec_MatchRulesMergedAndSorted_WithExistingRules() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec srcSpec = new V1alpha1WasmPluginSpec();
        srcSpec.setMatchRules(new ArrayList<>());
        srcSpec.getMatchRules().add(MatchRule.forDomain("example.com"));
        srcSpec.getMatchRules().add(MatchRule.forDomain("test.com"));
        srcPlugin.setSpec(srcSpec);
        
        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec dstSpec = new V1alpha1WasmPluginSpec();
        dstSpec.setMatchRules(new ArrayList<>());
        dstSpec.getMatchRules().add(MatchRule.forDomain("example.com"));
        dstPlugin.setSpec(dstSpec);
        
        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);
        
        List<MatchRule> expected = new ArrayList<>();
        expected.add(MatchRule.forDomain("example.com"));
        expected.add(MatchRule.forDomain("test.com"));
        
        assertEquals(expected, dstPlugin.getSpec().getMatchRules());
    }
    
    
    @Test
    void getWasmPluginInstanceFromCr_GlobalScope_Configured() {
        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
        plugin.setMetadata(createMetadata("global", "test-plugin", "v1"));
        plugin.setSpec(createSpecWithGlobalConfig());
        
        WasmPluginInstance instance = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.GLOBAL,
                null);
        Assertions.assertNotNull(instance);
        //        Assertions. assertEquals("global", instance.getPluginName());
        assertEquals("v1", instance.getPluginVersion());
        assertEquals(WasmPluginInstanceScope.GLOBAL, instance.getScope());
        Assertions.assertTrue(instance.getEnabled());
        assertEquals(1, instance.getConfigurations().size());
        assertEquals("value", instance.getConfigurations().get("key"));
    }
    
    @Test
    void getWasmPluginInstanceFromCr_DomainScope_NotConfigured() {
        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
        plugin.setMetadata(createMetadata("domain", "test-plugin", "v1"));
        plugin.setSpec(createSpecWithDomainConfig("example.com"));
        
        WasmPluginInstance instance = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.DOMAIN,
                "example.com");
        Assertions.assertNotNull(instance);
        //        Assertions. assertEquals("domain", instance.getPluginName());
        assertEquals("v1", instance.getPluginVersion());
        assertEquals(WasmPluginInstanceScope.DOMAIN, instance.getScope());
        Assertions.assertTrue(instance.getEnabled());
        assertEquals(1, instance.getConfigurations().size());
        assertEquals("value", instance.getConfigurations().get("key"));
        
        WasmPluginInstance instanceNotConfigured = converter.getWasmPluginInstanceFromCr(plugin,
                WasmPluginInstanceScope.DOMAIN, "nonexistent.com");
        Assertions.assertNull(instanceNotConfigured);
    }
    
    
    @Test
    void getWasmPluginInstanceFromCr_RouteScope_Configured() {
        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
        plugin.setMetadata(createMetadata("route", "test-plugin", "v1"));
        plugin.setSpec(createSpecWithRouteConfig("test-route"));
        
        WasmPluginInstance instance = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.ROUTE,
                "test-route");
        Assertions.assertNotNull(instance);
        //        Assertions. assertEquals("route", instance.getPluginName());
        assertEquals("v1", instance.getPluginVersion());
        assertEquals(WasmPluginInstanceScope.ROUTE, instance.getScope());
        Assertions.assertTrue(instance.getEnabled());
        assertEquals(1, instance.getConfigurations().size());
        assertEquals("value", instance.getConfigurations().get("key"));
    }
    
    
    @Test
    void setWasmPluginInstanceToCr_GlobalScope_ShouldSetDefaultConfig() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        WasmPluginInstance instance = WasmPluginInstance.builder().scope(WasmPluginInstanceScope.GLOBAL).target(null)
                .enabled(true).configurations(Map.of("key", "value")).build();
        
        converter.setWasmPluginInstanceToCr(cr, instance);
        
        V1alpha1WasmPluginSpec spec = cr.getSpec();
        Assertions.assertNotNull(spec);
        assertEquals(Map.of("key", "value"), spec.getDefaultConfig());
        Assertions.assertFalse(spec.getDefaultConfigDisable());
    }
    
    @Test
    void setWasmPluginInstanceToCr_DomainScope_ShouldAddOrUpdateDomainRule() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        WasmPluginInstance instance = WasmPluginInstance.builder().scope(WasmPluginInstanceScope.DOMAIN)
                .target("example.com").enabled(true).configurations(Map.of("key", "value")).build();
        
        converter.setWasmPluginInstanceToCr(cr, instance);
        
        V1alpha1WasmPluginSpec spec = cr.getSpec();
        Assertions.assertNotNull(spec);
        List<MatchRule> matchRules = spec.getMatchRules();
        Assertions.assertNotNull(matchRules);
        assertEquals(1, matchRules.size());
        MatchRule domainRule = matchRules.get(0);
        Assertions.assertTrue(domainRule.getDomain().contains("example.com"));
        assertEquals(Map.of("key", "value"), domainRule.getConfig());
        Assertions.assertFalse(domainRule.getConfigDisable());
    }
    
    @Test
    void setWasmPluginInstanceToCr_DomainScopeExistingRule_ShouldUpdateExistingDomainRule() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        spec.setMatchRules(List.of(new MatchRule(false, Map.of("key", "original"), List.of("example.com"), List.of())));
        cr.setSpec(spec);
        
        WasmPluginInstance instance = WasmPluginInstance.builder().scope(WasmPluginInstanceScope.DOMAIN)
                .target("example.com").enabled(true).configurations(Map.of("key", "updated")).build();
        
        converter.setWasmPluginInstanceToCr(cr, instance);
        
        List<MatchRule> matchRules = cr.getSpec().getMatchRules();
        Assertions.assertNotNull(matchRules);
        assertEquals(1, matchRules.size());
        MatchRule domainRule = matchRules.get(0);
        Assertions.assertTrue(domainRule.getDomain().contains("example.com"));
        assertEquals(Map.of("key", "updated"), domainRule.getConfig());
        Assertions.assertFalse(domainRule.getConfigDisable());
    }
    
    
    @Test
    void setWasmPluginInstanceToCr_RouteScope_ShouldAddOrUpdateRouteRule() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        WasmPluginInstance instance = WasmPluginInstance.builder().scope(WasmPluginInstanceScope.ROUTE)
                .target("route-1").enabled(true).configurations(Map.of("key", "value")).build();
        
        converter.setWasmPluginInstanceToCr(cr, instance);
        
        V1alpha1WasmPluginSpec spec = cr.getSpec();
        Assertions.assertNotNull(spec);
        List<MatchRule> matchRules = spec.getMatchRules();
        Assertions.assertNotNull(matchRules);
        assertEquals(1, matchRules.size());
        MatchRule routeRule = matchRules.get(0);
        Assertions.assertTrue(routeRule.getIngress().contains("route-1"));
        assertEquals(Map.of("key", "value"), routeRule.getConfig());
        Assertions.assertFalse(routeRule.getConfigDisable());
    }
    
    
    @Test
    void removeWasmPluginInstanceFromCr_GlobalScope_TargetNull_ShouldRemoveDefaultConfig() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");
        spec.setDefaultConfig(config);
        cr.setSpec(spec);
        boolean result = converter.removeWasmPluginInstanceFromCr(cr, WasmPluginInstanceScope.GLOBAL, null);
        
        Assertions.assertTrue(result);
        Assertions.assertNull(cr.getSpec().getDefaultConfig());
    }
    
    @Test
    void removeWasmPluginInstanceFromCr_DomainScope_ValidTarget_ShouldRemoveDomain() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        List<MatchRule> matchRules = new ArrayList<>();
        MatchRule rule = new MatchRule();
        rule.setDomain(new ArrayList<String>() {{
            add("example.com");
        }});
        matchRules.add(rule);
        spec.setMatchRules(matchRules);
        cr.setSpec(spec);
        
        boolean result = converter.removeWasmPluginInstanceFromCr(cr, WasmPluginInstanceScope.DOMAIN, "example.com");
        
        Assertions.assertTrue(result);
        Assertions.assertTrue(cr.getSpec().getMatchRules().isEmpty());
    }
    
    @Test
    void removeWasmPluginInstanceFromCr_DomainScope_EmptyTarget_ShouldNotChange() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        List<MatchRule> matchRules = new ArrayList<>();
        MatchRule rule = new MatchRule();
        rule.setDomain(new ArrayList<String>() {{
            add("example.com");
        }});
        matchRules.add(rule);
        spec.setMatchRules(matchRules);
        cr.setSpec(spec);
        
        boolean result = converter.removeWasmPluginInstanceFromCr(cr, WasmPluginInstanceScope.DOMAIN, "");
        
        Assertions.assertFalse(result);
        assertEquals(1, cr.getSpec().getMatchRules().size());
    }
    
    
    @Test
    void removeWasmPluginInstanceFromCr_RouteScope_ValidTarget_ShouldRemoveIngress() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        List<MatchRule> matchRules = new ArrayList<>();
        MatchRule rule = new MatchRule();
        rule.setIngress(new ArrayList<String>() {{
            add("test-route");
        }});
        matchRules.add(rule);
        spec.setMatchRules(matchRules);
        cr.setSpec(spec);
        
        boolean result = converter.removeWasmPluginInstanceFromCr(cr, WasmPluginInstanceScope.ROUTE, "test-route");
        
        Assertions.assertTrue(result);
        Assertions.assertTrue(cr.getSpec().getMatchRules().isEmpty());
    }
    
    @Test
    void removeWasmPluginInstanceFromCr_RouteScope_EmptyTarget_ShouldNotChange() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        List<MatchRule> matchRules = new ArrayList<>();
        MatchRule rule = new MatchRule();
        rule.setIngress(new ArrayList<String>() {{
            add("test-route");
        }});
        matchRules.add(rule);
        spec.setMatchRules(matchRules);
        cr.setSpec(spec);
        
        boolean result = converter.removeWasmPluginInstanceFromCr(cr, WasmPluginInstanceScope.ROUTE, "");
        
        Assertions.assertFalse(result);
        assertEquals(1, cr.getSpec().getMatchRules().size());
    }
    
    
    @Test
    public void testV1RegistryConfig2ServiceSource_NacosType() {
        V1RegistryConfig v1RegistryConfig = new V1RegistryConfig();
        v1RegistryConfig.setType(V1McpBridge.REGISTRY_TYPE_NACOS);
        v1RegistryConfig.setDomain("testDomain");
        v1RegistryConfig.setPort(80);
        v1RegistryConfig.setName("testName");
        v1RegistryConfig.setNacosNamespaceId("testNamespaceId");
        v1RegistryConfig.setNacosGroups(List.of("testGroup1", "testGroup2"));
        
        ServiceSource serviceSource = converter.v1RegistryConfig2ServiceSource(v1RegistryConfig);
        
        Assertions.assertNotNull(serviceSource);
        assertEquals(V1McpBridge.REGISTRY_TYPE_NACOS, serviceSource.getType());
        assertEquals("testDomain", serviceSource.getDomain());
        assertEquals(80, serviceSource.getPort());
        assertEquals("testName", serviceSource.getName());
        Map<String, Object> properties = serviceSource.getProperties();
        Assertions.assertNotNull(properties);
        assertEquals("testNamespaceId", properties.get(V1McpBridge.REGISTRY_TYPE_NACOS_NAMESPACE_ID));
        assertEquals(List.of("testGroup1", "testGroup2"), properties.get(V1McpBridge.REGISTRY_TYPE_NACOS_GROUPS));
    }
    
    @Test
    public void testV1RegistryConfig2ServiceSource_ZkType() {
        V1RegistryConfig v1RegistryConfig = new V1RegistryConfig();
        v1RegistryConfig.setType(V1McpBridge.REGISTRY_TYPE_ZK);
        v1RegistryConfig.setDomain("testDomain");
        v1RegistryConfig.setPort(80);
        v1RegistryConfig.setName("testName");
        v1RegistryConfig.setZkServicesPath(List.of("testPath1", "testPath2"));
        
        ServiceSource serviceSource = converter.v1RegistryConfig2ServiceSource(v1RegistryConfig);
        
        Assertions.assertNotNull(serviceSource);
        assertEquals(V1McpBridge.REGISTRY_TYPE_ZK, serviceSource.getType());
        assertEquals("testDomain", serviceSource.getDomain());
        assertEquals(80, serviceSource.getPort());
        assertEquals("testName", serviceSource.getName());
        Map<String, Object> properties = serviceSource.getProperties();
        Assertions.assertNotNull(properties);
        assertEquals(List.of("testPath1", "testPath2"), properties.get(V1McpBridge.REGISTRY_TYPE_ZK_SERVICES_PATH));
    }
    
    @Test
    public void testV1RegistryConfig2ServiceSource_ConsulType() {
        V1RegistryConfig v1RegistryConfig = new V1RegistryConfig();
        v1RegistryConfig.setType(V1McpBridge.REGISTRY_TYPE_CONSUL);
        v1RegistryConfig.setDomain("testDomain");
        v1RegistryConfig.setPort(80);
        v1RegistryConfig.setName("testName");
        v1RegistryConfig.setConsulDataCenter("testDataCenter");
        v1RegistryConfig.setConsulServiceTag("testServiceTag");
        v1RegistryConfig.setConsulRefreshInterval(30);
        
        ServiceSource serviceSource = converter.v1RegistryConfig2ServiceSource(v1RegistryConfig);
        
        Assertions.assertNotNull(serviceSource);
        assertEquals(V1McpBridge.REGISTRY_TYPE_CONSUL, serviceSource.getType());
        assertEquals("testDomain", serviceSource.getDomain());
        assertEquals(80, serviceSource.getPort());
        assertEquals("testName", serviceSource.getName());
        Map<String, Object> properties = serviceSource.getProperties();
        Assertions.assertNotNull(properties);
        assertEquals("testDataCenter", properties.get(V1McpBridge.REGISTRY_TYPE_CONSUL_DATA_CENTER));
        assertEquals("testServiceTag", properties.get(V1McpBridge.REGISTRY_TYPE_CONSUL_SERVICE_TAG));
        assertEquals(30, properties.get(V1McpBridge.REGISTRY_TYPE_CONSUL_REFRESH_INTERVAL));
    }
    
    @Test
    public void testV1RegistryConfig2ServiceSource_NullInput() {
        ServiceSource serviceSource = converter.v1RegistryConfig2ServiceSource(null);
        
        Assertions.assertNotNull(serviceSource);
        assertEquals(new ServiceSource(), serviceSource);
    }
    
    
    @Test
    public void addV1McpBridgeRegistry_RegistryDoesNotExist_ShouldAddAndReturnRegistryConfig() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        List<V1RegistryConfig> registries = new ArrayList<>();
        spec.setRegistries(registries);
        
        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080,
                new HashMap<>(), null);
        
        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);
        
        Assertions.assertNotNull(result);
        assertEquals(serviceSource.getName(), result.getName());
        assertEquals(serviceSource.getDomain(), result.getDomain());
        assertEquals(serviceSource.getType(), result.getType());
        assertEquals(serviceSource.getPort(), result.getPort());
        Assertions.assertTrue(registries.contains(result));
    }
    
    @Test
    public void addV1McpBridgeRegistry_RegistryExists_ShouldUpdateAndReturnRegistryConfig() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        List<V1RegistryConfig> registries = new ArrayList<>();
        V1RegistryConfig existingRegistry = new V1RegistryConfig();
        existingRegistry.setName("testService");
        registries.add(existingRegistry);
        spec.setRegistries(registries);
        
        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080,
                new HashMap<>(), null);
        
        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);
        
        Assertions.assertNotNull(result);
        assertEquals(serviceSource.getName(), result.getName());
        assertEquals(serviceSource.getDomain(), result.getDomain());
        assertEquals(serviceSource.getType(), result.getType());
        assertEquals(serviceSource.getPort(), result.getPort());
        Assertions.assertTrue(registries.contains(result));
    }
    
    @Test
    public void addV1McpBridgeRegistry_NullServiceSource_ShouldReturnNull() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        List<V1RegistryConfig> registries = new ArrayList<>();
        spec.setRegistries(registries);
        
        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, null);
        
        Assertions.assertNull(result);
    }
    
    @Test
    public void addV1McpBridgeRegistry_NullSpec_ShouldCreateSpecAndAddRegistry() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        
        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080,
                new HashMap<>(), null);
        
        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);
        
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(v1McpBridge.getSpec());
        Assertions.assertNotNull(v1McpBridge.getSpec().getRegistries());
        Assertions.assertTrue(v1McpBridge.getSpec().getRegistries().contains(result));
    }
    
    @Test
    public void addV1McpBridgeRegistry_NullRegistries_ShouldCreateRegistriesAndAddRegistry() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080,
                new HashMap<>(), null);
        
        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);
        
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(v1McpBridge.getSpec().getRegistries());
        Assertions.assertTrue(v1McpBridge.getSpec().getRegistries().contains(result));
    }
    
    
    @Test
    public void removeV1McpBridgeRegistry_RegistryExists_ShouldRemoveAndReturnRegistryConfig() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        List<V1RegistryConfig> registries = new ArrayList<>();
        V1RegistryConfig registryConfig = new V1RegistryConfig();
        registryConfig.setName("testRegistry");
        registries.add(registryConfig);
        spec.setRegistries(registries);
        
        V1RegistryConfig result = converter.removeV1McpBridgeRegistry(v1McpBridge, "testRegistry");
        
        Assertions.assertNotNull(result);
        assertEquals("testRegistry", result.getName());
        Assertions.assertTrue(spec.getRegistries().isEmpty());
    }
    
    @Test
    public void removeV1McpBridgeRegistry_RegistryDoesNotExist_ShouldReturnNull() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        List<V1RegistryConfig> registries = new ArrayList<>();
        V1RegistryConfig registryConfig = new V1RegistryConfig();
        registryConfig.setName("existingRegistry");
        registries.add(registryConfig);
        spec.setRegistries(registries);
        
        V1RegistryConfig result = converter.removeV1McpBridgeRegistry(v1McpBridge, "nonExistingRegistry");
        
        Assertions.assertNull(result);
        assertEquals(1, spec.getRegistries().size());
    }
    
    @Test
    public void removeV1McpBridgeRegistry_NoRegistries_ShouldReturnNull() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);
        
        spec.setRegistries(new ArrayList<>());
        
        V1RegistryConfig result = converter.removeV1McpBridgeRegistry(v1McpBridge, "testRegistry");
        
        Assertions.assertNull(result);
    }
    
    
    @Test
    public void removeV1McpBridgeRegistry_NullSpec_ShouldReturnNull() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        
        v1McpBridge.setSpec(null);
        
        V1RegistryConfig result = converter.removeV1McpBridgeRegistry(v1McpBridge, "testRegistry");
        
        Assertions.assertNull(result);
    }
    
    
    private V1Ingress buildBasicSupportedIngress() {
        V1Ingress ingress = new V1Ingress();
        
        V1ObjectMeta metadata = new V1ObjectMeta();
        ingress.setMetadata(metadata);
        
        V1IngressSpec spec = new V1IngressSpec();
        ingress.setSpec(spec);
        
        V1IngressRule rule = new V1IngressRule();
        
        List<V1IngressRule> rules = new ArrayList<>();
        rules.add(rule);
        spec.setRules(rules);
        
        V1HTTPIngressRuleValue httpRule = new V1HTTPIngressRuleValue();
        rule.setHttp(httpRule);
        
        V1HTTPIngressPath path = new V1HTTPIngressPath();
        
        List<V1HTTPIngressPath> paths = new ArrayList<>();
        paths.add(path);
        httpRule.setPaths(paths);
        
        path.setPathType(KubernetesConstants.IngressPathType.PREFIX);
        path.setPath("/");
        
        V1IngressBackend backend = new V1IngressBackend();
        V1TypedLocalObjectReference reference = new V1TypedLocalObjectReference();
        reference.setApiGroup(V1McpBridge.API_GROUP);
        reference.setKind(V1McpBridge.KIND);
        reference.setName(V1McpBridge.DEFAULT_NAME);
        backend.setResource(reference);
        path.setBackend(backend);
        
        return ingress;
    }
    
    private static Route buildBasicRoute() {
        Route route = new Route();
        route.setDomains(new ArrayList<>());
        route.setPath(new RoutePredicate());
        route.setCors(new CorsConfig());
        route.setCustomConfigs(new HashMap<>());
        return route;
    }
    
    private V1alpha1WasmPluginSpec createSpecWithGlobalConfig() {
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        spec.setDefaultConfig(Collections.singletonMap("key", "value"));
        spec.setDefaultConfigDisable(false);
        return spec;
    }
    
    private V1alpha1WasmPluginSpec createSpecWithDomainConfig(String domain) {
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        MatchRule matchRule = new MatchRule();
        matchRule.setDomain(Collections.singletonList(domain));
        matchRule.setConfig(Collections.singletonMap("key", "value"));
        matchRule.setConfigDisable(false);
        spec.setMatchRules(Collections.singletonList(matchRule));
        return spec;
    }
    
    private V1alpha1WasmPluginSpec createSpecWithRouteConfig(String route) {
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        MatchRule matchRule = new MatchRule();
        matchRule.setIngress(Collections.singletonList(route));
        matchRule.setConfig(Collections.singletonMap("key", "value"));
        matchRule.setConfigDisable(false);
        spec.setMatchRules(Collections.singletonList(matchRule));
        return spec;
    }
    
    private V1ObjectMeta createMetadata(String name, String pluginName, String pluginVersion) {
        Map<String, String> labels = new HashMap<>();
        labels.put(KubernetesConstants.Label.WASM_PLUGIN_NAME_KEY, pluginName);
        labels.put(KubernetesConstants.Label.WASM_PLUGIN_VERSION_KEY, pluginVersion);
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(name);
        metadata.setLabels(labels);
        return metadata;
    }
    
    
    private V1ObjectMeta createMetadata() {
        Map<String, String> labels = new HashMap<>();
        labels.put(KubernetesConstants.Label.WASM_PLUGIN_NAME_KEY, "test-plugin");
        labels.put(KubernetesConstants.Label.WASM_PLUGIN_VERSION_KEY, "v1");
        labels.put(KubernetesConstants.Label.WASM_PLUGIN_CATEGORY_KEY, "test-category");
        labels.put(KubernetesConstants.Label.WASM_PLUGIN_BUILT_IN_KEY, "true");
        
        Map<String, String> annotations = new HashMap<>();
        annotations.put(KubernetesConstants.Annotation.WASM_PLUGIN_TITLE_KEY, "Test Plugin");
        annotations.put(KubernetesConstants.Annotation.WASM_PLUGIN_DESCRIPTION_KEY, "A test plugin");
        annotations.put(KubernetesConstants.Annotation.WASM_PLUGIN_ICON_KEY, "icon.png");
        
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setLabels(labels);
        metadata.setAnnotations(annotations);
        return metadata;
    }
    
    private V1alpha1WasmPluginSpec createSpec() {
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        spec.setUrl("test/image:v1");
        spec.setPhase(PluginPhase.UNSPECIFIED.getName());
        spec.setPriority(10);
        return spec;
    }
}
