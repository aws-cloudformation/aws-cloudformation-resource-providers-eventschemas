package software.amazon.eventschemas.registrypolicy;

import software.amazon.awssdk.services.schemas.model.DescribeRegistryRequest;
import software.amazon.awssdk.services.schemas.model.DescribeRegistryResponse;
import software.amazon.awssdk.services.schemas.model.GetResourcePolicyRequest;
import software.amazon.awssdk.services.schemas.model.GetResourcePolicyResponse;
import software.amazon.awssdk.services.schemas.model.NotFoundException;
import software.amazon.awssdk.services.schemas.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.schemas.model.PutResourcePolicyResponse;
import software.amazon.awssdk.services.schemas.model.SchemasException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import software.amazon.cloudformation.exceptions.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;


@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void testSuccessState() {
        //GIVEN
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder()
                .registryName("test-registry")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        PutResourcePolicyResponse putResourcePolicyResponse = PutResourcePolicyResponse.builder()
                .policy("")
                .revisionId("1")
                .build();
        GetResourcePolicyResponse getResourcePolicyResponse = GetResourcePolicyResponse.builder()
                .policy("")
                .revisionId("1")
                .build();

        // Mock
        doReturn(DescribeRegistryResponse.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeRegistryRequest.class), any());
        doThrow(NotFoundException.builder().build()).doReturn(getResourcePolicyResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetResourcePolicyRequest.class), any());
        doReturn(putResourcePolicyResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(PutResourcePolicyRequest.class), any());

        //WHEN
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, null, logger);

        //THEN
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testInProgressState() {
        //GIVEN
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder()
                .registryName("test-registry")
                .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        PutResourcePolicyResponse putResourcePolicyResponse = PutResourcePolicyResponse.builder()
                .revisionId("2")
                .build();
        final CallbackContext outputContext = CallbackContext.builder()
                .registryPolicyCreated(true)
                .registryPolicyStabilized(false)
                .stabilizationRetriesRemaining(2)
                .build();

        // Mock
        doReturn(DescribeRegistryResponse.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeRegistryRequest.class), any());
        doThrow(NotFoundException.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetResourcePolicyRequest.class), any());
        doReturn(putResourcePolicyResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(PutResourcePolicyRequest.class), any());

        //WHEN
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, null, logger);

        //THEN
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualTo(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(30);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }


    @Test
    public void testRegistryNotFoundException() {
        //GIVEN
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder()
                .registryName("test-registry")
                .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        // Mock
        doThrow(NotFoundException.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeRegistryRequest.class), any());

        //WHEN
        assertThrows(CfnNotFoundException.class, () ->
                handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testCfnAlreadyExistsException() {
        //GIVEN
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder()
                .registryName("test-registry")
                .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        // Mock
        doReturn(DescribeRegistryResponse.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeRegistryRequest.class), any());

        doReturn(GetResourcePolicyResponse.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetResourcePolicyRequest.class), any());

        //WHEN
        assertThrows(CfnAlreadyExistsException.class, () ->
                handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testSchemasException() {
        //GIVEN
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder()
                .registryName("test-registry")
                .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        // Mock
        doThrow(NotFoundException.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetResourcePolicyRequest.class), any());
        doReturn(DescribeRegistryResponse.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeRegistryRequest.class), any());
        doThrow(SchemasException.builder().build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(PutResourcePolicyRequest.class), any());

        //WHEN
        assertThrows(CfnGeneralServiceException.class, () ->
                handler.handleRequest(proxy, request, null, logger));
    }

}
