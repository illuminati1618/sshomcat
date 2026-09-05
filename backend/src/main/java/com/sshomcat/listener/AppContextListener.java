package com.sshomcat.listener;

import com.sshomcat.AppServices;
import com.sshomcat.config.AppConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and validates config at startup (fail fast -- docs/roadmap.md M2, pulled into M1) and
 * starts the shared {@link org.apache.sshd.client.SshClient}. On shutdown, stops that client and
 * the session-timer executor so no MINA threads or connections survive a hot redeploy (they will
 * otherwise, and Tomcat will log a memory-leak warning on undeploy).
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            AppConfig config = AppConfig.load();
            AppServices.init(config);
            LOG.info(() -> "SSHomcat backend started. target=" + config.targetHost + ":" + config.targetPort
                    + " hostKeyVerification=" + config.hostKeyVerification);
        } catch (RuntimeException e) {
            // Fail fast and loud: an invalid/missing config should stop the webapp from coming
            // up at all, not fail confusingly on the first WebSocket connection.
            LOG.log(Level.SEVERE, "SSHomcat configuration error -- refusing to start: " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        AppServices.shutdown();
        LOG.info("SSHomcat backend stopped.");
    }
}
