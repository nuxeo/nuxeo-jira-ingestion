/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/).
 * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 * Notice of copyright on this source code does not indicate publication.
 *
 * Contributors:
 *     Julien Carsique <jcarsique@nuxeo.com>
 */

pipeline {
    agent {
        label "jenkins-ai-nuxeo1010"
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5'))
        timeout(time: 1, unit: 'HOURS')
    }
    environment {
        VERSION = ''
        MARKETPLACE_URL = 'https://connect.nuxeo.com/nuxeo/site/marketplace'
        MARKETPLACE_URL_PREPROD = 'https://nos-preprod-connect.nuxeocloud.com/nuxeo/site/marketplace'
        PACKAGE_PATTERN = 'nuxeo-jira-ingestion-package/target/nuxeo-jira-ingestion-package-*.zip'
    }
    stages {
        stage('Init') {
            steps {
//                library "nxAILib@$feat-INSIGHT-988-pipelineLib"
                library identifier: 'nxAILib@feat-INSIGHT-988-pipelineLib', retriever: modernSCM(
                        [$class       : 'GitSCMSource',
                         credentialsId: 'jx-pipeline-git-github-github',
                         remote       : 'https://github.com/nuxeo/nuxeo-ai-tools.git'])
                script {
                    VERSION = jx.getBranchVersion()
                    setGitHubBuildStatus('init')
                    setGitHubBuildStatus('maven/build')
                    if (!env.TAG_NAME) {
                        setGitHubBuildStatus('maven/test')
                    }
                    if (env.TAG_NAME || jx.isLongLivingBranch()) {
                        setGitHubBuildStatus('maven/deploy')
                        setGitHubBuildStatus('package/push')
                    }
                }
                container('nuxeo1010') {
                    sh "kubectl label pods ${NODE_NAME} branch=${BRANCH_NAME}"
                    setGitCredentials()
                    echo "VERSION: $VERSION"
                }
            }
            post {
                always {
                    setGitHubBuildStatus('init')
                }
            }
        }
        stage('Maven Build') {
            steps {
                container('nuxeo1010') {
                    sh "mvn clean install ${jx.getMavenArgs(true)}"
                    stash name: 'packages', includes: PACKAGE_PATTERN.replaceAll(' ', ','), allowEmpty: false
                }
            }
            post {
                always {
                    setGitHubBuildStatus('maven/build')
                }
            }
        }
        stage('Maven Test') {
            when {
                not {
                    tag '*'
                }
            }
            steps {
                container('nuxeo1010') {
                    sh "mvn test --fail-never -nsu ${jx.getMavenArgs()}"
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/*-reports/*.xml'
                    archiveArtifacts artifacts: '**/target/*.log, **/log/*.log, *.log' +
                            ', **/target/*-reports/*, **/target/results/*.html, **/target/*.png, **/target/*.html',
                            allowEmptyArchive: true
                    setGitHubBuildStatus('maven/test')
                }
            }
        }
        stage('Maven Deploy') {
            when {
                anyOf {
                    tag '*'
                    branch 'master*'
                    branch 'sprint-*'
                }
            }
            environment {
                MAVEN_OPTS = "-Xms512m -Xmx1g"
            }
            steps {
                container('nuxeo1010') {
                    sh "mvn deploy ${jx.getMavenArgs(true, true)}"
                    stash name: 'packages', includes: PACKAGE_PATTERN, allowEmpty: false
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: PACKAGE_PATTERN.replaceAll(' ', ', '), allowEmptyArchive: false
                    setGitHubBuildStatus('maven/deploy')
                }
            }
        }
        stage('Push Package') {
            when {
                anyOf {
                    tag '*'
                    branch 'master*'
                    branch 'sprint-*'
                }
            }
            steps {
                container('nuxeo1010') {
                    uploadPackages('connect-nuxeo-ai-jx-bot', 'connect-preprod')
                }
            }
            post {
                always {
                    setGitHubBuildStatus('package/push')
                }
            }
        }
        stage('Upgrade version stream') {
            when {
                tag '*'
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    container('nuxeo1010') {
                        sh """#!/bin/bash -xe
jx step create pr regex --regex 'version: (.*)' --version $VERSION --files packages/nuxeo-jira-ingestion.yml -r https://github.com/nuxeo/jx-ai-versions
"""
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.TAG_NAME || env.BRANCH_NAME ==~ 'master.*' || env.BRANCH_NAME ==~ 'sprint-.*') {
                    step([$class: 'JiraIssueUpdater', issueSelector: [$class: 'DefaultIssueSelector'], scm: scm])
                }
            }
        }
    }
}
