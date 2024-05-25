package kiwi.breen.jenkins;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class JenkinsGateway extends AbstractVerticle
{
    public static final String ADDRESS_JENKINS_QUERY = "jenkins.requests";

    private static final Logger logger = getLogger(JenkinsGateway.class);

    private final HttpClientOptions clientOptions;
    private final RequestOptions requestOptions;
    private final URI baseUri;

    private HttpClient httpClient;

    public JenkinsGateway(final URI baseUri, final HttpClientOptions clientOptions)
    {
        this.baseUri = baseUri;
        this.clientOptions = clientOptions;
        Optional.ofNullable(baseUri.getHost()).ifPresent(clientOptions::setDefaultHost);
        Optional.of(baseUri.getPort()).filter(p -> -1 != p).ifPresentOrElse(
                clientOptions::setDefaultPort,
                () -> clientOptions.setDefaultPort(switch (baseUri.getScheme())
                {
                    case "http" -> 80;
                    case "https" -> 443;
                    default -> -1;
                }));
        this.requestOptions = new RequestOptions()
                .addHeader("Accept", "application/json")
                .setMethod(HttpMethod.GET);
    }

    private void onFailure(final Throwable reason)
    {
        logger.error("Fetch jobs failure", reason);
    }

    private void onRequest(final Message<String> query)
    {
        final URI uri = URI.create(query.body());
        final URI relativeUri = this.baseUri.relativize(uri);

        if (relativeUri.isAbsolute())
        {
            query.fail(1000, "URI must be relative to the base URI");
        }

        final RequestOptions options = new RequestOptions(this.requestOptions).setURI(uri.toString());

        logger.debug("Fetching {}", options.toJson().encodePrettily());

        httpClient.request(options)
                .compose(HttpClientRequest::send)
                .compose(HttpClientResponse::body)
                .map(Buffer::toJsonObject)
                .onSuccess(query::reply)
                .onFailure(this::onFailure);
    }

    @Override
    public void start()
    {
        logger.info("started {}", deploymentID());
        httpClient = vertx.createHttpClient(clientOptions);
        vertx.eventBus().consumer(ADDRESS_JENKINS_QUERY, this::onRequest);
    }

    @Override
    public void stop()
    {
        logger.info("stopped {}", deploymentID());
        httpClient.close();
    }
}
