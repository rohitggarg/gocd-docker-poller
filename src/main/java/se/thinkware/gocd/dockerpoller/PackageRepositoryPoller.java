package se.thinkware.gocd.dockerpoller;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.thoughtworks.go.plugin.api.logging.Logger;
import se.thinkware.gocd.dockerpoller.message.CheckConnectionResultMessage;
import se.thinkware.gocd.dockerpoller.message.PackageMaterialProperties;
import se.thinkware.gocd.dockerpoller.message.PackageRevisionMessage;
import se.thinkware.gocd.dockerpoller.message.ValidationResultMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static se.thinkware.gocd.dockerpoller.JsonUtil.fromJsonString;

class PackageRepositoryPoller {

    private static final Logger logger = Logger.getLoggerFor(PackageRepositoryPoller.class);

    private final PackageRepositoryConfigurationProvider configurationProvider;

    private final HttpTransport transport;

    public PackageRepositoryPoller(PackageRepositoryConfigurationProvider configurationProvider) {
        logger.debug("Instantiated PackageRepositoryPoller");
        this.configurationProvider = configurationProvider;
        this.transport = new NetHttpTransport();
    }

    // This is used for testing, so that we can mock the HttpTransport
    public PackageRepositoryPoller(
            PackageRepositoryConfigurationProvider configurationProvider,
            HttpTransport transport
    ) {
        this.configurationProvider = configurationProvider;
        this.transport = transport;
    }
    
    private HttpResponse getUrl(GenericUrl url) throws IOException {
        HttpRequest request = transport.createRequestFactory().buildGetRequest(url);
        request.setThrowExceptionOnExecuteError(false);
        HttpResponse response = request.execute();

        logger.debug(String.format("HTTP GET URL: %s %s", url.toString(), response.getStatusCode()));
        if (response.isSuccessStatusCode()) {
            return response;
        } 

        if (response.getStatusCode() == 401) {
            String authenticate = response.getHeaders().getAuthenticate();
            logger.debug(String.format("WWW-Authenticate: %s", authenticate));
            if (authenticate != null) {
                Matcher matcher = Pattern
                     .compile("realm=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                     .matcher(authenticate);
                
                matcher.find();                
                String tokenUrl = matcher.group(1);
                logger.debug(String.format("Token URL: %s", tokenUrl));

                String tokenResponse = transport
                    .createRequestFactory()
                    .buildGetRequest(new GenericUrl(tokenUrl))
                    .execute()
                    .parseAsString();

                Map<String, String> tokenMap = new GsonBuilder().create().fromJson(
                    tokenResponse, 
                    new TypeToken<Map<String, String>>(){}.getType()
                );

                request = transport.createRequestFactory(req -> 
                    req.getHeaders().setAuthorization(
                        "Bearer " + tokenMap.get("token")
                    )
                ).buildGetRequest(url);

                return request.execute();
            }
        }
        
    	throw new HttpResponseException(response);
    }

    private CheckConnectionResultMessage checkUrl(GenericUrl url, String what) {
        logger.debug(String.format("Checking URL: %s", url.toString()));
        try {
            HttpResponse response = getUrl(url);
            HttpHeaders headers = response.getHeaders();
            String dockerHeader = "docker-distribution-api-version";
            String message;
            CheckConnectionResultMessage.STATUS status;
            if (headers.containsKey(dockerHeader)) {
                if (headers.get(dockerHeader).toString().startsWith("[registry/2.")) {
                    status = CheckConnectionResultMessage.STATUS.SUCCESS;
                    message = "Docker " + what + " found.";
                    logger.debug(message);
                } else {
                    status = CheckConnectionResultMessage.STATUS.FAILURE;
                    message = "Unknown value " + headers.get(dockerHeader).toString() + " for header " + dockerHeader;
                    logger.warn(message);
                }
            } else {
                status = CheckConnectionResultMessage.STATUS.FAILURE;
                message = "Missing header: " + dockerHeader + " found only: " + headers.keySet();
                logger.warn(message);
            }
            return new CheckConnectionResultMessage(status, Collections.singletonList(message));
        } catch (IOException ex) {
            String error = "Could not find docker " + what + ". [" + ex.getMessage() + "]";
            logger.warn(error);
            return new CheckConnectionResultMessage(
                    CheckConnectionResultMessage.STATUS.FAILURE,
                    Collections.singletonList(error));
        } catch (Exception ex) {
            logger.warn("Caught unexpected exception");
            logger.warn(ex.toString());
            throw ex;
        }
    }

    List<String> fetchTags(GenericUrl url) {
        try {
            logger.debug(String.format("Fetch tags for %s", url.toString()));
            String tagResponse = getUrl(url).parseAsString();          
            DockerTagsList tagsList = fromJsonString(tagResponse, DockerTagsList.class);
            logger.debug(String.format("Got tags: %s", tagsList.getTags().toString()));
            return tagsList.getTags();
        } catch (IOException ex) {
            logger.warn("Got no tags!");
            return Collections.emptyList();
        }
    }

    public CheckConnectionResultMessage checkConnectionToRepository(
            PackageMaterialProperties repositoryConfiguration
    ) {
        ValidationResultMessage validationResultMessage =
                configurationProvider.validateRepositoryConfiguration(repositoryConfiguration);
        if (validationResultMessage.failure()) {
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.FAILURE, validationResultMessage.getMessages());
        }
        String dockerRegistryUrl = repositoryConfiguration.getProperty(Constants.DOCKER_REGISTRY_URL).value();
        return checkUrl(new GenericUrl(dockerRegistryUrl), "registry");
    }

