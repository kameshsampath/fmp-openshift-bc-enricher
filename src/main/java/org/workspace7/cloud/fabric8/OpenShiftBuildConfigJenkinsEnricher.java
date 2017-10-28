/**
 * Copyright (c) 2017-present Kamesh Sampath<kamesh.sampath@hotmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.workspace7.cloud.fabric8;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.*;
import org.apache.commons.text.StrSubstitutor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class OpenShiftBuildConfigJenkinsEnricher extends BaseEnricher {

    private static final String SERIAL_RUN_POLICY = "Serial";
    private static final String BUILD_TRIGGER_TYPE_GITHUB = "GitHub";
    private static final String BUILD_TRIGGER_TYPE_GENERIC = "Generic";
    private static final String SOURCE_TYPE_GIT = "Git";
    private final String name;
    private MavenProject mavenProject;

    // Available configuration keys
    private enum Config implements Configs.Key {
        enabled {{
            d = "yes";
        }},
        gitSourceUri,
        gitSourceRef,
        githubWebHookSecret,
        genericSecret;

        public String def() {
            return d;
        }

        protected String d;
    }

    public OpenShiftBuildConfigJenkinsEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-openshift-bc-jenkins-enricher");
        mavenProject = buildContext.getProject();
        this.name = MavenUtil.createDefaultResourceName(mavenProject, "pipelines");
    }

    @Override
    public void addMissingResources(final KubernetesListBuilder builder) {

        if ("yes".equalsIgnoreCase(getConfig(Config.enabled))) {

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
            log.info("Adding Build Config");
            addJenkinsStrategyBuildConfig(buildConfigs, triggers);

            if (!buildConfigs.isEmpty()) {
                BuildConfig[] bcs = new BuildConfig[buildConfigs.size()];
                buildConfigs.toArray(bcs);
                builder.addToBuildConfigItems(bcs);
            }
        }

    }

    /**
     * @param buildConfigs
     * @param triggers
     */
    protected void addJenkinsStrategyBuildConfig(List<BuildConfig> buildConfigs, List<BuildTriggerPolicy> triggers) {

        String uri = getConfig(Config.gitSourceUri);
        String ref = getConfig(Config.gitSourceRef);

        BuildSourceBuilder buildSourceBuilder = new BuildSourceBuilder();

        //Deduce it from Maven SCM details of the project
        if (mavenProject.getScm() != null) {

            if (uri == null) {
                uri = mavenProject.getScm().getDeveloperConnection();
                if (uri == null) {
                    uri = mavenProject.getScm().getConnection();
                }
            }

            ref = mavenProject.getScm().getTag();

            if (ref == null) {
                ref = "master";
            }
        }

        JenkinsPipelineBuildStrategyBuilder jpsBuilder = new JenkinsPipelineBuildStrategyBuilder()
            .withJenkinsfilePath("Jenkinsfile");

        BuildStrategyBuilder buildStrategyBuilder = new BuildStrategyBuilder()
            .withType("JenkinsPipeline")
            .withJenkinsPipelineStrategy(jpsBuilder.build());

        //Check if the JenkinsFile exists
        if (hasJenkinsfile(mavenProject.getBasedir())) {
            log.info("Adding OpenShift Build Config with Jenkins Pipeline Strategy");
            Map<String, String> buildLabels = new HashMap<>();
            buildLabels.put("build", name);

            BuildConfigBuilder buildConfigBuilder = new BuildConfigBuilder()
                .withNewMetadata()
                .withLabels(buildLabels)
                .withName(name)
                .endMetadata()
                .withNewSpec()
                .withRunPolicy(SERIAL_RUN_POLICY)
                .withSource(buildSourceBuilder
                    .withNewGit(null, null, null, ref, uri)
                    .withType(SOURCE_TYPE_GIT)
                    .build())
                .withStrategy(buildStrategyBuilder.build())
                .withTriggers(triggers)
                .endSpec();

            buildConfigs.add(buildConfigBuilder.build());
        }
    }

    private boolean hasJenkinsfile(File basedir) {
        boolean jenkinsFileExists = FileUtils.fileExists(basedir.getAbsolutePath() + "/Jenkinsfile");
        return jenkinsFileExists;
    }


}
