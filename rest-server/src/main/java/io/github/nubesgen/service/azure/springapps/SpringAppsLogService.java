package io.github.nubesgen.service.azure.springapps;

import com.azure.resourcemanager.appplatform.models.DeploymentInstance;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.appplatform.models.SpringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Providing operations for get app build source and deploy logs.
 */
@Service
public class SpringAppsLogService {

    private static final Logger log = LoggerFactory.getLogger(SpringAppsLogService.class);

    /**
     * Get log by url string.
     *
     * @param strUrl the log url string
     * @param basicAuth the basicAuth
     * @return the log string
     */
    public String getLogByUrl(String strUrl, String basicAuth) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(strUrl);
            connection = (HttpsURLConnection) url.openConnection();
            if (basicAuth != null) {
                connection.setRequestProperty("Authorization", basicAuth);
            }
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            try (InputStream inputStream = connection.getInputStream()) {
                return new String(inputStream.readAllBytes());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to close connection.", e);
            }
        }
    }

    /**
     * Get the application log streaming endpoint.
     *
     * @param springService the spring service
     * @param appName the app name
     */
    public String getLogStreamingEndpoint(SpringService springService, String appName) {
        return Optional.ofNullable(springService.apps().getByName(appName)).map(SpringApp::activeDeploymentName).map(d -> {
            final String endpoint = springService.apps().getByName(appName).parent().listTestKeys().primaryTestEndpoint();
            List<DeploymentInstance> deploymentInstances =
                    springService.apps().getByName(appName).getActiveDeployment().instances();
            deploymentInstances =
                    deploymentInstances.stream().sorted(Comparator.comparing(DeploymentInstance::startTime)).collect(Collectors.toList());
            String instanceName = deploymentInstances.get(deploymentInstances.size() - 1).name();
            return String.format("%s/api/logstream/apps/%s/instances/%s", endpoint.replace(".test", ""), appName,
                    instanceName);
        }).orElse(null);
    }

}
