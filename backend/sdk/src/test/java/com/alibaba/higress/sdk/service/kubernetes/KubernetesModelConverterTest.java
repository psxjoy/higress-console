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
import com.alibaba.higress.sdk.exception.BusinessException;
import com.alibaba.higress.sdk.exception.ValidationException;
import com.alibaba.higress.sdk.model.*;
import com.alibaba.higress.sdk.model.route.CorsConfig;
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
import io.kubernetes.client.openapi.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class KubernetesModelConverterTest {

    private KubernetesModelConverter converter;
    private Domain domain;

    @BeforeEach
    public void setUp() {
        KubernetesClientService service = mock(KubernetesClientService.class);
        converter = new KubernetesModelConverter(service);
        domain = Domain.builder()
                .name("www.example.com")
                .version("1")
                .enableHttps("true")
                .certIdentifier("my-cert")
                .build();
    }

    @AfterEach
    public void tearDown() {
        converter = null;
    }


//    @Test
//    void tlsCertificate2Secret_ValidCertificateWithDomains_ShouldSetCorrectLabels() throws IOException {
//        String certificatePEM = new String(Files.readAllBytes(Paths.get("src/test/resources/valid-certificate.pem")));
//        TlsCertificate certificate = TlsCertificate.builder()
//                .cert(certificatePEM)
//                .key("dummyKey")
//                .name("test-certificate")
//                .version("1")
//                .domains(Collections.emptyList())
//                .build();
//
//        V1Secret secret = converter.tlsCertificate2Secret(certificate);
//
//        Assertions.assertNotNull(secret);
//        Assertions.assertEquals(KubernetesConstants.SECRET_TYPE_TLS, secret.getType());
//        Assertions.assertEquals("test-certificate", secret.getMetadata().getName());
//        Assertions.assertEquals("1", secret.getMetadata().getResourceVersion());
//
//        Map<String, byte[]> data = secret.getData();
//        Assertions.assertNotNull(data);
//        Assertions.assertEquals(2, data.size());
//        Assertions.assertArrayEquals(TypeUtil.string2Bytes(certificatePEM), data.get(KubernetesConstants.SECRET_TLS_CRT_FIELD));
//        Assertions.assertArrayEquals(TypeUtil.string2Bytes("dummyKey"), data.get(KubernetesConstants.SECRET_TLS_KEY_FIELD));
//
//        Map<String, String> labels = secret.getMetadata().getLabels();
//        Assertions.assertNotNull(labels);
//        Assertions.assertEquals(3, labels.size());
//        Assertions.assertEquals(KubernetesConstants.Label.DOMAIN_VALUE_DUMMY, labels.get("domain.higress.io/example.com"));
//        Assertions.assertEquals(KubernetesConstants.Label.DOMAIN_VALUE_DUMMY, labels.get("domain.higress.io/www.example.com"));
//        Assertions.assertEquals(KubernetesConstants.Label.DOMAIN_VALUE_DUMMY, labels.get("domain.higress.io/test.example.com"));
//    }

    @Test
    void tlsCertificate2Secret_ValidCertificateWithoutDomains_ShouldNotSetLabels() {
        TlsCertificate certificate = TlsCertificate.builder()
                .cert("dummyCert")
                .key("dummyKey")
                .name("test-certificate")
                .version("1")
                .domains(Collections.emptyList())
                .build();

        V1Secret secret = converter.tlsCertificate2Secret(certificate);

        Assertions.assertNotNull(secret);
        Assertions.assertEquals(KubernetesConstants.SECRET_TYPE_TLS, secret.getType());
        Assertions.assertEquals("test-certificate", secret.getMetadata().getName());
        Assertions.assertEquals("1", secret.getMetadata().getResourceVersion());

        Map<String, byte[]> data = secret.getData();
        Assertions.assertNotNull(data);
        Assertions.assertEquals(2, data.size());
        Assertions.assertArrayEquals(TypeUtil.string2Bytes("dummyCert"), data.get(KubernetesConstants.SECRET_TLS_CRT_FIELD));
        Assertions.assertArrayEquals(TypeUtil.string2Bytes("dummyKey"), data.get(KubernetesConstants.SECRET_TLS_KEY_FIELD));

        Map<String, String> labels = secret.getMetadata().getLabels();
        Assertions.assertNull(labels);
//        Assertions.assertTrue(labels.isEmpty());
    }


    @Test
    void getWasmPluginInstanceFromCr_GlobalScope_Configured() {
        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
        plugin.setMetadata(createMetadata("global", "test-plugin", "v1"));
        plugin.setSpec(createSpecWithGlobalConfig());

        WasmPluginInstance instance = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.GLOBAL, null);
        Assertions.assertNotNull(instance);
//        Assertions. assertEquals("global", instance.getPluginName());
        Assertions.assertEquals("v1", instance.getPluginVersion());
        Assertions.assertEquals(WasmPluginInstanceScope.GLOBAL, instance.getScope());
        Assertions.assertTrue(instance.getEnabled());
        Assertions.assertEquals(1, instance.getConfigurations().size());
        Assertions.assertEquals("value", instance.getConfigurations().get("key"));
    }

    @Test
    void getWasmPluginInstanceFromCr_DomainScope_NotConfigured() {
        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
        plugin.setMetadata(createMetadata("domain", "test-plugin", "v1"));
        plugin.setSpec(createSpecWithDomainConfig("example.com"));

        WasmPluginInstance instance = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.DOMAIN, "example.com");
        Assertions.assertNotNull(instance);
