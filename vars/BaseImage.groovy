def call(Map pipelineParams) {
pipeline
{
    agent any
    environment {
    slack_channel = "${pipelineParams.slack_channel}"
    tests_dir = "${pipelineParams.ci_tests_dir}"
    BUNDLE_GITHUB__COM = credentials('BUNDLE_GITHUB')
    AWS_REGION="eu-west-1"
    DOCKERHUB_CREDS= credentials('dockerhub')
    }
    stages{
        stage('Checkout'){
            steps{
                git url: "${pipelineParams.scmUrl}",
                    branch: "${pipelineParams.branch}",
                    credentialsId: 'github'
            }
        }
                stage('Setup'){
            steps{
            script{
            sh '''export DOCKER_CONFIG=$WORKSPACE/.docker && docker login -u ${DOCKERHUB_CREDS_USR} -p ${DOCKERHUB_CREDS_PSW}'''
                
            }
            }
        }

        stage('Trigger Building') {
           steps {
              executeScriptSteps("${pipelineParams.language_versions}")
           }
            
        }
        
    }
    
}
}




def executeScriptSteps(version_string) {
    versions=[:]
    version_string.split(",").each { param ->
    def nameAndValue = param.split(":")
            versions[nameAndValue[0]] = nameAndValue[1]
            println("Versions dict")
            println (versions)
    }
    build_versions(versions)
}
    
def build_versions(versions) {
    versions.each { VERSION ->
    println (VERSION.key)
    println (VERSION.value)
    def build_stage_name = VERSION.key + '-Build'
        stage (build_stage_name) {
        timestamps{
            script{
                echo "$VERSION.key is busy building....."
                sh "echo ${VERSION.key} is ${VERSION.value} with ${tests_dir}; ls -l"
                sh "export DOCKER_CONFIG=$WORKSPACE/.docker;export; if [ -f ${tests_dir}/docker_build.sh ]; then ${tests_dir}/docker_build.sh ${VERSION.key} ${VERSION.value}; else echo \"Docker Build Stage Skipped, no Build scripts not found in ${tests_dir}\"; fi"
                }
        }
    }
    def push_stage_name = VERSION.key + '-Push'
        stage (push_stage_name) {
            timestamps{
              sh "unset DOCKER_CONFIG && if [ -f ${tests_dir}/docker_push.sh ]; then ${tests_dir}/docker_push.sh ${VERSION.value}; else echo \"Docker Push Stage Skipped, no Push scripts not found in ${tests_dir}\"; fi"
            }
        }
        
    }
}
