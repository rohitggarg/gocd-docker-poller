package se.thinkware.gocd.dockerpoller;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.thinkware.gocd.dockerpoller.message.CheckConnectionResultMessage;
import se.thinkware.gocd.dockerpoller.message.PackageMaterialProperties;
import se.thinkware.gocd.dockerpoller.message.PackageMaterialProperty;
import se.thinkware.gocd.dockerpoller.message.PackageRevisionMessage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class PackageRepositoryPollerIntTests {

	@Test
	void CheckDockerhub() {
		PackageRepositoryPoller poller = new PackageRepositoryPoller(
			new PackageRepositoryConfigurationProvider());
		PackageMaterialProperties repositoryConfiguration = new PackageMaterialProperties();
		PackageMaterialProperty url = new PackageMaterialProperty().withValue("https://index.docker.io/v2/");
		repositoryConfiguration.addPackageMaterialProperty(Constants.DOCKER_REGISTRY_URL, url);
		PackageMaterialProperty name = new PackageMaterialProperty().withValue("dockerhub");
		repositoryConfiguration.addPackageMaterialProperty(Constants.DOCKER_REGISTRY_NAME, name);
		CheckConnectionResultMessage status = poller.checkConnectionToRepository(repositoryConfiguration);
		assertTrue(status.success());
	}

	@Test
	void RepoRunDockerhub() {
		PackageRepositoryPoller poller = new PackageRepositoryPoller(
			new PackageRepositoryConfigurationProvider());
		PackageMaterialProperties repositoryConfiguration = new PackageMaterialProperties();
		PackageMaterialProperty url = new PackageMaterialProperty().withValue("https://index.docker.io/v2/");
		repositoryConfiguration.addPackageMaterialProperty(Constants.DOCKER_REGISTRY_URL, url);
		PackageMaterialProperty name = new PackageMaterialProperty().withValue("dockerhub");
		repositoryConfiguration.addPackageMaterialProperty(Constants.DOCKER_REGISTRY_NAME, name);

		PackageMaterialProperties packageConfiguration = new PackageMaterialProperties();
		PackageMaterialProperty image = new PackageMaterialProperty().withValue("library/debian");
		packageConfiguration.addPackageMaterialProperty(Constants.DOCKER_IMAGE, image);

        CheckConnectionResultMessage status = poller.checkConnectionToPackage(
                packageConfiguration,
                repositoryConfiguration
        );

        assertTrue(status.success());
	}
}
