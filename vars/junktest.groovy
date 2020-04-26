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

pipeline
{
    agent any
    environment {
    slack_channel = "${pipelineParams.slack_channel}"
    tests_dir = "${pipelineParams.ci_tests_dir}"
    BUNDLE_GITHUB__COM= credentials('BUNDLE_GITHUB')
    AWS_REGION="eu-west-1"
    }
    stages{
        stage('Checkout'){
            steps{
                git url: pipelineParams.scmUrl,
                branch: pipelineParams.branch,
                credentialsId: 'github'
            }
        }
        stage('Lint') {
            steps{
                setBuildStatus( "Jenkins ${env.STAGE_NAME} executing", "PENDING","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");
                script { 
                    sh 'export'
                    sh '''test -f ${tests_dir}/lint.sh && ${tests_dir}/lint.sh || echo "Linting  Skipped, no Linting scripts found in ${tests_dir}" '''
                    
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
        stage('Test') {
            steps{
                setBuildStatus( "Jenkins ${env.STAGE_NAME} executing", "PENDING","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");
                script {

                sh '''test -f ${tests_dir}/test.sh && ${tests_dir}/test.sh || echo "Test Stage Skipped, no Testing scripts found in ${tests_dir}" '''

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
        stage('Build') {
            steps{
                setBuildStatus( "Jenkins ${env.STAGE_NAME} executing", "PENDING","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");
                script {

                sh '''test -f ${tests_dir}/build.sh && ${tests_dir}/build.sh || echo "Build Stage Skipped, no Build scripts found in ${tests_dir}" '''

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
        stage('Docker Push') {
          steps {
            script {
            gitRef = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%H'").trim()    
            docker.withRegistry('https://576513738724.dkr.ecr.eu-west-1.amazonaws.com') {
            def localdocker = docker.build( pipelineParams.image_tag + gitRef, '--build-arg BUNDLE_GITHUB__COM=${BUNDLE_GITHUB__COM} .')
            localdocker.push()
            }
            }
          }
       }
    }
}
}
