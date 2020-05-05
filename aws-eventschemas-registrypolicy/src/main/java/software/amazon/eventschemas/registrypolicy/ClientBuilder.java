package software.amazon.eventschemas.registrypolicy;

import software.amazon.awssdk.services.schemas.SchemasClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {

    static SchemasClient getSchemasClient() {
        return SchemasClient.builder()
                .httpClient(LambdaWrapper.HTTP_CLIENT)
                .build();
    }
}