//        Assertions. assertEquals("domain", instance.getPluginName());
        Assertions.assertEquals("v1", instance.getPluginVersion());
        Assertions.assertEquals(WasmPluginInstanceScope.DOMAIN, instance.getScope());
        Assertions.assertTrue(instance.getEnabled());
        Assertions.assertEquals(1, instance.getConfigurations().size());
        Assertions.assertEquals("value", instance.getConfigurations().get("key"));

        WasmPluginInstance instanceNotConfigured = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.DOMAIN, "nonexistent.com");
        Assertions.assertNull(instanceNotConfigured);
    }


    @Test
    void getWasmPluginInstanceFromCr_RouteScope_Configured() {
        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
        plugin.setMetadata(createMetadata("route", "test-plugin", "v1"));
        plugin.setSpec(createSpecWithRouteConfig("test-route"));

        WasmPluginInstance instance = converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.ROUTE, "test-route");
        Assertions.assertNotNull(instance);
//        Assertions. assertEquals("route", instance.getPluginName());
        Assertions.assertEquals("v1", instance.getPluginVersion());
        Assertions.assertEquals(WasmPluginInstanceScope.ROUTE, instance.getScope());
        Assertions.assertTrue(instance.getEnabled());
        Assertions.assertEquals(1, instance.getConfigurations().size());
        Assertions.assertEquals("value", instance.getConfigurations().get("key"));
    }

