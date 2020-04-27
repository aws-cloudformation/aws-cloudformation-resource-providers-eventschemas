package software.amazon.eventschemas.registrypolicy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.schemas.model.ConflictException;
import software.amazon.awssdk.services.schemas.model.GetResourcePolicyRequest;
import software.amazon.awssdk.services.schemas.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.schemas.model.PutResourcePolicyResponse;
import software.amazon.awssdk.services.schemas.model.SchemasException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.awssdk.services.schemas.SchemasClient;
import software.amazon.awssdk.services.schemas.model.NotFoundException;

import static software.amazon.eventschemas.registrypolicy.ResourceModel.TYPE_NAME;

public class UpdateHandler extends BaseHandler<CallbackContext> {

    private static final int CALLBACK_DELAY_SECONDS = 30;
    private static final int NUMBER_OF_CREATE_POLL_RETRIES = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchemasClient schemasClient = ClientBuilder.getSchemasClient();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final CallbackContext context = callbackContext == null ? CallbackContext.builder()
                .registryPolicyUpdated(false)
                .registryPolicyStabilized(false)
                .stabilizationRetriesRemaining(NUMBER_OF_CREATE_POLL_RETRIES)
                .build() : callbackContext;

        final ResourceModel resourceModel = request.getDesiredResourceState();
        final String registryName = resourceModel.getRegistryName();

        if (resourceModel.getId() == null) {
            resourceModel.setId(registryName);
        }

        if (!context.isRegistryPolicyUpdated()) {
            String revisionId = getCurrentRevisionId(registryName, proxy);
            PutResourcePolicyResponse putResourcePolicyResponse = updatePolicy(registryName, revisionId, resourceModel.getPolicy(), proxy);

            context.setRegistryPolicyUpdated(true);
            resourceModel.setRevisionId(putResourcePolicyResponse.revisionId());

            logger.log(String.format("%s [%s] updated successfully",
                    ResourceModel.TYPE_NAME, registryName));
        }

        if (!context.isRegistryPolicyStabilized()) {
            boolean stabilized = isRegistryPolicyStabilized(registryName, resourceModel.getRevisionId(), proxy);
            if (!stabilized) {
                context.decrementStabilizationRetriesRemaining();
            }
            context.setRegistryPolicyStabilized(stabilized);
        }

        if (!context.isRegistryPolicyStabilized()) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(context)
                    .status(OperationStatus.IN_PROGRESS)
                    .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                    .resourceModel(resourceModel)
                    .build();
        }

        return ProgressEvent.defaultSuccessHandler(resourceModel);
    }

    private boolean isRegistryPolicyStabilized(String registryName, String revisionId, AmazonWebServicesClientProxy proxy) {
        try {
            GetResourcePolicyRequest getResourcePolicyRequest = GetResourcePolicyRequest.builder().registryName(registryName).build();
            String revisionReturned = proxy.injectCredentialsAndInvokeV2(getResourcePolicyRequest, schemasClient::getResourcePolicy).revisionId();
            return revisionReturned.equals(revisionId);
        } catch (NotFoundException e) {
            return false;
        } catch (SchemasException e) {
            throw new CfnGeneralServiceException("UpdateRegistryPolicy", e);
        }
    }

    private String getCurrentRevisionId(String registryName, AmazonWebServicesClientProxy proxy) {
        try {
            GetResourcePolicyRequest getResourcePolicyRequest = GetResourcePolicyRequest.builder().registryName(registryName).build();
            return proxy.injectCredentialsAndInvokeV2(getResourcePolicyRequest, schemasClient::getResourcePolicy).revisionId();
        } catch (NotFoundException e) {
            // Either Registry or Policy does not exist
            throw new CfnNotFoundException(TYPE_NAME, registryName, e);
        } catch (SchemasException e) {
            throw new CfnGeneralServiceException("UpdateRegistryPolicy", e);
        }
    }

    private PutResourcePolicyResponse updatePolicy(String registryName, String revisionId, Object policyObject, AmazonWebServicesClientProxy proxy) {
        try {
            String policy = MAPPER.writeValueAsString(policyObject);
            PutResourcePolicyRequest putResourcePolicyRequest = PutResourcePolicyRequest.builder().registryName(registryName).policy(policy).revisionId(revisionId).build();
            return proxy.injectCredentialsAndInvokeV2(putResourcePolicyRequest, schemasClient::putResourcePolicy);
        } catch (ConflictException e) {
            throw new CfnResourceConflictException(TYPE_NAME, registryName, e.getMessage());
        } catch (JsonProcessingException e) {
            throw new CfnInvalidRequestException(e);
        } catch (SchemasException e) {
            throw new CfnGeneralServiceException("UpdateRegistryPolicy", e);
        }
    }

}
