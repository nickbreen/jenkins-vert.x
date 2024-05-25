package kiwi.breen.jenkins;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import org.slf4j.Logger;

import java.net.URI;

import static org.slf4j.LoggerFactory.getLogger;

public class Main
{
    private static final Logger logger = getLogger(Main.class);

    public static void main(final String[] args)
    {
        final Vertx vertx = Vertx.vertx();
        Runtime.getRuntime().addShutdownHook(new Thread(vertx::close));

        Future.all(
                vertx.deployVerticle(new JenkinsGateway(
                        URI.create(System.getenv("JENKINS_URL")),
                        new HttpClientOptions().setSsl(true).setTrustAll(true))),
                vertx.deployVerticle(new JenkinsJobPoller(3000L)),
                vertx.deployVerticle(new JenkinsTestResultFetcher()))
                .onFailure(event -> {
                    logger.error("Failed to deploy verticles: {}", event.getMessage());
                    vertx.close();
                });
    }
}
