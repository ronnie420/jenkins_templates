void setBuildStatus(String message, String state,String git_repo,String context) {
  step([
      $class: "GitHubCommitStatusSetter",
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: git_repo],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: context],
      errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ]);
}

def call(Map pipelineParams) {
pipeline {
    agent any
    environment {
        slack_channel = "${pipelineParams.slack_channel}"
        tests_dir = "${pipelineParams.ci_tests_dir}"
        BUNDLE_GITHUB__COM = credentials('BUNDLE_GITHUB')
        DOCKERHUB_CREDS= credentials('dockerhub')
        AWS_REGION = "eu-west-1"
    }
    stages {
        stage('Checkout') {
            steps {
                git url: pipelineParams.scmUrl,
                    branch: pipelineParams.branch,
                    credentialsId: 'github'
            }
        }
        stage('Setup'){
           steps{
             script{
               sh '''export DOCKER_CONFIG=$WORKSPACE/.docker && eval $(aws ecr get-login --no-include-email --region eu-west-1 --registry-ids 576513738724) && docker login -u ${DOCKERHUB_CREDS_USR} -p ${DOCKERHUB_CREDS_PSW}'''

             }
           }
        }
        stage('Lint') {
            steps {
                script {
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker ; if [ -f ${tests_dir}/lint.sh ]; then ${tests_dir}/lint.sh ci; else echo \"Linting Stage Skipped, no Build scripts not found in ${tests_dir}\"; fi"
                }
            }
            post {
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following JOB ran into issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }

        }
        stage('Test') {
            steps {
                script {
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker; if [ -f ${tests_dir}/test.sh ]; then ${tests_dir}/test.sh ci;else  echo \"Test Stage   Skipped, no Testing scripts found in ${tests_dir}\"; fi"
                }

            }
            post {
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following JOB ran into issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }

        }
        stage('integration-test') {
            steps{

                script {
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker; if [ -f ${tests_dir}/integration_test.sh ]; then  ${tests_dir}/integration_test.sh ci;else echo \"Integration Tests Skipped, no integration_test.sh found in ${tests_dir}\";fi"
                    
                }
            }
            post {
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following JOB ran into issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
            
        }
        stage('Build') {
            steps {
                script {
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker; if [ -f ${tests_dir}/build.sh ]; then ${tests_dir}/build.sh ci;else  echo \"Build Stage   Skipped, no Build scripts found in ${tests_dir}\"; fi"
                }

            }
            post {
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following JOB ran into issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
        }
        stage('Docker Push') {
            steps {
                script {
                    gitRef = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%H'").trim()
                    docker.withRegistry('https://576513738724.dkr.ecr.eu-west-1.amazonaws.com') {
                        def localdocker = docker.build(pipelineParams.image_tag + gitRef, '--build-arg BUNDLE_GITHUB__COM=${BUNDLE_GITHUB__COM} .')
                        localdocker.push()
                    }
                }
            }
        }
    }
  }
}
