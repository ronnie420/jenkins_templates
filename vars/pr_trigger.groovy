void setBuildStatus(String message, String state,String git_repo,String context) {
  step([
      $class: "GitHubCommitStatusSetter",
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: git_repo],
      commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: context],
      errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ]);
}


def call(Map pipelineParams) {

pipeline
{
    agent any
    environment {
    slack_channel = "${pipelineParams.slack_channel}"
    tests_dir = "${pipelineParams.ci_tests_dir}"
    BUNDLE_GITHUB__COM = credentials('BUNDLE_GITHUB')
    DOCKERHUB_CREDS= credentials('dockerhub')
    AWS_REGION = "eu-west-1"
    CI = 1
    CC_TEST_REPORTER_ID =  "${pipelineParams.cc_test_reporter_id}}"
    commitSha = "${env.GITHUB_PR_HEAD_SHA}"
    }
    stages{
        stage('checkout'){
            steps{
                git url: "${env.GITHUB_REPO_SSH_URL}",
                branch: "${env.GITHUB_PR_SOURCE_BRANCH}"
            }
        }
        stage('Setup'){
           steps{
             script{
               sh '''export DOCKER_CONFIG=$WORKSPACE/.docker && eval $(aws ecr get-login --no-include-email --region eu-west-1 --registry-ids 576513738724) && docker login -u ${DOCKERHUB_CREDS_USR} -p ${DOCKERHUB_CREDS_PSW}'''

             }
           }
        }
        stage('check-style') {
            steps{
                setBuildStatus( "Jenkins ${env.STAGE_NAME} executing", "PENDING","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}") 

                script { 
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker;if [ -f ${tests_dir}/lint.sh ]; then ${tests_dir}/lint.sh ci; else echo \"Linting Skipped, Linting scripts not found in ${tests_dir}\"; fi"
                    
                }
            }
            post {
                
                always {
                    setBuildStatus( "${currentBuild.currentResult}", "${currentBuild.currentResult}","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");

                }
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following PR ran into linting issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n    Title: ${env.GITHUB_PR_TITLE}\n    PR URL:  ${env.GITHUB_PR_URL}", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
            
        }
        stage('unit-test') {
            steps{
                setBuildStatus( "Jenkins ${env.STAGE_NAME} executing", "PENDING","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");

                script { 
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker;if [ -f ${tests_dir}/test.sh ]; then ${tests_dir}/test.sh ci; else echo \"Testing Stage Skipped, no Testing scripts not found in ${tests_dir}\"; fi"
                    
                }
            }
            post {
                
                always {
                    setBuildStatus( "${currentBuild.currentResult}", "${currentBuild.currentResult}","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");

                }
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following PR ran into testing issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n    Title: ${env.GITHUB_PR_TITLE}\n    PR URL:  ${env.GITHUB_PR_URL}", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
            
        }
        stage('integration-test') {
            steps{
                setBuildStatus( "Jenkins ${env.STAGE_NAME} executing", "PENDING","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");

                script { 
                    sh "export DOCKER_CONFIG=$WORKSPACE/.docker;if [ -f ${tests_dir}/integration_test.sh ]; then ${tests_dir}/integration_test.sh ci; else echo \"Integration Testing Skipped, no Integration Testing scripts not found in ${tests_dir}\"; fi"
                    
                }
            }
            post {
                
                always {
                    setBuildStatus( "${currentBuild.currentResult}", "${currentBuild.currentResult}","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");

                }
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "*The Following PR ran into integration testing issues, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    Stage: ${env.STAGE_NAME}\n    Title: ${env.GITHUB_PR_TITLE}\n    PR URL:  ${env.GITHUB_PR_URL}", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
            
        }
       
    }
    
}
}