//    @Test
//    void getWasmPluginInstanceFromCr_InvalidScope_ThrowsException() {
//        V1alpha1WasmPlugin plugin = new V1alpha1WasmPlugin();
//        plugin.setMetadata(createMetadata("invalid", "test-plugin", "v1"));
//        plugin.setSpec(createSpecWithGlobalConfig());
//
//        Assertions. assertThrows(IllegalArgumentException.class, () -> converter.getWasmPluginInstanceFromCr(plugin, WasmPluginInstanceScope.fromId("invalid"), null));
//    }

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

    /******
     *
     */
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
        Assertions.assertEquals("testRegistry", result.getName());
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
        Assertions.assertEquals(1, spec.getRegistries().size());
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

    @Test
    public void addV1McpBridgeRegistry_RegistryDoesNotExist_ShouldAddAndReturnRegistryConfig() {
        V1McpBridge v1McpBridge = new V1McpBridge();
        V1McpBridgeSpec spec = new V1McpBridgeSpec();
        v1McpBridge.setSpec(spec);

        List<V1RegistryConfig> registries = new ArrayList<>();
        spec.setRegistries(registries);

        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080, new HashMap<>(), null);

        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(serviceSource.getName(), result.getName());
        Assertions.assertEquals(serviceSource.getDomain(), result.getDomain());
        Assertions.assertEquals(serviceSource.getType(), result.getType());
        Assertions.assertEquals(serviceSource.getPort(), result.getPort());
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

        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080, new HashMap<>(), null);

        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(serviceSource.getName(), result.getName());
        Assertions.assertEquals(serviceSource.getDomain(), result.getDomain());
        Assertions.assertEquals(serviceSource.getType(), result.getType());
        Assertions.assertEquals(serviceSource.getPort(), result.getPort());
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

        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080, new HashMap<>(), null);

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

        ServiceSource serviceSource = new ServiceSource("testService", "1.0", "http", "test.domain.com", 8080, new HashMap<>(), null);

        V1RegistryConfig result = converter.addV1McpBridgeRegistry(v1McpBridge, serviceSource);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(v1McpBridge.getSpec().getRegistries());
        Assertions.assertTrue(v1McpBridge.getSpec().getRegistries().contains(result));
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
        Assertions.assertEquals(V1McpBridge.REGISTRY_TYPE_NACOS, serviceSource.getType());
        Assertions.assertEquals("testDomain", serviceSource.getDomain());
        Assertions.assertEquals(80, serviceSource.getPort());
        Assertions.assertEquals("testName", serviceSource.getName());
        Map<String, Object> properties = serviceSource.getProperties();
        Assertions.assertNotNull(properties);
        Assertions.assertEquals("testNamespaceId", properties.get(V1McpBridge.REGISTRY_TYPE_NACOS_NAMESPACE_ID));
        Assertions.assertEquals(List.of("testGroup1", "testGroup2"), properties.get(V1McpBridge.REGISTRY_TYPE_NACOS_GROUPS));
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
        Assertions.assertEquals(V1McpBridge.REGISTRY_TYPE_ZK, serviceSource.getType());
        Assertions.assertEquals("testDomain", serviceSource.getDomain());
        Assertions.assertEquals(80, serviceSource.getPort());
        Assertions.assertEquals("testName", serviceSource.getName());
        Map<String, Object> properties = serviceSource.getProperties();
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(List.of("testPath1", "testPath2"), properties.get(V1McpBridge.REGISTRY_TYPE_ZK_SERVICES_PATH));
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
        Assertions.assertEquals(V1McpBridge.REGISTRY_TYPE_CONSUL, serviceSource.getType());
        Assertions.assertEquals("testDomain", serviceSource.getDomain());
        Assertions.assertEquals(80, serviceSource.getPort());
        Assertions.assertEquals("testName", serviceSource.getName());
        Map<String, Object> properties = serviceSource.getProperties();
        Assertions.assertNotNull(properties);
        Assertions.assertEquals("testDataCenter", properties.get(V1McpBridge.REGISTRY_TYPE_CONSUL_DATA_CENTER));
        Assertions.assertEquals("testServiceTag", properties.get(V1McpBridge.REGISTRY_TYPE_CONSUL_SERVICE_TAG));
        Assertions.assertEquals(30, properties.get(V1McpBridge.REGISTRY_TYPE_CONSUL_REFRESH_INTERVAL));
    }

    @Test
    public void testV1RegistryConfig2ServiceSource_NullInput() {
        ServiceSource serviceSource = converter.v1RegistryConfig2ServiceSource(null);

        Assertions.assertNotNull(serviceSource);
        Assertions.assertEquals(new ServiceSource(), serviceSource);
    }


    //--
    @Test
    void ingress2Route_ValidIngressWithSingleRule_ReturnsValidRoute() {
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
        Assertions.assertEquals("test-ingress", route.getName());
        Assertions.assertEquals("1", route.getVersion());
    }

    @Test
    void ingress2Route_ValidIngressWithMultipleRules_ReturnsValidRoute() {
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
        Assertions.assertEquals("test-ingress", route.getName());
        Assertions.assertEquals("1", route.getVersion());
        Assertions.assertEquals(null, route.getDomains());
    }

    @Test
    void ingress2Route_ValidIngressWithTLS_ReturnsValidRoute() {
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
        Assertions.assertEquals("test-ingress", route.getName());
        Assertions.assertEquals("1", route.getVersion());
        Assertions.assertEquals(null, route.getDomains());
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
        Assertions.assertEquals("test-ingress", route.getName());
        Assertions.assertEquals("1", route.getVersion());
        Assertions.assertNotNull(route.getCustomConfigs());
        Assertions.assertEquals("annotation-value", route.getCustomConfigs().get("example.com"));
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
        Assertions.assertEquals(null, route.getName());
        Assertions.assertEquals(null, route.getVersion());
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
        Assertions.assertEquals("test-ingress", route.getName());
        Assertions.assertEquals("1", route.getVersion());
        Assertions.assertNull(route.getDomains());
    }

    //--
    @Test
    void mergeWasmPluginSpec_SourceSpecNull_DestinationSpecUnchanged() {
        V1alpha1WasmPlugin srcPlugin = new V1alpha1WasmPlugin();
        srcPlugin.setSpec(null);

        V1alpha1WasmPlugin dstPlugin = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec dstSpec = new V1alpha1WasmPluginSpec();
        dstSpec.setDefaultConfig(new HashMap<>());
        dstPlugin.setSpec(dstSpec);

        converter.mergeWasmPluginSpec(srcPlugin, dstPlugin);

        Assertions.assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
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

        Assertions.assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
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

        Assertions.assertEquals(expected, dstPlugin.getSpec().getMatchRules());
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

        Assertions.assertEquals(expected, dstPlugin.getSpec().getMatchRules());
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

        Assertions.assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
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

        Assertions.assertEquals(new HashMap<>(), dstPlugin.getSpec().getDefaultConfig());
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

        Assertions.assertEquals(expected, dstPlugin.getSpec().getMatchRules());
    }

    //--
    @Test
    void wasmPluginToCr_ValidInput_ShouldConvertCorrectly() {
        WasmPlugin plugin = WasmPlugin.builder()
                .name("test-plugin")
                .pluginVersion("1.0.0")
                .version("1")
                .category("test-category")
                .title("Test Plugin")
                .description("A test plugin")
                .icon("test-icon")
                .builtIn(true)
                .imageRepository("test-repository")
                .imageVersion("test-version")
                .phase("test-phase")
                .priority(10)
                .build();

        V1alpha1WasmPlugin cr = converter.wasmPluginToCr(plugin);

        Assertions.assertNotNull(cr);
        Assertions.assertNotNull(cr.getMetadata());
        Assertions.assertEquals("test-plugin-1.0.0", cr.getMetadata().getName());
        Assertions.assertEquals("1", cr.getMetadata().getResourceVersion());

        Assertions.assertEquals("test-plugin", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_NAME_KEY));
        Assertions.assertEquals("1.0.0", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_VERSION_KEY));
        Assertions.assertEquals("test-category", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_CATEGORY_KEY));
        Assertions.assertEquals("true", cr.getMetadata().getLabels().get(KubernetesConstants.Label.WASM_PLUGIN_BUILT_IN_KEY));

        Assertions.assertEquals("Test Plugin", cr.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.WASM_PLUGIN_TITLE_KEY));
        Assertions.assertEquals("A test plugin", cr.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.WASM_PLUGIN_DESCRIPTION_KEY));
        Assertions.assertEquals("test-icon", cr.getMetadata().getAnnotations().get(KubernetesConstants.Annotation.WASM_PLUGIN_ICON_KEY));

        Assertions.assertNotNull(cr.getSpec());
        Assertions.assertEquals("test-phase", cr.getSpec().getPhase());
        Assertions.assertEquals(10, cr.getSpec().getPriority().intValue());
        Assertions.assertEquals("oci://test-repository:test-version", cr.getSpec().getUrl());
    }

    @Test
    void wasmPluginToCr_NullImageRepository_ShouldHandleCorrectly() {
        WasmPlugin plugin = WasmPlugin.builder()
                .name("test-plugin")
                .pluginVersion("1.0.0")
                .version("1")
                .imageRepository(null)
                .imageVersion("test-version")
                .build();

        V1alpha1WasmPlugin cr = converter.wasmPluginToCr(plugin);

        Assertions.assertNotNull(cr);
        Assertions.assertNotNull(cr.getSpec());
        Assertions.assertEquals(null, cr.getSpec().getUrl());
    }

    @Test
    void wasmPluginToCr_EmptyImageVersion_ShouldHandleCorrectly() {
        WasmPlugin plugin = WasmPlugin.builder()
                .name("test-plugin")
                .pluginVersion("1.0.0")
                .version("1")
                .imageRepository("test-repository")
                .imageVersion("")
                .build();

        V1alpha1WasmPlugin cr = converter.wasmPluginToCr(plugin);

        Assertions.assertNotNull(cr);
        Assertions.assertNotNull(cr.getSpec());
        Assertions.assertEquals("oci://test-repository", cr.getSpec().getUrl());
    }

    //--
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
        Assertions.assertEquals("test-plugin", plugin.getName());
        Assertions.assertEquals("v1", plugin.getPluginVersion());
        Assertions.assertEquals("test-category", plugin.getCategory());
        Assertions.assertEquals(Boolean.TRUE, plugin.getBuiltIn());
        Assertions.assertEquals("Test Plugin", plugin.getTitle());
        Assertions.assertEquals("A test plugin", plugin.getDescription());
        Assertions.assertEquals("icon.png", plugin.getIcon());
        Assertions.assertEquals("test/image", plugin.getImageRepository());
        Assertions.assertEquals("v1", plugin.getImageVersion());
        Assertions.assertEquals(PluginPhase.UNSPECIFIED.getName(), plugin.getPhase());
        Assertions.assertEquals(10, plugin.getPriority());
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
        Assertions.assertEquals(null, plugin.getName());
        Assertions.assertEquals(null, plugin.getPluginVersion());
        Assertions.assertEquals("test-category", plugin.getCategory());
        Assertions.assertEquals(Boolean.TRUE, plugin.getBuiltIn());
        Assertions.assertEquals("Test Plugin", plugin.getTitle());
        Assertions.assertEquals("A test plugin", plugin.getDescription());
        Assertions.assertEquals("icon.png", plugin.getIcon());
        Assertions.assertEquals("image", plugin.getImageRepository());
        Assertions.assertEquals(null, plugin.getImageVersion());
        Assertions.assertEquals(PluginPhase.UNSPECIFIED.getName(), plugin.getPhase());
        Assertions.assertEquals(10, plugin.getPriority());
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
    //--

    /*********
     *
     */


    //    @Test
