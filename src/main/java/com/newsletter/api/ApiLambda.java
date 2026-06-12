package com.newsletter.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.newsletter.common.AwsClientFactory;
import com.newsletter.common.DynamoDbRepository;
import java.util.Map;

public class ApiLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ApiRouter router;

    public ApiLambda() {
        var dynamo = AwsClientFactory.dynamoDb();
        DynamoDbRepository repository = new DynamoDbRepository(dynamo,
            System.getenv("ARTICLES_TABLE"),
            System.getenv("DIGESTS_TABLE"),
            System.getenv("FEEDBACK_TABLE"));
        this.router = new ApiRouter(repository);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String path = event.getPath();
        String method = event.getHttpMethod();

        ApiRouter.Response response;

        if ("OPTIONS".equals(method)) {
            response = new ApiRouter.Response(200, "");
        } else if ("GET".equals(method)) {
            Map<String, String> params = event.getQueryStringParameters() != null
                ? event.getQueryStringParameters() : Map.of();
            response = router.handleGet(path, params);
        } else if ("POST".equals(method)) {
            response = router.handlePost(path, event.getBody());
        } else {
            response = new ApiRouter.Response(405, "{\"error\": \"Method not allowed\"}");
        }

        APIGatewayProxyResponseEvent apiResponse = new APIGatewayProxyResponseEvent();
        apiResponse.setStatusCode(response.statusCode());
        apiResponse.setBody(response.body());
        apiResponse.setHeaders(response.headers());
        return apiResponse;
    }
}
