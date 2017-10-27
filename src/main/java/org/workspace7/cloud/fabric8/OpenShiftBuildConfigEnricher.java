package org.workspace7.cloud.fabric8;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.*;
import org.apache.commons.text.StrSubstitutor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class OpenShiftBuildConfigEnricher extends BaseEnricher {

    private static final String SERIAL_RUN_POLICY = "Serial";
    private static final String BUILD_TRIGGER_TYPE_GITHUB = "GitHub";
    private static final String BUILD_TRIGGER_TYPE_GENERIC = "Generic";
    private final String name;
    private MavenProject mavenProject;

    // Available configuration keys
    private enum Config implements Configs.Key {
        enabled {{
            d = "yes";
        }},
        githubWebHookSecret,
        genericSecret;

        public String def() {
            return d;
        }

        protected String d;
    }

    public OpenShiftBuildConfigEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-openshift-bc-enricher");
        mavenProject = buildContext.getProject();
        this.name = MavenUtil.createDefaultResourceName(mavenProject, "pipelines");
    }

    @Override
    public void addMissingResources(final KubernetesListBuilder builder) {

        final List<BuildConfig> buildConfigs = new ArrayList<>();

        final List<BuildTriggerPolicy> triggers = new ArrayList<>();

        final BuildTriggerPolicyBuilder githubTrigger = new BuildTriggerPolicyBuilder()
            .withType(BUILD_TRIGGER_TYPE_GITHUB)
            .withGithub(new WebHookTriggerBuilder()
                .withSecret(getConfig(Config.githubWebHookSecret))
                .build());

        final BuildTriggerPolicyBuilder genericTrigger = new BuildTriggerPolicyBuilder()
            .withType(BUILD_TRIGGER_TYPE_GENERIC)
            .withGeneric(new WebHookTriggerBuilder()
                .withSecret(getConfig(Config.genericSecret))
                .build());

        //need to update it
        triggers.add(githubTrigger.build());
        triggers.add(genericTrigger.build());

        if ("yes".equalsIgnoreCase(getConfig(Config.enabled))) {
            log.info("Adding Build Config");
            addJenkinsStrategyBuildConfig(buildConfigs, triggers);
        }

        if (!buildConfigs.isEmpty()) {
            BuildConfig[] bcs = new BuildConfig[buildConfigs.size()];
            buildConfigs.toArray(bcs);
            builder.addToBuildConfigItems(bcs);
        }

    }

    /**
     * @param buildConfigs
     * @param triggers
     */
    protected void addJenkinsStrategyBuildConfig(List<BuildConfig> buildConfigs, List<BuildTriggerPolicy> triggers) {

        JenkinsPipelineBuildStrategyBuilder jpsBuilder = new JenkinsPipelineBuildStrategyBuilder()
            .withJenkinsfile("Jenkinsfile");

        BuildStrategyBuilder buildStrategyBuilder = new BuildStrategyBuilder()
            .withType("JenkinsPipeline")
            .withJenkinsPipelineStrategy(jpsBuilder.build());

        //Check if the JenkinsFile exists
        if (hasJenkinsfile(mavenProject.getBasedir())) {
            log.info("Adding OpenShift Build Config with Jenkins Pipeline Strategy");
            Map<String, String> buildLabels = new HashMap<>();
            buildLabels.put("build", name);
            buildConfigs.add(new BuildConfigBuilder()
                .withNewMetadata()
                .withLabels(buildLabels)
                .withName(name)
                .endMetadata()
                .withNewSpec()
                .withRunPolicy(SERIAL_RUN_POLICY)
                .withStrategy(buildStrategyBuilder.build())
                .withTriggers(triggers)
                .endSpec()
                .build());
        }
    }

    private boolean hasJenkinsfile(File basedir) {
        boolean jenkinsFileExists = FileUtils.fileExists(basedir.getAbsolutePath() + "/Jenkinsfile");
        return jenkinsFileExists;
    }


}
