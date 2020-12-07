package de.fraunhofer.isst.dataspaceconnector.controller;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.RdfResource;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import de.fraunhofer.iais.eis.util.Util;
import de.fraunhofer.isst.dataspaceconnector.exceptions.ConnectorConfigurationException;
import de.fraunhofer.isst.dataspaceconnector.services.resource.OfferedResourceService;
import de.fraunhofer.isst.dataspaceconnector.services.resource.RequestedResourceService;
import de.fraunhofer.isst.dataspaceconnector.services.usagecontrol.PolicyHandler;
import de.fraunhofer.isst.ids.framework.configuration.ConfigurationContainer;
import de.fraunhofer.isst.ids.framework.spring.starter.SerializerProvider;
import de.fraunhofer.isst.ids.framework.spring.starter.TokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

/**
 * This class provides endpoints for basic connector services.
 *
 * @version $Id: $Id
 */
@RestController
@Tag(name = "Connector: Selfservice", description = "Endpoints for connector information")
public class MainController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    private final TokenProvider tokenProvider;
    private final ConfigurationContainer configurationContainer;
    private final SerializerProvider serializerProvider;

    private final OfferedResourceService offeredResourceService;
    private final RequestedResourceService requestedResourceService;

    private final PolicyHandler policyHandler;

    /**
     * Constructor for MainController.
     *
     * @param policyHandler            a {@link PolicyHandler} object.
     * @param tokenProvider            a {@link TokenProvider} object.
     * @param configurationContainer   a {@link de.fraunhofer.isst.ids.framework.spring.starter.ConfigProducer}
     *                                 object.
     * @param serializerProvider       a {@link de.fraunhofer.isst.ids.framework.spring.starter.SerializerProvider}
     *                                 object.
     * @param offeredResourceService   a {@link OfferedResourceService} object.
     * @param requestedResourceService a {@link RequestedResourceService} object.
     * @throws IllegalArgumentException - if one of the parameters is null.
     */
    @Autowired
    public MainController(@NotNull TokenProvider tokenProvider,
        @NotNull ConfigurationContainer configurationContainer,
        @NotNull SerializerProvider serializerProvider,
        @NotNull OfferedResourceService offeredResourceService,
        @NotNull RequestedResourceService requestedResourceService,
        @NotNull PolicyHandler policyHandler) throws IllegalArgumentException {
        if (tokenProvider == null) {
            throw new IllegalArgumentException("The TokenProvider cannot be null");
        }

        if (configurationContainer == null) {
            throw new IllegalArgumentException("The ConfigurationContainer cannot be null");
        }

        if (serializerProvider == null) {
            throw new IllegalArgumentException("The SerializerProvider cannot be null");
        }

        if (offeredResourceService == null) {
            throw new IllegalArgumentException("The OfferedResourceService cannot be null");
        }

        if (requestedResourceService == null) {
            throw new IllegalArgumentException("The RequestedResourceService cannot be null");
        }

        if (policyHandler == null) {
            throw new IllegalArgumentException("The PolicyHandler cannot be null");
        }

        this.tokenProvider = tokenProvider;
        this.configurationContainer = configurationContainer;
        this.serializerProvider = serializerProvider;
        this.offeredResourceService = offeredResourceService;
        this.requestedResourceService = requestedResourceService;
        this.policyHandler = policyHandler;
    }

    /**
     * Gets connector self-description without catalog.
     *
     * @return Self-description or error response.
     */
    @Operation(summary = "Public Endpoint for Connector Self-description",
        description = "Get the connector's reduced self-description.")
    @RequestMapping(value = {"/", ""}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> getPublicSelfDescription() {
        Assert.notNull(configurationContainer, "The configurationContainer cannot be null.");
        Assert.notNull(serializerProvider, "The serializerProvider cannot be null.");

        try {
            // Modify a connector for exposing the reduced self description
            var connector = (BaseConnectorImpl) getConnector();
            connector.setResourceCatalog(null);
            connector.setPublicKey(null);

            return new ResponseEntity<>(serializerProvider.getSerializer().serialize(connector),
                HttpStatus.OK);
        } catch (ConnectorConfigurationException exception) {
            // No connector found
            LOGGER.warn("No connector has been configurated.", exception);
            return new ResponseEntity<>("No connector is currently available.",
                HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IOException exception) {
            // Could not serialize the connector.
            LOGGER.error("Could not serialize the connector.", exception);
            return new ResponseEntity<>("No connector is currently available.",
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Gets connector self-description.
     *
     * @return Self-description or error response.
     */
    @Operation(summary = "Connector Self-description",
        description = "Get the connector's self-description.")
    @RequestMapping(value = {"/admin/api/selfservice"}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> getSelfService() {
        try {
            // Modify a connector for exposing a resource catalog
            var connector = (BaseConnectorImpl) getConnector();
            connector.setResourceCatalog(Util.asList(buildResourceCatalog()));

            return new ResponseEntity<>(serializerProvider.getSerializer().serialize(connector),
                HttpStatus.OK);
        } catch (ConnectorConfigurationException exception) {
            // No connector found
            LOGGER.warn("No connector has been configurated.", exception);
            return new ResponseEntity<>("No connector is currently available.",
                HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IOException exception) {
            // Could not serialize the connector.
            LOGGER.error("Could not serialize the connector.", exception);
            return new ResponseEntity<>("No connector is currently available.",
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get the configuration of the connector.
     *
     * @return a {@link org.springframework.http.ResponseEntity} object.
     */
    @Operation(summary = "Get Connector configuration",
        description = "Get the connector's configuration.")
    @RequestMapping(value = "/admin/api/example/configuration", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> getConnectorConfiguration() {
        //NOTE: This needs some cleanup. Skip exception handling.
        var exceptions = new ArrayList<URI>();
        exceptions.add(URI.create("https://localhost:8080/"));
        exceptions.add(URI.create("http://localhost:8080/"));

        return new ResponseEntity<>(new ConfigurationModelBuilder()
            ._configurationModelLogLevel_(LogLevel.NO_LOGGING)
            ._connectorDeployMode_(ConnectorDeployMode.TEST_DEPLOYMENT)
            ._connectorProxy_(Util.asList(new ProxyBuilder()
                ._noProxy_(exceptions)
                ._proxyAuthentication_(new BasicAuthenticationBuilder().build())
                ._proxyURI_(URI.create("proxy.dortmund.isst.fraunhofer.de:3128"))
                .build()))
            ._connectorStatus_(ConnectorStatus.CONNECTOR_ONLINE)
            ._connectorDescription_(new BaseConnectorBuilder()
                ._maintainer_(URI.create("https://example.com"))
                ._curator_(URI.create("https://example.com"))
                ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
                ._outboundModelVersion_("4.0.0")
                ._inboundModelVersion_(Util.asList("4.0.0"))
                ._title_(Util.asList(new TypedLiteral("Dataspace Connector")))
                ._description_(Util.asList(new TypedLiteral(
                    "IDS Connector with static example resources hosted by the Fraunhofer ISST")))
                ._version_("v3.0.0")
                ._publicKey_(new PublicKeyBuilder()
                    ._keyType_(KeyType.RSA) //tokenProvider.providePublicKey().getAlgorithm() ?
                    ._keyValue_(tokenProvider.providePublicKey().getEncoded())
                    .build()
                )
                ._hasDefaultEndpoint_(new ConnectorEndpointBuilder()
                    ._accessURL_(URI.create("/api/ids/data"))
                    .build())
                .build())
            ._keyStore_(URI.create("file:///conf/keystore.p12"))
            ._trustStore_(URI.create("file:///conf/truststore.p12"))
            .build().toRdf(), HttpStatus.OK);
    }

    /**
     * Get the policy pattern.
     *
     * @param policy a {@link java.lang.String} object.
     * @return a {@link org.springframework.http.ResponseEntity} object.
     */
    @Operation(summary = "Get pattern of policy",
        description = "Get the policy pattern represented by a given JSON string.")
    @RequestMapping(value = "/admin/api/example/policy-pattern", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> getPolicyPattern(
        @Parameter(description = "The JSON string representing a policy", required = true)
        @RequestParam("policy") String policy) {
        Assert.notNull(policyHandler, "The policyHandler cannot be null.");
        try {
            // Return the policy pattern
            return new ResponseEntity<>(policyHandler.getPattern(policy), HttpStatus.OK);
        } catch (IOException exception) {
            // Failed to receive the pattern. Inform the requester.
            LOGGER.error("Failed to receive policy.", exception);
            return new ResponseEntity<>("The policy is currently not available.",
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get an example policy pattern.
     *
     * @param pattern a {@link PolicyHandler.Pattern} object.
     * @return a {@link org.springframework.http.ResponseEntity} object.
     */
    @Operation(summary = "Get example policy",
        description = "Get an example policy for a given policy pattern.")
    @RequestMapping(value = "/admin/api/example/usage-policy", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> getExampleUsagePolicy(
        @Parameter(description = "The policy pattern.", required = true)
        @RequestParam("pattern") PolicyHandler.Pattern pattern) {
        ContractOffer contractOffer = null;

        switch (pattern) {
            case PROVIDE_ACCESS:
                contractOffer = new ContractOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("provide-access")))
                        ._action_(Util.asList(Action.USE))
                        .build()))
                    .build();
                break;
            case PROHIBIT_ACCESS:
                contractOffer = new ContractOfferBuilder()
                    ._prohibition_(Util.asList(new ProhibitionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("prohibit-access")))
                        ._action_(Util.asList(Action.USE))
                        .build()))
                    .build();
                break;
            case N_TIMES_USAGE:
                contractOffer = new NotMoreThanNOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("n-times-usage")))
                        ._action_(Util.asList(Action.USE))
                        ._constraint_(Util.asList(new ConstraintBuilder()
                            ._leftOperand_(LeftOperand.COUNT)
                            ._operator_(BinaryOperator.LTEQ)
                            ._rightOperand_(new RdfResource("5", URI.create("xsd:double")))
                            ._pipEndpoint_(
                                URI.create("https://localhost:8080/admin/api/resources/"))
                            .build()))
                        .build()))
                    .build();
                break;
            case DURATION_USAGE:
                contractOffer = new ContractOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("duration-usage")))
                        ._action_(Util.asList(Action.USE))
                        ._constraint_(Util.asList(new ConstraintBuilder()
                            ._leftOperand_(LeftOperand.ELAPSED_TIME)
                            ._operator_(BinaryOperator.SHORTER_EQ)
                            ._rightOperand_(new RdfResource("PT4H", URI.create("xsd:duration")))
                            .build()))
                        .build()))
                    .build();
                break;
            case USAGE_DURING_INTERVAL:
                contractOffer = new ContractOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("usage-during-interval")))
                        ._action_(Util.asList(Action.USE))
                        ._constraint_(Util.asList(new ConstraintBuilder()
                            ._leftOperand_(LeftOperand.POLICY_EVALUATION_TIME)
                            ._operator_(BinaryOperator.AFTER)
                            ._rightOperand_(new RdfResource("2020-07-11T00:00:00Z",
                                URI.create("xsd:dateTimeStamp")))
                            .build(), new ConstraintBuilder()
                            ._leftOperand_(LeftOperand.POLICY_EVALUATION_TIME)
                            ._operator_(BinaryOperator.BEFORE)
                            ._rightOperand_(new RdfResource("2020-07-11T00:00:00Z",
                                URI.create("xsd:dateTimeStamp")))
                            .build()))
                        .build()))
                    .build();
                break;
            case USAGE_UNTIL_DELETION:
                contractOffer = new ContractOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("usage-until-deletion")))
                        ._action_(Util.asList(Action.USE))
                        ._constraint_(Util.asList(new ConstraintBuilder()
                            ._leftOperand_(LeftOperand.POLICY_EVALUATION_TIME)
                            ._operator_(BinaryOperator.AFTER)
                            ._rightOperand_(new RdfResource("2020-07-11T00:00:00Z",
                                URI.create("xsd:dateTimeStamp")))
                            .build(), new ConstraintBuilder()
                            ._leftOperand_(LeftOperand.POLICY_EVALUATION_TIME)
                            ._operator_(BinaryOperator.BEFORE)
                            ._rightOperand_(new RdfResource("2020-07-11T00:00:00Z",
                                URI.create("xsd:dateTimeStamp")))
                            .build()))
                        ._postDuty_(Util.asList(new DutyBuilder()
                            ._action_(Util.asList(Action.DELETE))
                            ._constraint_(Util.asList(new ConstraintBuilder()
                                ._leftOperand_(LeftOperand.POLICY_EVALUATION_TIME)
                                ._operator_(BinaryOperator.TEMPORAL_EQUALS)
                                ._rightOperand_(new RdfResource("2020-07-11T00:00:00Z",
                                    URI.create("xsd:dateTimeStamp")))
                                .build()))
                            .build()))
                        .build()))
                    .build();
                break;
            case USAGE_LOGGING:
                contractOffer = new ContractOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("usage-logging")))
                        ._action_(Util.asList(Action.USE))
                        ._postDuty_(Util.asList(new DutyBuilder()
                            ._action_(Util.asList(Action.LOG))
                            .build()))
                        .build()))
                    .build();
                break;
            case USAGE_NOTIFICATION:
                contractOffer = new ContractOfferBuilder()
                    ._permission_(Util.asList(new PermissionBuilder()
                        ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                        ._description_(Util.asList(new TypedLiteral("usage-notification")))
                        ._action_(Util.asList(Action.USE))
                        ._postDuty_(Util.asList(new DutyBuilder()
                            ._action_(Util.asList(Action.NOTIFY))
                            ._constraint_(Util.asList(new ConstraintBuilder()
                                ._leftOperand_(LeftOperand.ENDPOINT)
                                ._operator_(BinaryOperator.DEFINES_AS)
                                ._rightOperand_(
                                    new RdfResource("https://localhost:8000/api/ids/data",
                                        URI.create("xsd:anyURI")))
                                .build()))
                            .build()))
                        .build()))
                    .build();
                break;
        }

        return new ResponseEntity<>(contractOffer.toRdf(), HttpStatus.OK);
    }

    private Connector getConnector() throws ConnectorConfigurationException {
        Assert.notNull(configurationContainer, "The config cannot be null.");

        final var connector = configurationContainer.getConnector();
        if (connector == null) {
            // The connector is needed for every answer and cannot be null
            throw new ConnectorConfigurationException("No connector configurated.");
        }

        return connector;
    }

    private ResourceCatalog buildResourceCatalog() throws ConstraintViolationException {
        return new ResourceCatalogBuilder()
            ._offeredResource_(new ArrayList<>(offeredResourceService.getResourceList()))
            ._requestedResource_(new ArrayList<>(requestedResourceService.getRequestedResources()))
            .build();
    }
}
