package se.thinkware.gocd.dockerpoller;

import com.thoughtworks.go.plugin.api.AbstractGoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import se.thinkware.gocd.dockerpoller.message.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse.success;
import static se.thinkware.gocd.dockerpoller.JsonUtil.fromJsonString;
import static se.thinkware.gocd.dockerpoller.JsonUtil.toJsonString;

@Extension
public class PackageRepositoryMaterial extends AbstractGoPlugin {

    public static final String EXTENSION = "package-repository";
    public static final String REQUEST_REPOSITORY_CONFIGURATION = "repository-configuration";
    public static final String REQUEST_PACKAGE_CONFIGURATION = "package-configuration";
    public static final String REQUEST_VALIDATE_REPOSITORY_CONFIGURATION = "validate-repository-configuration";
    public static final String REQUEST_VALIDATE_PACKAGE_CONFIGURATION = "validate-package-configuration";
    public static final String REQUEST_CHECK_REPOSITORY_CONNECTION = "check-repository-connection";
    public static final String REQUEST_CHECK_PACKAGE_CONNECTION = "check-package-connection";
    public static final String REQUEST_LATEST_PACKAGE_REVISION = "latest-revision";
    public static final String REQUEST_LATEST_PACKAGE_REVISION_SINCE = "latest-revision-since";

    private final Map<String, MessageHandler> handlerMap = new LinkedHashMap<>();
    private final PackageRepositoryConfigurationProvider configurationProvider;
    private final PackageRepositoryPoller packageRepositoryPoller;

    public PackageRepositoryMaterial() {
        configurationProvider = new PackageRepositoryConfigurationProvider();
        packageRepositoryPoller = new PackageRepositoryPoller(configurationProvider);
        handlerMap.put(REQUEST_REPOSITORY_CONFIGURATION, this::handleRepositoryConfigurationsMessage);
        handlerMap.put(REQUEST_PACKAGE_CONFIGURATION, this::handlePackageConfigurationMessage);
        handlerMap.put(REQUEST_VALIDATE_REPOSITORY_CONFIGURATION, this::handleValidateRepositoryConfigurationMessage);
        handlerMap.put(REQUEST_VALIDATE_PACKAGE_CONFIGURATION, this::handleValidatePackageConfigurationMessage);
        handlerMap.put(REQUEST_CHECK_REPOSITORY_CONNECTION, this::handleCheckRepositoryConnectionMessage);
        handlerMap.put(REQUEST_CHECK_PACKAGE_CONNECTION, this::handleCheckPackageConnectionMessage);
        handlerMap.put(REQUEST_LATEST_PACKAGE_REVISION, this::handleLatestRevisionMessage);
        handlerMap.put(REQUEST_LATEST_PACKAGE_REVISION_SINCE, this::handleLatestRevisionSinceMessage);
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        try {
            if (handlerMap.containsKey(goPluginApiRequest.requestName())) {
                return handlerMap.get(goPluginApiRequest.requestName()).handle(goPluginApiRequest);
            }
            return DefaultGoPluginApiResponse.badRequest(String.format("Invalid request name %s", goPluginApiRequest.requestName()));
        } catch (Exception e) {
            String message = e.getMessage();
            return DefaultGoPluginApiResponse.error(message == null ? String.format("Encountered error of type %s without message.", e.getClass()) : e.getMessage());
        }
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION, Collections.singletonList("1.0"));
    }

    GoPluginApiResponse handlePackageConfigurationMessage(GoPluginApiRequest request) {
        return success(toJsonString(configurationProvider.packageConfiguration().getPropertyMap()));
    }


    public GoPluginApiResponse handleRepositoryConfigurationsMessage(GoPluginApiRequest request) {
        return success(toJsonString(configurationProvider.repositoryConfiguration().getPropertyMap()));
    }


    public GoPluginApiResponse handleValidateRepositoryConfigurationMessage(GoPluginApiRequest request) {

        ValidateRepositoryConfigurationMessage message = fromJsonString(request.requestBody(), ValidateRepositoryConfigurationMessage.class);
        ValidationResultMessage validationResultMessage = configurationProvider.validateRepositoryConfiguration(message.getRepositoryConfiguration());
        if (validationResultMessage.failure()) {
            return success(toJsonString(validationResultMessage.getValidationErrors()));
        }
        return success("");
    }

    private GoPluginApiResponse handleValidatePackageConfigurationMessage(GoPluginApiRequest request) {
        ValidatePackageConfigurationMessage message = fromJsonString(request.requestBody(), ValidatePackageConfigurationMessage.class);
        ValidationResultMessage validationResultMessage = configurationProvider.validatePackageConfiguration(message.getPackageConfiguration());
        if (validationResultMessage.failure()) {
            return success(toJsonString(validationResultMessage.getValidationErrors()));
        }
        return success("");
    }

    public GoPluginApiResponse handleCheckRepositoryConnectionMessage(GoPluginApiRequest request) {
        RepositoryConnectionMessage message = fromJsonString(request.requestBody(), RepositoryConnectionMessage.class);
        CheckConnectionResultMessage result = packageRepositoryPoller.checkConnectionToRepository(message.getRepositoryConfiguration());
        return success(toJsonString(result));
    }

    private GoPluginApiResponse handleCheckPackageConnectionMessage(GoPluginApiRequest request) {
        PackageConnectionMessage message = fromJsonString(request.requestBody(), PackageConnectionMessage.class);
        CheckConnectionResultMessage result = packageRepositoryPoller.checkConnectionToPackage(message.getPackageConfiguration(), message.getRepositoryConfiguration());
        return success(toJsonString(result));
    }

    public GoPluginApiResponse handleLatestRevisionMessage(GoPluginApiRequest request) {
        LatestPackageRevisionMessage message = fromJsonString(request.requestBody(), LatestPackageRevisionMessage.class);
        PackageRevisionMessage revision = packageRepositoryPoller.getLatestRevision(message.getPackageConfiguration(), message.getRepositoryConfiguration());
        return success(toJsonString(revision));
    }

    public GoPluginApiResponse handleLatestRevisionSinceMessage(GoPluginApiRequest request) {
        LatestPackageRevisionSinceMessage message = fromJsonString(request.requestBody(), LatestPackageRevisionSinceMessage.class);
        PackageRevisionMessage revision = packageRepositoryPoller.getLatestRevisionSince(message.getPackageConfiguration(), message.getRepositoryConfiguration(), message.getPreviousRevision());
        return success(revision == null ? null : toJsonString(revision));
    }
}