//    void secret2TlsCertificate_ValidSecretWithCertAndKey_ShouldReturnTlsCertificate() {
//        // Arrange
//        V1Secret secret = new V1Secret();
//        secret.setData(new HashMap<String, byte[]>() {{
//            put(KubernetesConstants.SECRET_TLS_CRT_FIELD, "certData".getBytes());
//            put(KubernetesConstants.SECRET_TLS_KEY_FIELD, "keyData".getBytes());
//        }});
//        secret.setMetadata(new V1ObjectMeta() {{
//            setName("test-secret");
//            setResourceVersion("123");
//        }});
//
//        X509Certificate certificate = mock(X509Certificate.class);
//        Mockito.when(certificate.getNotBefore()).thenReturn(new Date());
//        Mockito.when(certificate.getNotAfter()).thenReturn(new Date());
//        Mockito.when(certificate.getSubjectDN().getPrincipal()).thenReturn("CN=test");
//        Mockito.when(certificate.getIssuerDN().getPrincipal()).thenReturn("CN=test-ca");
//
//        CertificateFactory certificateFactory = mock(CertificateFactory.class);
//        Mockito.when(certificateFactory.getInstance("X509")).thenReturn(certificateFactory);
//        Mockito.when(certificateFactory.generateCertificate(Mockito.any(ByteArrayInputStream.class))).thenReturn(certificate);
//
//        try (MockedStatic<TypeUtil> mockedTypeUtil = Mockito.mockStatic(TypeUtil.class)) {
//            mockedTypeUtil.when(() -> TypeUtil.bytes2String(Mockito.any(byte[].class))).thenAnswer(invocation -> new String((byte[]) invocation.getArguments()[0]));
//            mockedTypeUtil.when(() -> TypeUtil.date2LocalDateTime(Mockito.any(Date.class))).thenAnswer(invocation -> ((Date) invocation.getArguments()[0]).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
//
//            // Act
//            TlsCertificate tlsCertificate = converter.secret2TlsCertificate(secret);
//
//            // Assert
//            Assertions.assertNotNull(tlsCertificate);
//            Assertions.assertEquals("test-secret", tlsCertificate.getName());
//            Assertions.assertEquals("123", tlsCertificate.getVersion());
//            Assertions.assertEquals("certData", tlsCertificate.getCert());
//            Assertions.assertEquals("keyData", tlsCertificate.getKey());
//            Assertions.assertNotNull(tlsCertificate.getValidityStart());
//            Assertions.assertNotNull(tlsCertificate.getValidityEnd());
//            Assertions.assertNotNull(tlsCertificate.getDomains());
//        }
//    }
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
        Assertions.assertEquals("test-secret", tlsCertificate.getName());
        Assertions.assertEquals("123", tlsCertificate.getVersion());
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
        Assertions.assertEquals("certData", tlsCertificate.getCert());
        Assertions.assertEquals("keyData", tlsCertificate.getKey());
