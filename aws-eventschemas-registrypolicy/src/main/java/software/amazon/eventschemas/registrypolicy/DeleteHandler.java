package software.amazon.eventschemas.registrypolicy;

import lombok.extern.java.Log;
import software.amazon.awssdk.services.schemas.SchemasClient;
import software.amazon.awssdk.services.schemas.model.DeleteResourcePolicyRequest;
import software.amazon.awssdk.services.schemas.model.GetResourcePolicyRequest;
import software.amazon.awssdk.services.schemas.model.NotFoundException;
import software.amazon.awssdk.services.schemas.model.SchemasException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static software.amazon.eventschemas.registrypolicy.ResourceModel.TYPE_NAME;

public class DeleteHandler extends BaseHandler<CallbackContext> {

    private static final int CALLBACK_DELAY_SECONDS = 30;
    private static final int NUMBER_OF_CREATE_POLL_RETRIES = 3;

    private final SchemasClient schemasClient = ClientBuilder.getSchemasClient();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final CallbackContext context = callbackContext == null ? CallbackContext.builder()
                .registryPolicyDeleted(false)
                .registryPolicyStabilized(false)
                .stabilizationRetriesRemaining(NUMBER_OF_CREATE_POLL_RETRIES)
                .build() : callbackContext;

        final ResourceModel resourceModel = request.getDesiredResourceState();
        String registryName = resourceModel.getId();

        if (!context.isRegistryPolicyDeleted()) {
            deletePolicy(registryName, proxy);
            context.setRegistryPolicyDeleted(true);
            logger.log(String.format("%s [%s] deleted successfully",
                    ResourceModel.TYPE_NAME, registryName));
        }

        if (!context.isRegistryPolicyStabilized()) {
            boolean stabilized = isRegistryPolicyStabilized(registryName, proxy);
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

    private boolean isRegistryPolicyStabilized(String registryName, AmazonWebServicesClientProxy proxy) {
        GetResourcePolicyRequest getResourcePolicyRequest = GetResourcePolicyRequest.builder().registryName(registryName).build();
        try {
            proxy.injectCredentialsAndInvokeV2(getResourcePolicyRequest, schemasClient::getResourcePolicy);
            return false;
        } catch (NotFoundException e) {
            return true;
        } catch (SchemasException e) {
            throw new CfnGeneralServiceException("DeletePolicy", e);
        }
    }

    private void deletePolicy(String registryName, AmazonWebServicesClientProxy proxy) {
        try {
            DeleteResourcePolicyRequest deleteResourcePolicyRequest = DeleteResourcePolicyRequest.builder()
                    .registryName(registryName)
                    .build();
            proxy.injectCredentialsAndInvokeV2(deleteResourcePolicyRequest, schemasClient::deleteResourcePolicy);
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(TYPE_NAME, registryName, e);
        } catch (SchemasException e) {
            throw new CfnGeneralServiceException("DeletePolicy", e);
        }
    }
}
