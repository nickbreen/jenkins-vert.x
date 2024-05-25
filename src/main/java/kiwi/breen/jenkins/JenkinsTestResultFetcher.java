package kiwi.breen.jenkins;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.slf4j.LoggerFactory.getLogger;

public class JenkinsTestResultFetcher extends AbstractVerticle
{
    private static final Logger logger = getLogger(JenkinsTestResultFetcher.class);
    public static final String ADDRESS_JENKINS_TEST_RESULTS = "jenkins.test.results";

    @Override
    public void start()
    {
        logger.info("started {}", deploymentID());
        vertx.eventBus().consumer(JenkinsJobPoller.ADDRESS_JENKINS_JOB, this::onJob);
    }

    @Override
    public void stop()
    {
        logger.info("stopped {}", deploymentID());
    }

    private void onJob(Message<JsonObject> message)
    {
        Optional.ofNullable(message.body())
                .map(job -> job.getJsonObject("lastBuild"))
                .flatMap(this::buildTestResultsUrlIfHasFailedTests)
                .ifPresent(this::fetchTestResults);
    }

    private Optional<String> buildTestResultsUrlIfHasFailedTests(final JsonObject lastBuild)
    {
        final JsonArray actions = lastBuild.getJsonArray("actions", new JsonArray());
        return IntStream.range(0, actions.size())
                .mapToObj(actions::getJsonObject)
                .filter(action -> action.getInteger("failCount", -1) > 0)
                .map(action -> lastBuild.getString("url") + action.getString("urlName"))
                .findFirst();
    }

    private void fetchTestResults(final String testResultsUrl)
    {
        vertx.eventBus().request(
                        JenkinsGateway.ADDRESS_JENKINS_QUERY,
                        testResultsUrl + "/api/json?tree=suites[cases[className,name,status]]")
                .map(Message::body)
                .map(JsonObject.class::cast)
                .map(testResults -> testResults.put("url", testResultsUrl))
                .onSuccess(this::onTestResults)
                .onFailure(this::onFailure);
    }

    private void onTestResults(final JsonObject testResults)
    {
        vertx.eventBus().publish(ADDRESS_JENKINS_TEST_RESULTS, testResults);
    }

    private void onFailure(final Throwable throwable)
    {
        logger.error("Failed to fetch test results", throwable);
    }
}