//        Assertions.assertNotNull(tlsCertificate.getValidityStart());
//        Assertions.assertNotNull(tlsCertificate.getValidityEnd());
//        Assertions.assertNotNull(tlsCertificate.getDomains());
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
        Assertions.assertEquals("test-secret", tlsCertificate.getName());
        Assertions.assertEquals("123", tlsCertificate.getVersion());
        Assertions.assertNull(tlsCertificate.getCert());
        Assertions.assertNull(tlsCertificate.getKey());
        Assertions.assertNull(tlsCertificate.getValidityStart());
        Assertions.assertNull(tlsCertificate.getValidityEnd());
        Assertions.assertNull(tlsCertificate.getDomains());
    }


//    @Test
//    public void testDomain2ConfigMap_ValidDomain_ConfigMapCreated() {
//        try (MockedStatic<KubernetesUtil> mockedStatic = mockStatic(KubernetesUtil.class)) {
//            mockedStatic.when(() -> KubernetesUtil.normalizeDomainName(ArgumentMatchers.anyString())).thenReturn("www.example.com");
//
//            V1ConfigMap configMap = converter.domain2ConfigMap(domain);
//
//            Assertions.assertNotNull(configMap, "ConfigMap should not be null");
//            Assertions.assertEquals("domain-www.example.com", configMap.getMetadata().getName(), "ConfigMap name should be 'domain-www.example.com'");
//            Assertions.assertEquals("1", configMap.getMetadata().getResourceVersion(), "Resource version should be '1'");
//            Map<String, String> expectedData = new HashMap<>();
//            expectedData.put(CommonKey.DOMAIN, "www.example.com");
//            expectedData.put(KubernetesConstants.K8S_CERT, "my-cert");
//            expectedData.put(KubernetesConstants.K8S_ENABLE_HTTPS, "true");
//            Assertions.assertEquals(expectedData, configMap.getData(), "ConfigMap data should match expected data");
//        }
//    }

    @Test
    public void testDomainName2ConfigMapName_NormalDomainName_ConfigMapNameCreated() {
        String domainName = "www.example.com";
        String expectedConfigMapName = "domain-www.example.com";
        String actualConfigMapName = converter.domainName2ConfigMapName(domainName);

        Assertions.assertEquals(expectedConfigMapName, actualConfigMapName, "ConfigMap name should be 'domain-www.example.com'");
    }

    @Test
    public void testDomainName2ConfigMapName_WildcardDomainName_ConfigMapNameCreated() {
        String domainName = "*.example.com";
        String expectedConfigMapName = "domain-wildcard.example.com";
        String actualConfigMapName = converter.domainName2ConfigMapName(domainName);

        Assertions.assertEquals(expectedConfigMapName, actualConfigMapName, "ConfigMap name should be 'domain-wildcard.example.com'");
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
        Assertions.assertEquals("example.com", domain.getName());
        Assertions.assertEquals("1", domain.getVersion());
        Assertions.assertEquals("cert-identifier", domain.getCertIdentifier());
        Assertions.assertEquals("true", domain.getEnableHttps());
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
        Assertions.assertEquals("example.com", domain.getName());
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
        Assertions.assertEquals("The ConfigMap data is illegal", exception.getMessage());
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
        Assertions.assertEquals("example.com", domain.getName());
        Assertions.assertNull(domain.getCertIdentifier());
        Assertions.assertNull(domain.getEnableHttps());
    }


    @Test
    void setWasmPluginInstanceToCr_GlobalScope_ShouldSetDefaultConfig() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        WasmPluginInstance instance = WasmPluginInstance.builder()
                .scope(WasmPluginInstanceScope.GLOBAL)
                .target(null)
                .enabled(true)
                .configurations(Map.of("key", "value"))
                .build();

        converter.setWasmPluginInstanceToCr(cr, instance);

        V1alpha1WasmPluginSpec spec = cr.getSpec();
        Assertions.assertNotNull(spec);
        Assertions.assertEquals(Map.of("key", "value"), spec.getDefaultConfig());
        Assertions.assertFalse(spec.getDefaultConfigDisable());
    }

    @Test
    void setWasmPluginInstanceToCr_DomainScope_ShouldAddOrUpdateDomainRule() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        WasmPluginInstance instance = WasmPluginInstance.builder()
                .scope(WasmPluginInstanceScope.DOMAIN)
                .target("example.com")
                .enabled(true)
                .configurations(Map.of("key", "value"))
                .build();

        converter.setWasmPluginInstanceToCr(cr, instance);

        V1alpha1WasmPluginSpec spec = cr.getSpec();
        Assertions.assertNotNull(spec);
        List<MatchRule> matchRules = spec.getMatchRules();
        Assertions.assertNotNull(matchRules);
        Assertions.assertEquals(1, matchRules.size());
        MatchRule domainRule = matchRules.get(0);
        Assertions.assertTrue(domainRule.getDomain().contains("example.com"));
        Assertions.assertEquals(Map.of("key", "value"), domainRule.getConfig());
        Assertions.assertFalse(domainRule.getConfigDisable());
    }

    @Test
    void setWasmPluginInstanceToCr_DomainScopeExistingRule_ShouldUpdateExistingDomainRule() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        V1alpha1WasmPluginSpec spec = new V1alpha1WasmPluginSpec();
        spec.setMatchRules(List.of(new MatchRule(false, Map.of("key", "original"), List.of("example.com"), List.of())));
        cr.setSpec(spec);

        WasmPluginInstance instance = WasmPluginInstance.builder()
                .scope(WasmPluginInstanceScope.DOMAIN)
                .target("example.com")
                .enabled(true)
                .configurations(Map.of("key", "updated"))
                .build();

        converter.setWasmPluginInstanceToCr(cr, instance);

        List<MatchRule> matchRules = cr.getSpec().getMatchRules();
        Assertions.assertNotNull(matchRules);
        Assertions.assertEquals(1, matchRules.size());
        MatchRule domainRule = matchRules.get(0);
        Assertions.assertTrue(domainRule.getDomain().contains("example.com"));
        Assertions.assertEquals(Map.of("key", "updated"), domainRule.getConfig());
        Assertions.assertFalse(domainRule.getConfigDisable());
    }


    @Test
    void setWasmPluginInstanceToCr_RouteScope_ShouldAddOrUpdateRouteRule() {
        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
        WasmPluginInstance instance = WasmPluginInstance.builder()
                .scope(WasmPluginInstanceScope.ROUTE)
                .target("route-1")
                .enabled(true)
                .configurations(Map.of("key", "value"))
                .build();

        converter.setWasmPluginInstanceToCr(cr, instance);

        V1alpha1WasmPluginSpec spec = cr.getSpec();
        Assertions.assertNotNull(spec);
        List<MatchRule> matchRules = spec.getMatchRules();
        Assertions.assertNotNull(matchRules);
        Assertions.assertEquals(1, matchRules.size());
        MatchRule routeRule = matchRules.get(0);
        Assertions.assertTrue(routeRule.getIngress().contains("route-1"));
        Assertions.assertEquals(Map.of("key", "value"), routeRule.getConfig());
        Assertions.assertFalse(routeRule.getConfigDisable());
    }

    //TODO
