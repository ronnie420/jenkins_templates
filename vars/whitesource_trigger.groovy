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
        project_name = scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.")[0]
        WS_API_KEY = "974b2695040c4d7aa26af565427ae5e21ead9c8e845841798450c499e57c978a"
    }
    stage('List env vars') {
      sh 'printenv | sort'
    }
    stage('Download WS script') {
      sh 'curl -LJO https://github.com/whitesource/unified-agent-distribution/raw/master/standAlone/wss_agent.sh'
    }
    stage('Run WS script') {
      sh 'wss_agent.sh -apiKey ${WS_API_KEY} -project ${project_name} -d .'
    }
  }
}