    public CheckConnectionResultMessage checkConnectionToPackage(
            PackageMaterialProperties packageConfiguration,
            PackageMaterialProperties repositoryConfiguration
    ) {
        String dockerPackageUrl =
                getDockerPackageUrl(packageConfiguration, repositoryConfiguration);
        return checkUrl(new GenericUrl(dockerPackageUrl), "image");
    }

    private String getDockerPackageUrl(
            PackageMaterialProperties packageConfiguration,
            PackageMaterialProperties repositoryConfiguration
    ) {
        return repositoryConfiguration.getProperty(Constants.DOCKER_REGISTRY_URL).value() +
                packageConfiguration.getProperty(Constants.DOCKER_IMAGE).value() +
                "/tags/list";
    }

    static String expandNums(String versionString) {
        Pattern p = Pattern.compile("[0-9]+");
        Matcher m = p.matcher(versionString);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String match = String.format("%06d", Integer.parseInt(m.group()));

            m.appendReplacement(sb, match);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String biggest(String first, String second) {
        String firstComp = expandNums(first);
        String secondComp = expandNums(second);
        if (firstComp.compareTo(secondComp) > 0) {
            return first;
        } else {
            return second;
        }
    }

    public PackageRevisionMessage getLatestRevision(
            PackageMaterialProperties packageConfiguration,
            PackageMaterialProperties repositoryConfiguration
    ) {
        logger.debug("getLatestRevision");
        GenericUrl url = new GenericUrl(getDockerPackageUrl(packageConfiguration, repositoryConfiguration));
        List<String> tags = fetchTags(url);
        String filter = packageConfiguration.getProperty(Constants.DOCKER_TAG_FILTER).value();
        if (filter.equals("")) {
            filter = ".*";
        }

        try {
            Pattern pattern = Pattern.compile(filter);

            List<Object> matching = tags.stream().filter(pattern.asPredicate()).collect(Collectors.toList());

            if (matching.isEmpty()) {
                logger.warn("Found no matching revision.");
                return new PackageRevisionMessage();
            }

            String latest = "";
            for (Object tag: matching) {
                latest = biggest(latest, tag.toString());
            }

            logger.info(String.format("Latest revision is: %s", latest));
            return new PackageRevisionMessage(latest, new Date(), "docker", null,null);

        } catch (PatternSyntaxException e) {
            String message = String.format("Invalid docker tag filter '%s' used for image '%s': %s", filter, url, e.getMessage());
            logger.error(message);
            throw new PatternSyntaxException(message, e.getPattern(), e.getIndex());
        }
    }

    public PackageRevisionMessage getLatestRevisionSince(
            PackageMaterialProperties packageConfiguration,
            PackageMaterialProperties repositoryConfiguration,
            PackageRevisionMessage previous
    ) {
        logger.debug(String.format("getLatestRevisionSince %s", previous.getRevision()));
        PackageRevisionMessage latest = getLatestRevision(packageConfiguration, repositoryConfiguration);
        if (biggest(previous.getRevision(), latest.getRevision()).equals(latest.getRevision())) {
            logger.info(String.format("Latest revision is: %s", latest));
            return latest;
        } else {
            logger.warn("Found no matching revision.");
            return new PackageRevisionMessage();
        }
    }

}