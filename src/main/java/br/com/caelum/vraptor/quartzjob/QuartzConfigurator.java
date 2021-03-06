package br.com.caelum.vraptor.quartzjob;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import br.com.caelum.vraptor.events.VRaptorInitialized;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.caelum.vraptor.environment.Environment;

@ApplicationScoped
public class QuartzConfigurator {

	private static final int TEN_SECONDS = 10000;
	private Scheduler scheduler;
	private boolean initialized;

	private final static Logger logger = LoggerFactory.getLogger(QuartzConfigurator.class);
	private Environment env;

	@Deprecated // CDI eyes only
	public QuartzConfigurator() {}

	@Inject
	public QuartzConfigurator(Environment env) throws SchedulerException {
		this.env = env;
		scheduler = StdSchedulerFactory.getDefaultScheduler();
	}

	public void initialize(@Observes VRaptorInitialized event) {
		try {
			boolean notProduction = !env.getName().equals("production");
			boolean force = Boolean.parseBoolean(env.get("force.quartz.jobs", "false"));

			if (notProduction && !force) return;

			logger.info("Quartz configurator initializing...");

			String url = (env.get("host") + "/jobs/configure").replace("https", "http");

			Runnable quartzMe = new StartQuartz(url);
			new Thread(quartzMe).start();

		} catch (Exception e) {
			logger.error("could not schedule job", e);
			throw new RuntimeException(e);
		}
	}

	class StartQuartz implements Runnable {
		private static final int TWO_MINUTES = 2*60*1000;
		private final String url;

		public StartQuartz(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			try {
				HttpClient http = new HttpClient();
				waitTenSeconds(http);
				logger.info("Invoking quartz configurator at " + url);
				http.executeMethod(new GetMethod(url));
			} catch (Exception e) {
				logger.error("Could not configure quartz!", e);
			}
		}

		public void waitTenSeconds(HttpClient http) throws InterruptedException  {
			Thread.sleep(TEN_SECONDS);
		}
	}

	public void add(JobDetail job, Trigger trigger) throws SchedulerException {
		scheduler.scheduleJob(job, trigger);
	}

	public void start() throws SchedulerException {
		scheduler.start();
		initialized = true;
	}

	public boolean isInitialized() {
		return initialized;
	}

	@PreDestroy
	public void destroy() throws SchedulerException {
		scheduler.shutdown();
	}
}
