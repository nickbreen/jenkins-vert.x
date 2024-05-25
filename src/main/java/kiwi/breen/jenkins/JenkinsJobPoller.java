package kiwi.breen.jenkins;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.IntStream;

public class JenkinsJobPoller extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(JenkinsJobPoller.class);
    private static final String query = "/api/json?tree=jobs[url,name,lastBuild[url,actions[totalCount,skipCount,failCount,urlName]]]";

    public static final String ADDRESS_JENKINS_JOB = "jenkins.job";

    private final long interval;
    private long timerId;

    public JenkinsJobPoller(final long interval)
    {
        this.interval = interval;
    }

    @Override
    public void start()
    {
        logger.info("started {}", deploymentID());
        timerId = vertx.setPeriodic(interval, this::fetchJobs);
    }

    private void fetchJobs(final long timerId)
    {
        logger.debug("Fetching jobs {}", timerId);
        vertx.eventBus()
                .request(JenkinsGateway.ADDRESS_JENKINS_QUERY, query)
                .map(Message::body)
                .map(JsonObject.class::cast)
                .onSuccess(this::onJobs)
                .onFailure(this::onFailure);
    }

    private void onJobs(final JsonObject query)
    {
        logger.trace("Jobs: {}", query.encodePrettily());
        final JsonArray jobs = query.getJsonArray("jobs", new JsonArray());
        IntStream.range(0, jobs.size())
                .mapToObj(jobs::getJsonObject)
                .forEach(this::onJob);
    }

    private void onJob(final JsonObject job)
    {
        vertx.eventBus().publish(ADDRESS_JENKINS_JOB, job);
    }

    private void onFailure(final Throwable reason)
    {
        logger.error("Request failed", reason);
    }

    @Override
    public void stop()
    {
        logger.info("stopped {}", deploymentID());
        vertx.cancelTimer(timerId);
    }
}