//    @Test
//    void setWasmPluginInstanceToCr_InvalidScope_ShouldThrowException() {
//        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
//        WasmPluginInstance instance = WasmPluginInstance.builder()
//                .scope(null)
//                .target("invalid")
//                .enabled(true)
//                .configurations(Map.of("key", "value"))
//                .build();
//
//        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
//            converter.setWasmPluginInstanceToCr(cr, instance);
//        });
//        Assertions.assertEquals("Unsupported scope: null", exception.getMessage());
//    }


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
        Assertions.assertEquals(1, cr.getSpec().getMatchRules().size());
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
        Assertions.assertEquals(1, cr.getSpec().getMatchRules().size());
    }

//    @Test
//    void removeWasmPluginInstanceFromCr_InvalidScope_ShouldThrowException() {
//        V1alpha1WasmPlugin cr = new V1alpha1WasmPlugin();
//        WasmPluginInstanceScope scope = WasmPluginInstanceScope.ROUTE;
//
//        Assertions.assertThrows(IllegalArgumentException.class,
//                () -> converter.removeWasmPluginInstanceFromCr(cr, scope, "test-route"));
//    }


    /*********
     *
     */

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
        Assertions.assertEquals(expectedRoute, route);
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
        Assertions.assertEquals(expectedRoute, route);
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
        Assertions.assertEquals(expectedRoute, route);
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
        Assertions.assertEquals(expectedRoute, route);
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
        Assertions.assertEquals(expectedRoute, route);
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
        Assertions.assertEquals(expectedRoute, route);
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
        Assertions.assertEquals(expectedRoute, route);
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

        Assertions.assertEquals(expectedIngress, ingress);
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

        Assertions.assertEquals(expectedIngress, ingress);
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

        Assertions.assertEquals(expectedIngress, ingress);
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

        Assertions.assertEquals(expectedIngress, ingress);
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

        Assertions.assertEquals(expectedIngress, ingress);
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

        Assertions.assertEquals(expectedIngress, ingress);
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

        Assertions.assertEquals(expectedIngress, ingress);
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
}
